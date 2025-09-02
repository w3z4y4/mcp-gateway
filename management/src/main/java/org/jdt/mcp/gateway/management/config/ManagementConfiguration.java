package org.jdt.mcp.gateway.management.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class ManagementConfiguration {

    @Bean
    public WebClient webClient() {
        // 配置HttpClient
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000) // 连接超时10秒
                .responseTimeout(Duration.ofSeconds(30)) //响应超时30秒
                .doOnConnected(coon ->
                        coon.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)))
                .keepAlive(true); // 启用keep-alive

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(1024 * 1024)) // 1MB
                .build();
    }
}