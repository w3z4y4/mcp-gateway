package org.jdt.mcp.gateway.management.service;

import org.jdt.mcp.gateway.core.dto.ConfigGenerateRequest;
import org.jdt.mcp.gateway.core.dto.ConfigValidationResult;
import org.jdt.mcp.gateway.core.dto.ServiceConfigInfo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ConfigGeneratorService {
    Mono<String> generateYamlConfig(ConfigGenerateRequest request);
    Mono<String> generateJsonConfig(ConfigGenerateRequest request);
    Flux<ServiceConfigInfo> getAvailableServicesForUser(String userId);
    Mono<ConfigValidationResult> validateConfigRequest(ConfigGenerateRequest request);
}