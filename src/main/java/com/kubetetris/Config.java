package com.kubetetris;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CoreV1ApiOverride;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.IOException;


@Component
public class Config {

    @Bean
    public CoreV1Api getApiClient() throws IOException {
        ApiClient client = io.kubernetes.client.util.Config.fromConfig("/Users/yashbhatnagar/Downloads/kubeconfig");
        Configuration.setDefaultApiClient(client);
        CoreV1Api api = new CoreV1ApiOverride();
        return api;
    }
}
