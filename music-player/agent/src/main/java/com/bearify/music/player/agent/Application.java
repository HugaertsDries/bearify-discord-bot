package com.bearify.music.player.agent;

import com.bearify.music.player.agent.domain.VoiceConnectionManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class Application {

    @Bean
    public SmartLifecycle cleanup(VoiceConnectionManager manager) {
        return new SmartLifecycle() {

            private volatile boolean running = false;

            @Override
            public void start() {
                running = true;
            }

            @Override
            public void stop() {
                manager.close();
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
