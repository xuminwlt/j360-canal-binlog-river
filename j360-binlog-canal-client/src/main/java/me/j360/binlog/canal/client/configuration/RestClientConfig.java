package me.j360.binlog.canal.client.configuration;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;



@Configuration
public class RestClientConfig {

    @Bean(destroyMethod = "close")
    public RestClient restClient() {
        RestClient restClient = RestClient.builder(
                new HttpHost("localhost", 9200, "http"))
                .setMaxRetryTimeoutMillis(10000)
                .build();
        return restClient;
    }

}
