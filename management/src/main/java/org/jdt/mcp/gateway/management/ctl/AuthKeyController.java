package org.jdt.mcp.gateway.management.ctl;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.dto.AuthKeyApplyRequest;
import org.jdt.mcp.gateway.core.dto.AuthKeyResponse;
import org.jdt.mcp.gateway.core.dto.BatchAuthKeyApplyRequest;
import org.jdt.mcp.gateway.core.dto.BatchAuthKeyApplyResponse;
import org.jdt.mcp.gateway.management.service.AuthKeyManagementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/management/auth-keys")
@Slf4j
public class AuthKeyController {

    private final AuthKeyManagementService authKeyManagementService;

    public AuthKeyController(AuthKeyManagementService authKeyManagementService) {
        this.authKeyManagementService = authKeyManagementService;
    }

    /**
     * 用户申请MCP服务访问密钥
     */
    @PostMapping("/apply")
    public Mono<AuthKeyResponse> applyAuthKey(@Valid @RequestBody AuthKeyApplyRequest request) {
        log.info("User {} applying for auth key for service {}", request.getUserId(), request.getServiceId());
        return authKeyManagementService.applyAuthKey(request);
    }

    /**
     * 用户批量申请多个MCP服务访问密钥
     */
    @PostMapping("/batch-apply")
    public Mono<BatchAuthKeyApplyResponse> batchApplyAuthKeys(@Valid @RequestBody BatchAuthKeyApplyRequest request) {
        log.info("User {} batch applying for auth keys for services: {}",
                request.getUserId(), request.getServiceIds());
        return authKeyManagementService.batchApplyAuthKeys(request);
    }

    /**
     * 获取用户的所有密钥
     */
    @GetMapping("/user/{userId}")
    public Flux<AuthKeyResponse> getUserAuthKeys(@PathVariable String userId) {
        return authKeyManagementService.getUserAuthKeys(userId);
    }

    /**
     * 分页查询所有密钥（管理员功能）
     */
    @GetMapping
    public Mono<Page<AuthKeyResponse>> getAllAuthKeys(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return authKeyManagementService.getAllAuthKeys(userId, serviceId, isActive, pageable);
    }

    /**
     * 撤销密钥
     */
    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> revokeAuthKey(@PathVariable Long keyId) {
        log.info("Revoking auth key: {}", keyId);
        return authKeyManagementService.revokeAuthKey(keyId);
    }

    /**
     * 激活/停用密钥
     */
    @PatchMapping("/{keyId}/status")
    public Mono<AuthKeyResponse> updateKeyStatus(
            @PathVariable Long keyId,
            @RequestParam Boolean isActive) {
        log.info("Updating auth key {} status to {}", keyId, isActive);
        return authKeyManagementService.updateKeyStatus(keyId, isActive);
    }

    /**
     * 续期密钥
     */
    @PostMapping("/{keyId}/renew")
    public Mono<AuthKeyResponse> renewAuthKey(
            @PathVariable Long keyId,
            @RequestParam(required = false, defaultValue = "0") long extendHours) {
        log.info("Renewing auth key: {} for {} hours", keyId, extendHours);
        return authKeyManagementService.renewAuthKey(keyId, extendHours);
    }

    /**
     * 批量撤销用户的某个服务的所有密钥
     */
    @DeleteMapping("/user/{userId}/service/{serviceId}")
    public Mono<Integer> revokeUserServiceKeys(
            @PathVariable String userId,
            @PathVariable String serviceId) {
        log.info("Revoking all keys for user {} and service {}", userId, serviceId);
        return authKeyManagementService.revokeUserServiceKeys(userId, serviceId);
    }

}