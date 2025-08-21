package org.jdt.mcp.gateway.proxy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(ProxyConfiguration proxyConfig) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(proxyConfig.getTimeout())
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) proxyConfig.getConnectTimeout().toMillis());

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer ->
                        configurer.defaultCodecs()
                                .maxInMemorySize((int) proxyConfig.getMaxInMemorySize())
                )
                .build();
    }
}