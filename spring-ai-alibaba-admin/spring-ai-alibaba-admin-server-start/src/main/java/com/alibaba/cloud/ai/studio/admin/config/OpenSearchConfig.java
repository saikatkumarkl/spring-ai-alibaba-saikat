package com.alibaba.cloud.ai.studio.admin.config;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URL;

@Configuration
@EnableConfigurationProperties(OpenSearchProperties.class)
public class OpenSearchConfig {

    @Bean
    public RestClient restClient(OpenSearchProperties properties) {
        try {
            URL url = new URL(properties.getUrl());

            return RestClient.builder(
                    new HttpHost(url.getHost(), url.getPort(), url.getProtocol()))
                .setRequestConfigCallback(requestConfigBuilder ->
                    requestConfigBuilder
                        .setConnectTimeout(properties.getConnectTimeout())
                        .setSocketTimeout(properties.getSocketTimeout()))
                .setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder
                        .setMaxConnTotal(properties.getConnectionPool().getMaxConnections())
                        .setMaxConnPerRoute(properties.getConnectionPool().getMaxIdleConnections()))
                .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create OpenSearch RestClient", e);
        }
    }

    @Bean
    public RestClientTransport openSearchTransport(RestClient restClient) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    @Bean
    public OpenSearchClient openSearchClient(RestClientTransport transport) {
        return new OpenSearchClient(transport);
    }
}
