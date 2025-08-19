package org.jdt.mcp.gateway.demo.conf;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
@Slf4j
public class AuthKeyFilter implements WebFilter {

    private static final String AUTH_KEY_PARAM = "key";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 只拦截 SSE 请求
        if (request.getPath().value().startsWith("/sse")) {
            String authKey = request.getQueryParams().getFirst(AUTH_KEY_PARAM);

            if (authKey != null) {
                log.info("拦截器捕获到认证 key: {}", authKey);
                System.out.println("MCP Server 拦截器收到认证 key: " + authKey);
            } else {
                log.info("SSE 请求未提供认证 key");
                System.out.println("MCP Server SSE 请求未提供认证 key");
            }

            // 将认证信息存储到 exchange 属性中
            exchange.getAttributes().put("auth_key", authKey);

            // 使用 Reactor Context 来传递认证信息
            return chain.filter(exchange)
                    .contextWrite(Context.of("auth_key", authKey))
                    .doOnSubscribe(subscription -> {
                        // 在订阅时设置 ThreadLocal
                        AuthKeyContext.setAuthKey(authKey);
                    })
                    .doFinally(signalType -> {
                        // 请求结束后清理 ThreadLocal
                        AuthKeyContext.clear();
                    });
        }

        return chain.filter(exchange);
    }
}