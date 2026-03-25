package com.bearify.music.player.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class ApplicationConfig {

    @Bean
    ScheduledExecutorService audioScheduler() {
        return Executors.newScheduledThreadPool(2);
    }
}
