package com.bearify.music.player.agent;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.music.player.agent.domain.VoiceConnectionManager;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class Application {

    @Bean
    public SmartLifecycle cleanup(VoiceConnectionManager manager,
                                  DiscordClient discord,
                                  StringRedisTemplate redis,
                                  @Value("${player.id}") String playerId) {
        return new SmartLifecycle() {

            private volatile boolean running = false;

            @Override
            public void start() {
                // starts after Discord has connected; lets the controller see whether this bear is still in voice
                redis.opsForValue().set(PlayerRedisProtocol.Keys.bot(playerId), discord.selfUserId());
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
