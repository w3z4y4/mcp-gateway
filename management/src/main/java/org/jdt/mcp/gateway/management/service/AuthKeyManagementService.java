package org.jdt.mcp.gateway.management.service;

import org.jdt.mcp.gateway.core.dto.AuthKeyApplyRequest;
import org.jdt.mcp.gateway.core.dto.AuthKeyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AuthKeyManagementService {
    Mono<AuthKeyResponse> applyAuthKey(AuthKeyApplyRequest request);
    Flux<AuthKeyResponse> getUserAuthKeys(String userId);
    Mono<Page<AuthKeyResponse>> getAllAuthKeys(String userId, String serviceId, Boolean isActive, Pageable pageable);
    Mono<Void> revokeAuthKey(Long keyId);
    Mono<AuthKeyResponse> updateKeyStatus(Long keyId, Boolean isActive);
    Mono<AuthKeyResponse> renewAuthKey(Long keyId, long extendHours);
    Mono<Integer> revokeUserServiceKeys(String userId, String serviceId);
}