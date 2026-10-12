package dev.haller.dashboard.config;

import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }

    // Bridge uses a self-signed cert — SSL validation must be disabled
    @Bean("hueWebClient")
    public WebClient hueWebClient() throws Exception {
        HttpClient httpClient = HttpClient.create()
            // TCP keepalive detects a dead bridge connection (e.g. bridge rebooted) on the
            // long-lived event stream without any HTTP requests
            .option(ChannelOption.SO_KEEPALIVE, true);
        if (Epoll.isAvailable()) {
            httpClient = httpClient
                .option(EpollChannelOption.TCP_KEEPIDLE, 60)
                .option(EpollChannelOption.TCP_KEEPINTVL, 10)
                .option(EpollChannelOption.TCP_KEEPCNT, 3);
        }
        httpClient = httpClient
            .secure(ssl -> {
                try {
                    ssl.sslContext(SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
