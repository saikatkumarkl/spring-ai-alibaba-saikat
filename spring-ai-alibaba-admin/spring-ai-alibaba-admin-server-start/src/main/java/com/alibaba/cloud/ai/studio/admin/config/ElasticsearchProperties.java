package com.alibaba.cloud.ai.studio.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "spring.elasticsearch")
public class ElasticsearchProperties {

    /**
     * Elasticsearch service address
     */
    private String url = "http://localhost:9200";

    /**
     * Connection timeout (milliseconds)
     */
    private Integer connectTimeout = 5000;

    /**
     * Socket timeout (milliseconds)
     */
    private Integer socketTimeout = 60000;

    /**
     * Connection pool configuration
     */
    private ConnectionPool connectionPool = new ConnectionPool();

    @Data
    public static class ConnectionPool {
        /**
         * Maximum number of connections
         */
        private Integer maxConnections = 100;

        /**
         * Maximum number of idle connections
         */
        private Integer maxIdleConnections = 50;

        /**
         * Connection keepalive time (milliseconds)
         */
        private Long keepAlive = 300000L;
    }
}
