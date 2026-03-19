package com.bearify.music.player.agent;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.DiscordClientFactory;
import com.bearify.discord.api.gateway.GuildClient;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.discord.api.voice.VoiceSessionListener;

import java.util.Optional;
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
        "player.id=test-player"
})
@Import(AbstractAgentIntegrationTest.AgentTestConfig.class)
public abstract class AbstractAgentIntegrationTest {

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
    static class AgentTestConfig {
        @Bean
        DiscordClientFactory discordClientFactory() {
            return (commands, handler) -> new NoOpDiscordClient();
        }
    }

    private static final class NoOpDiscordClient implements DiscordClient {

        @Override
        public void start(String token) {
        }

        @Override
        public void start(String token, String guildId) {
        }

        @Override
        public GuildClient guild(String guildId) {
            return () -> new NoOpVoiceSession(guildId);
        }

        @Override
        public void shutdown() {
        }
    }

    private static final class NoOpVoiceSession implements VoiceSession {

        private final String guildId;

        private NoOpVoiceSession(String guildId) {
            this.guildId = guildId;
        }

        @Override
        public void join(String channelId, VoiceSessionListener onJoined) {
        }

        @Override
        public void leave() {
        }

        @Override
        public Optional<String> getConnectedChannelId() {
            return Optional.empty();
        }

        @Override
        public boolean isAlone() {
            return true;
        }

        @Override
        public String getGuildId() {
            return guildId;
        }
    }
}
