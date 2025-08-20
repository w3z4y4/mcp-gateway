package org.jdt.mcp.gateway.management.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ConfigValidationResult {
    private Boolean isValid;
    private List<String> errors;
    private List<String> warnings;
    private Integer validServiceCount;
    private Integer totalServiceCount;
}
