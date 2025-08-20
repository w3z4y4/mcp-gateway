package org.jdt.mcp.gateway.management.service;

import org.jdt.mcp.gateway.management.dto.ConfigGenerateRequest;
import org.jdt.mcp.gateway.management.dto.ConfigValidationResult;
import org.jdt.mcp.gateway.management.dto.ServiceConfigInfo;

import java.util.List;

public interface ConfigGeneratorService {
    String generateYamlConfig(ConfigGenerateRequest request);
    String generateJsonConfig(ConfigGenerateRequest request);
    List<ServiceConfigInfo> getAvailableServicesForUser(String userId);
    ConfigValidationResult validateConfigRequest(ConfigGenerateRequest request);
}
