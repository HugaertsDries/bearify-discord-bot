package com.bearify.controller;

import com.bearify.discord.api.gateway.DiscordClientFactory;
import com.bearify.discord.testing.MockDiscordClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "discord.token=test-token",
        "spring.autoconfigure.exclude=com.bearify.discord.jda.JdaAutoConfiguration"
})
@Import(AbstractControllerIntegrationTest.DiscordTestConfig.class)
public abstract class AbstractControllerIntegrationTest {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @TestConfiguration
    static class DiscordTestConfig {
        @Bean
        DiscordClientFactory discordClientFactory() {
            return new MockDiscordClient.Factory();
        }
    }
}
