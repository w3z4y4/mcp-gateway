package org.jdt.mcp.gateway.management.ctl;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.dto.ConfigGenerateRequest;
import org.jdt.mcp.gateway.management.service.ConfigGeneratorService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management/config")
@Slf4j
public class ConfigGeneratorController {

    private final ConfigGeneratorService configGeneratorService;

    public ConfigGeneratorController(ConfigGeneratorService configGeneratorService) {
        this.configGeneratorService = configGeneratorService;
    }

    /**
     * 生成Spring AI MCP Client YAML配置
     */
    @PostMapping("/yaml")
    public ResponseEntity<String> generateYamlConfig(@Valid @RequestBody ConfigGenerateRequest request) {
        log.info("Generating YAML config for user: {}, services: {}", request.getUserId(), request.getServiceIds());
        String yamlConfig = configGeneratorService.generateYamlConfig(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=mcp-client-config.yml")
                .contentType(MediaType.parseMediaType("application/x-yaml"))
                .body(yamlConfig);
    }

    /**
     * 生成JSON格式配置
     */
    @PostMapping("/json")
    public ResponseEntity<String> generateJsonConfig(@Valid @RequestBody ConfigGenerateRequest request) {
        log.info("Generating JSON config for user: {}, services: {}", request.getUserId(), request.getServiceIds());
        String jsonConfig = configGeneratorService.generateJsonConfig(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=mcp-client-config.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonConfig);
    }

    /**
     * 预览YAML配置（不下载）
     */
    @PostMapping("/yaml/preview")
    public ResponseEntity<String> previewYamlConfig(@Valid @RequestBody ConfigGenerateRequest request) {
        log.info("Previewing YAML config for user: {}, services: {}", request.getUserId(), request.getServiceIds());
        String yamlConfig = configGeneratorService.generateYamlConfig(request);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-yaml"))
                .body(yamlConfig);
    }

    /**
     * 预览JSON配置（不下载）
     */
    @PostMapping("/json/preview")
    public ResponseEntity<String> previewJsonConfig(@Valid @RequestBody ConfigGenerateRequest request) {
        log.info("Previewing JSON config for user: {}, services: {}", request.getUserId(), request.getServiceIds());
        String jsonConfig = configGeneratorService.generateJsonConfig(request);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonConfig);
    }

    /**
     * 获取用户可用的服务列表（用于配置生成页面）
     */
    @GetMapping("/available-services/{userId}")
    public ResponseEntity<?> getAvailableServices(@PathVariable String userId) {
        var availableServices = configGeneratorService.getAvailableServicesForUser(userId);
        return ResponseEntity.ok(availableServices);
    }

    /**
     * 验证配置生成请求
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateConfigRequest(@Valid @RequestBody ConfigGenerateRequest request) {
        var validation = configGeneratorService.validateConfigRequest(request);
        return ResponseEntity.ok(validation);
    }
}