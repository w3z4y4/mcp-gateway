package org.jdt.mcp.gateway.demo.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Employee {
    @JsonProperty
    String name;
    @JsonProperty
    String phoneNum;
    @JsonProperty
    String department;
}
