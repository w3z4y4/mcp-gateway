package org.jdt.mcp.gateway.core.entity;

public record AuthCallRecord(String path, String ip, String authKey, boolean success) {

}
