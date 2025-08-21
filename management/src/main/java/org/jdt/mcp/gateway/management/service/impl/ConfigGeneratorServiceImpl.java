package org.jdt.mcp.gateway.management.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.entity.AuthKeyEntity;
import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import org.jdt.mcp.gateway.core.entity.ServiceStatus;
import org.jdt.mcp.gateway.management.dto.ConfigGenerateRequest;
import org.jdt.mcp.gateway.management.dto.ConfigValidationResult;
import org.jdt.mcp.gateway.management.dto.ServiceConfigInfo;
import org.jdt.mcp.gateway.mapper.AuthKeyMapper;
import org.jdt.mcp.gateway.mapper.MCPServiceMapper;
import org.jdt.mcp.gateway.management.service.ConfigGeneratorService;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

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
    public String generateYamlConfig(ConfigGenerateRequest request) {
        Map<String, Object> config = generateBaseConfig(request);
        return yaml.dump(config);
    }

    @Override
    public String generateJsonConfig(ConfigGenerateRequest request) {
        List<ServiceConfigInfo> serviceConfigs = getServiceConfigs(request);

        Map<String, Object> connections = new HashMap<>();
        for (ServiceConfigInfo serviceConfig : serviceConfigs) {
            Map<String, Object> serviceConfigMap = new HashMap<>();
            serviceConfigMap.put("url", buildServiceUrl(request.getBaseUrl(), serviceConfig));
            serviceConfigMap.put("type", "sse"); // 默认使用sse类型
            serviceConfigMap.put("timeout", request.getTimeout());
            serviceConfigMap.put("disabled", false);

            if (request.getAutoApprove()) {
                serviceConfigMap.put("autoApprove", Collections.emptyList());
            }

            connections.put(serviceConfig.getServiceId(), serviceConfigMap);
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(connections);
        } catch (JsonProcessingException e) {
            log.error("Error generating JSON config", e);
            throw new RuntimeException("Failed to generate JSON config", e);
        }
    }

    @Override
    public List<ServiceConfigInfo> getAvailableServicesForUser(String userId) {
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
    }

    @Override
    public ConfigValidationResult validateConfigRequest(ConfigGenerateRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int validServiceCount = 0;

        List<ServiceConfigInfo> availableServices = getAvailableServicesForUser(request.getUserId());
        Map<String, ServiceConfigInfo> serviceMap = availableServices.stream()
                .collect(Collectors.toMap(ServiceConfigInfo::getServiceId, s -> s));

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
    }

    private Map<String, Object> generateBaseConfig(ConfigGenerateRequest request) {
        List<ServiceConfigInfo> serviceConfigs = getServiceConfigs(request);

        Map<String, Object> connections = new HashMap<>();
        for (ServiceConfigInfo serviceConfig : serviceConfigs) {
            Map<String, Object> serviceConfigMap = new HashMap<>();
            serviceConfigMap.put("url", buildServiceUrl(request.getBaseUrl(), serviceConfig));
            serviceConfigMap.put("type", "async"); // Spring AI格式默认用async

            connections.put(serviceConfig.getServiceId(), serviceConfigMap);
        }

        Map<String, Object> mcpClient = new HashMap<>();
        mcpClient.put("toolcallback", Map.of("enable", request.getToolCallbackEnable()));
        mcpClient.put("sse", Map.of("connections", connections));

        Map<String, Object> springAi = new HashMap<>();
        springAi.put("mcp", Map.of("client", mcpClient));

        return Map.of("spring", Map.of("ai", springAi));
    }

    private List<ServiceConfigInfo> getServiceConfigs(ConfigGenerateRequest request) {
        ConfigValidationResult validation = validateConfigRequest(request);
        if (!validation.getIsValid()) {
            throw new IllegalArgumentException("Invalid config request: " + String.join(", ", validation.getErrors()));
        }

        List<ServiceConfigInfo> availableServices = getAvailableServicesForUser(request.getUserId());
        Map<String, ServiceConfigInfo> serviceMap = availableServices.stream()
                .collect(Collectors.toMap(ServiceConfigInfo::getServiceId, s -> s));

        return request.getServiceIds().stream()
                .map(serviceMap::get)
                .filter(Objects::nonNull)
                .filter(ServiceConfigInfo::getIsActive)
                .filter(service -> service.getAuthKey() != null)
                .collect(Collectors.toList());
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
}