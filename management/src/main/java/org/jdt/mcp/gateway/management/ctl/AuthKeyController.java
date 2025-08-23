package org.jdt.mcp.gateway.management.ctl;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.jdt.mcp.gateway.core.dto.AuthKeyApplyRequest;
import org.jdt.mcp.gateway.core.dto.AuthKeyResponse;
import org.jdt.mcp.gateway.management.service.AuthKeyManagementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<AuthKeyResponse> applyAuthKey(@Valid @RequestBody AuthKeyApplyRequest request) {
        log.info("User {} applying for auth key for service {}", request.getUserId(), request.getServiceId());
        AuthKeyResponse response = authKeyManagementService.applyAuthKey(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取用户的所有密钥
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AuthKeyResponse>> getUserAuthKeys(@PathVariable String userId) {
        List<AuthKeyResponse> keys = authKeyManagementService.getUserAuthKeys(userId);
        return ResponseEntity.ok(keys);
    }

    /**
     * 分页查询所有密钥（管理员功能）
     */
    @GetMapping
    public ResponseEntity<Page<AuthKeyResponse>> getAllAuthKeys(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false) Boolean isActive,
            Pageable pageable) {
        Page<AuthKeyResponse> keys = authKeyManagementService.getAllAuthKeys(userId, serviceId, isActive, pageable);
        return ResponseEntity.ok(keys);
    }

    /**
     * 撤销密钥
     */
    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> revokeAuthKey(@PathVariable Long keyId) {
        log.info("Revoking auth key: {}", keyId);
        authKeyManagementService.revokeAuthKey(keyId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 激活/停用密钥
     */
    @PatchMapping("/{keyId}/status")
    public ResponseEntity<AuthKeyResponse> updateKeyStatus(
            @PathVariable Long keyId,
            @RequestParam Boolean isActive) {
        log.info("Updating auth key {} status to {}", keyId, isActive);
        AuthKeyResponse response = authKeyManagementService.updateKeyStatus(keyId, isActive);
        return ResponseEntity.ok(response);
    }

    /**
     * 续期密钥
     */
    @PostMapping("/{keyId}/renew")
    public ResponseEntity<AuthKeyResponse> renewAuthKey(
            @PathVariable Long keyId,
            @RequestParam(required = false, defaultValue = "0") long extendHours) {
        log.info("Renewing auth key: {} for {} hours", keyId, extendHours);
        AuthKeyResponse response = authKeyManagementService.renewAuthKey(keyId, extendHours);
        return ResponseEntity.ok(response);
    }

    /**
     * 批量撤销用户的某个服务的所有密钥
     */
    @DeleteMapping("/user/{userId}/service/{serviceId}")
    public ResponseEntity<Integer> revokeUserServiceKeys(
            @PathVariable String userId,
            @PathVariable String serviceId) {
        log.info("Revoking all keys for user {} and service {}", userId, serviceId);
        int revokedCount = authKeyManagementService.revokeUserServiceKeys(userId, serviceId);
        return ResponseEntity.ok(revokedCount);
    }
}
