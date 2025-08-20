package org.jdt.mcp.gateway.management.service;

import org.jdt.mcp.gateway.management.dto.AuthKeyApplyRequest;
import org.jdt.mcp.gateway.management.dto.AuthKeyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AuthKeyManagementService {
    AuthKeyResponse applyAuthKey(AuthKeyApplyRequest request);
    List<AuthKeyResponse> getUserAuthKeys(String userId);
    Page<AuthKeyResponse> getAllAuthKeys(String userId, String serviceId, Boolean isActive, Pageable pageable);
    void revokeAuthKey(Long keyId);
    AuthKeyResponse updateKeyStatus(Long keyId, Boolean isActive);
    AuthKeyResponse renewAuthKey(Long keyId, long extendHours);
    int revokeUserServiceKeys(String userId, String serviceId);
}
