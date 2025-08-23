package org.jdt.mcp.gateway.management.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.entity.AuthKeyEntity;
import org.jdt.mcp.gateway.core.entity.MCPClientConfig;
import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import org.jdt.mcp.gateway.core.entity.ServiceStatus;
import org.jdt.mcp.gateway.core.dto.ConfigGenerateRequest;
import org.jdt.mcp.gateway.core.dto.ConfigValidationResult;
import org.jdt.mcp.gateway.core.dto.ServiceConfigInfo;
import org.jdt.mcp.gateway.mapper.AuthKeyMapper;
import org.jdt.mcp.gateway.mapper.MCPServiceMapper;
import org.jdt.mcp.gateway.management.service.ConfigGeneratorService;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ConfigGeneratorServiceImpl implements ConfigGeneratorService {

    private final MCPServiceMapper serviceMapper;
    private final AuthKeyMapper authKeyMapper;
    private final ObjectMapper objectMapper;
    private final Yaml yaml;

    public ConfigGeneratorServiceImpl(MCPServiceMapper serviceMapper, AuthKeyMapper authKeyMapper) {
        this.serviceMapper = serviceMapper;
        this.authKeyMapper = authKeyMapper;
        this.objectMapper = new ObjectMapper();

        // 配置YAML输出格式
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        this.yaml = new Yaml(options);
    }

    @Override
    public Mono<String> generateYamlConfig(ConfigGenerateRequest request) {
        return buildMCPClientConfig(request)
                .map(this::convertConfigToMap)
                .map(yaml::dump)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<String> generateJsonConfig(ConfigGenerateRequest request) {
        return getServiceConfigs(request)
                .collectList()
                .map(serviceConfigs -> {
                    Map<String, ServiceConnectionJsonConfig> connections = new HashMap<>();
                    for (ServiceConfigInfo serviceConfig : serviceConfigs) {
                        ServiceConnectionJsonConfig jsonConfig = ServiceConnectionJsonConfig.builder()
                                .url(buildServiceUrl(request.getBaseUrl(), serviceConfig))
                                .type("sse")
                                .timeout(request.getTimeout())
                                .disabled(false)
                                .autoApprove(request.getAutoApprove() ? Collections.emptyList() : null)
                                .build();

                        connections.put(serviceConfig.getServiceId(), jsonConfig);
                    }

                    try {
                        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(connections);
                    } catch (JsonProcessingException e) {
                        log.error("Error generating JSON config", e);
                        throw new RuntimeException("Failed to generate JSON config", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<ServiceConfigInfo> getAvailableServicesForUser(String userId) {
        return Mono.fromCallable(() -> {
                    // 获取用户有权限的服务
                    List<AuthKeyEntity> userKeys = authKeyMapper.findByUserId(userId);
                    Set<String> authorizedServiceIds = userKeys.stream()
                            .filter(key -> key.getIsActive() &&
                                    (key.getExpiresAt() == null || key.getExpiresAt().isAfter(LocalDateTime.now())))
                            .map(AuthKeyEntity::getMCPServiceId)
                            .collect(Collectors.toSet());

                    // 获取所有激活的服务
                    List<MCPServiceEntity> activeServices = serviceMapper.findByStatus(ServiceStatus.ACTIVE);

                    return activeServices.stream()
                            .filter(service -> authorizedServiceIds.contains(service.getServiceId()))
                            .map(service -> {
                                // 获取用户对应的密钥
                                String authKey = userKeys.stream()
                                        .filter(key -> key.getMCPServiceId().equals(service.getServiceId()) &&
                                                key.getIsActive() &&
                                                (key.getExpiresAt() == null || key.getExpiresAt().isAfter(LocalDateTime.now())))
                                        .map(AuthKeyEntity::getKeyHash)
                                        .findFirst()
                                        .orElse(null);

                                return ServiceConfigInfo.builder()
                                        .serviceId(service.getServiceId())
                                        .serviceName(service.getName())
                                        .endpoint(service.getEndpoint())
                                        .authKey(authKey)
                                        .maxQps(service.getMaxQps())
                                        .isActive(service.getStatus() == ServiceStatus.ACTIVE)
                                        .description(service.getDescription())
                                        .build();
                            })
                            .collect(Collectors.toList());
                }).subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<ConfigValidationResult> validateConfigRequest(ConfigGenerateRequest request) {
        return getAvailableServicesForUser(request.getUserId())
                .collectMap(ServiceConfigInfo::getServiceId, service -> service)
                .map(serviceMap -> {
                    List<String> errors = new ArrayList<>();
                    List<String> warnings = new ArrayList<>();
                    int validServiceCount = 0;

                    for (String serviceId : request.getServiceIds()) {
                        ServiceConfigInfo service = serviceMap.get(serviceId);
                        if (service == null) {
                            errors.add("Service not found or not authorized: " + serviceId);
                        } else if (!service.getIsActive()) {
                            warnings.add("Service is not active: " + serviceId + " (" + service.getServiceName() + ")");
                        } else if (service.getAuthKey() == null) {
                            errors.add("No valid auth key found for service: " + serviceId);
                        } else {
                            validServiceCount++;
                        }
                    }

                    if (request.getServiceIds().isEmpty()) {
                        errors.add("At least one service must be selected");
                    }

                    if (request.getTimeout() != null && request.getTimeout() <= 0) {
                        errors.add("Timeout must be positive");
                    }

                    return ConfigValidationResult.builder()
                            .isValid(errors.isEmpty())
                            .errors(errors)
                            .warnings(warnings)
                            .validServiceCount(validServiceCount)
                            .totalServiceCount(request.getServiceIds().size())
                            .build();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 构建MCP客户端配置实体
     */
    private Mono<MCPClientConfig> buildMCPClientConfig(ConfigGenerateRequest request) {
        return getServiceConfigs(request)
                .collectList()
                .map(serviceConfigs -> {
                    // 构建服务连接配置
                    Map<String, MCPClientConfig.ServiceConnectionConfig> connections = new HashMap<>();
                    for (ServiceConfigInfo serviceConfig : serviceConfigs) {
                        MCPClientConfig.ServiceConnectionConfig connectionConfig =
                                MCPClientConfig.ServiceConnectionConfig.builder()
                                        .url(buildServiceUrl(request.getBaseUrl(), serviceConfig))
                                        .timeout(request.getTimeout())
                                        .disabled(false)
                                        .autoApprove(request.getAutoApprove() ? Collections.emptyList() : null)
                                        .build();

                        connections.put(serviceConfig.getServiceId(), connectionConfig);
                    }

                    // 构建完整配置树
                    MCPClientConfig.SSEConfig sseConfig = MCPClientConfig.SSEConfig.builder()
                            .connections(connections)
                            .build();

                    MCPClientConfig.ToolCallbackConfig toolCallbackConfig = MCPClientConfig.ToolCallbackConfig.builder()
                            .enable(request.getToolCallbackEnable())
                            .build();

                    MCPClientConfig.ClientConfig clientConfig = MCPClientConfig.ClientConfig.builder()
                            .toolcallback(toolCallbackConfig)
                            .sse(sseConfig)
                            .type("async")
                            .build();

                    MCPClientConfig.MCPConfig mcpConfig = MCPClientConfig.MCPConfig.builder()
                            .client(clientConfig)
                            .build();

                    MCPClientConfig.AIConfig aiConfig = MCPClientConfig.AIConfig.builder()
                            .mcp(mcpConfig)
                            .build();

                    MCPClientConfig.SpringConfig springConfig = MCPClientConfig.SpringConfig.builder()
                            .ai(aiConfig)
                            .build();

                    return MCPClientConfig.builder()
                            .spring(springConfig)
                            .build();
                });
    }

    /**
     * 将配置实体转换为Map（用于YAML序列化）
     */
    private Map<String, Object> convertConfigToMap(MCPClientConfig config) {
        try {
            // 使用ObjectMapper转换为Map
            String json = objectMapper.writeValueAsString(config);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return map;
        } catch (JsonProcessingException e) {
            log.error("Error converting config to map", e);
            throw new RuntimeException("Failed to convert config to map", e);
        }
    }

    private Flux<ServiceConfigInfo> getServiceConfigs(ConfigGenerateRequest request) {
        return validateConfigRequest(request)
                .flatMapMany(validation -> {
                    if (!validation.getIsValid()) {
                        return Flux.error(new IllegalArgumentException("Invalid config request: " +
                                String.join(", ", validation.getErrors())));
                    }

                    return getAvailableServicesForUser(request.getUserId())
                            .collectMap(ServiceConfigInfo::getServiceId, service -> service)
                            .flatMapMany(serviceMap ->
                                    Flux.fromIterable(request.getServiceIds())
                                            .map(serviceMap::get)
                                            .filter(Objects::nonNull)
                                            .filter(ServiceConfigInfo::getIsActive)
                                            .filter(service -> service.getAuthKey() != null)
                            );
                });
    }

    private String buildServiceUrl(String baseUrl, ServiceConfigInfo serviceConfig) {
        String url = baseUrl;
        if (!url.endsWith("/")) {
            url += "/";
        }
        url += "mcp/" + serviceConfig.getServiceId();

        if (serviceConfig.getAuthKey() != null) {
            url += "?key=" + serviceConfig.getAuthKey();
        }

        return url;
    }

    /**
     * JSON格式的服务连接配置（内部类）
     */
    @lombok.Data
    @lombok.Builder
    private static class ServiceConnectionJsonConfig {
        private String url;
        private String type;
        private Integer timeout;
        private Boolean disabled;
        private List<String> autoApprove;
    }
}