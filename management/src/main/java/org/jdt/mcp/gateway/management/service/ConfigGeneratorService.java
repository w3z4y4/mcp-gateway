package org.jdt.mcp.gateway.management.service;

import org.jdt.mcp.gateway.core.dto.ConfigGenerateRequest;
import org.jdt.mcp.gateway.core.dto.ConfigValidationResult;
import org.jdt.mcp.gateway.core.dto.ServiceConfigInfo;

import java.util.List;

public interface ConfigGeneratorService {
    String generateYamlConfig(ConfigGenerateRequest request);
    String generateJsonConfig(ConfigGenerateRequest request);
    List<ServiceConfigInfo> getAvailableServicesForUser(String userId);
    ConfigValidationResult validateConfigRequest(ConfigGenerateRequest request);
}
