package org.jdt.mcp.gateway.management.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.entity.AuthKeyEntity;
import org.jdt.mcp.gateway.core.entity.MCPServiceEntity;
import org.jdt.mcp.gateway.core.tool.AuthKeyGenerator;
import org.jdt.mcp.gateway.core.dto.AuthKeyApplyRequest;
import org.jdt.mcp.gateway.core.dto.AuthKeyResponse;
import org.jdt.mcp.gateway.mapper.AuthKeyMapper;
import org.jdt.mcp.gateway.mapper.MCPServiceMapper;
import org.jdt.mcp.gateway.management.service.AuthKeyManagementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class AuthKeyManagementServiceImpl implements AuthKeyManagementService {

    private final AuthKeyMapper authKeyMapper;
    private final MCPServiceMapper serviceMapper;

    public AuthKeyManagementServiceImpl(AuthKeyMapper authKeyMapper, MCPServiceMapper serviceMapper) {
        this.authKeyMapper = authKeyMapper;
        this.serviceMapper = serviceMapper;
    }

    @Override
    public Mono<AuthKeyResponse> applyAuthKey(AuthKeyApplyRequest request) {
        return Mono.fromCallable(() -> {
            // 验证服务是否存在
            MCPServiceEntity service = serviceMapper.findByServiceId(request.getServiceId());
            if (service == null) {
                throw new IllegalArgumentException("Service not found: " + request.getServiceId());
            }

            // 检查用户是否已有该服务的有效密钥
            List<AuthKeyEntity> existingKeys = authKeyMapper.findByUserIdAndServiceId(
                    request.getUserId(), request.getServiceId());
            long activeKeysCount = existingKeys.stream()
                    .filter(key -> key.getIsActive() && (key.getExpiresAt() == null || key.getExpiresAt().isAfter(LocalDateTime.now())))
                    .count();

            if (activeKeysCount > 0) {
                throw new IllegalStateException("User already has active key for this service");
            }

            // 生成新的密钥
            AuthKeyEntity authKey;
            if (request.getExpireHours() != null && request.getExpireHours() > 0) {
                authKey = AuthKeyGenerator.buildAuthKeyEntityWithExpiry(
                        request.getUserId(), request.getServiceId(), request.getExpireHours());
            } else {
                authKey = AuthKeyGenerator.buildAuthKeyEntity(request.getUserId(), request.getServiceId());
            }

            authKeyMapper.insert(authKey);
            log.info("Generated auth key for user {} and service {}", request.getUserId(), request.getServiceId());

            return buildAuthKeyResponse(authKey, service.getName());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<AuthKeyResponse> getUserAuthKeys(String userId) {
        return Mono.fromCallable(() -> authKeyMapper.findByUserId(userId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(keys -> Flux.fromIterable(keys)
                        .map(key -> {
                            MCPServiceEntity service = serviceMapper.findByServiceId(key.getMCPServiceId());
                            return buildAuthKeyResponse(key, service != null ? service.getName() : "Unknown");
                        }));
    }

    @Override
    public Mono<Page<AuthKeyResponse>> getAllAuthKeys(String userId, String serviceId, Boolean isActive, Pageable pageable) {
        return Mono.fromCallable(() -> {
            List<AuthKeyEntity> keys = authKeyMapper.findByConditions(userId, serviceId, isActive,
                    (int) pageable.getOffset(), pageable.getPageSize());
            long total = authKeyMapper.countByConditions(userId, serviceId, isActive);

            List<AuthKeyResponse> responses = keys.stream()
                    .map(key -> {
                        MCPServiceEntity service = serviceMapper.findByServiceId(key.getMCPServiceId());
                        return buildAuthKeyResponse(key, service != null ? service.getName() : "Unknown");
                    })
                    .collect(Collectors.toList());
            return (Page<AuthKeyResponse>) new PageImpl<>(responses, pageable, total);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> revokeAuthKey(Long keyId) {
        return Mono.fromRunnable(() -> {
            AuthKeyEntity key = authKeyMapper.findById(keyId);
            if (key == null) {
                throw new IllegalArgumentException("Auth key not found: " + keyId);
            }

            authKeyMapper.deleteById(keyId);
            log.info("Revoked auth key: {}", keyId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<AuthKeyResponse> updateKeyStatus(Long keyId, Boolean isActive) {
        return Mono.fromCallable(() -> {
            AuthKeyEntity key = authKeyMapper.findById(keyId);
            if (key == null) {
                throw new IllegalArgumentException("Auth key not found: " + keyId);
            }

            key.setIsActive(isActive);
            authKeyMapper.update(key);
            log.info("Updated auth key {} status to {}", keyId, isActive);

            MCPServiceEntity service = serviceMapper.findByServiceId(key.getMCPServiceId());
            return buildAuthKeyResponse(key, service != null ? service.getName() : "Unknown");
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<AuthKeyResponse> renewAuthKey(Long keyId, long extendHours) {
        return Mono.fromCallable(() -> {
            AuthKeyEntity key = authKeyMapper.findById(keyId);
            if (key == null) {
                throw new IllegalArgumentException("Auth key not found: " + keyId);
            }

            LocalDateTime newExpireTime;
            if (extendHours <= 0) {
                // 设置为永不过期
                newExpireTime = null;
            } else {
                LocalDateTime currentExpire = key.getExpiresAt();
                if (currentExpire == null || currentExpire.isBefore(LocalDateTime.now())) {
                    // 如果当前已过期或永不过期，从现在开始延长
                    newExpireTime = LocalDateTime.now().plusHours(extendHours);
                } else {
                    // 在当前过期时间基础上延长
                    newExpireTime = currentExpire.plusHours(extendHours);
                }
            }

            key.setExpiresAt(newExpireTime);
            key.setIsActive(true); // 续期时激活密钥
            authKeyMapper.update(key);
            log.info("Renewed auth key: {}, new expire time: {}", keyId, newExpireTime);

            MCPServiceEntity service = serviceMapper.findByServiceId(key.getMCPServiceId());
            return buildAuthKeyResponse(key, service != null ? service.getName() : "Unknown");
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Integer> revokeUserServiceKeys(String userId, String serviceId) {
        return Mono.fromCallable(() -> {
            List<AuthKeyEntity> keys = authKeyMapper.findByUserIdAndServiceId(userId, serviceId);
            int revokedCount = 0;

            for (AuthKeyEntity key : keys) {
                if (key.getIsActive()) {
                    authKeyMapper.deleteById(key.getId());
                    revokedCount++;
                }
            }

            log.info("Revoked {} keys for user {} and service {}", revokedCount, userId, serviceId);
            return revokedCount;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private AuthKeyResponse buildAuthKeyResponse(AuthKeyEntity key, String serviceName) {
        return AuthKeyResponse.builder()
                .id(key.getId())
                .keyHash(key.getKeyHash())
                .userId(key.getUserId())
                .serviceId(key.getMCPServiceId())
                .serviceName(serviceName)
                .expiresAt(key.getExpiresAt())
                .isActive(key.getIsActive())
                .createdAt(key.getCreatedAt())
                .lastUsedAt(key.getLastUsedAt())
                .build();
    }
}