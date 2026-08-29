package com.kubetetris.api.config;

import com.kubetetris.collector.KubernetesCollector;
import com.kubetetris.engine.balancer.Balancer;
import com.kubetetris.engine.config.EngineConfig;
import com.kubetetris.engine.scheduler.Scheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    @Bean
    public EngineConfig engineConfig() {
        return EngineConfig.defaults();
    }

    @Bean
    public Scheduler scheduler(EngineConfig config) {
        return new Scheduler(config);
    }

    @Bean
    public Balancer balancer(EngineConfig config) {
        return new Balancer(config);
    }

    @Bean
    public KubernetesCollector kubernetesCollector() {
        return new KubernetesCollector();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
