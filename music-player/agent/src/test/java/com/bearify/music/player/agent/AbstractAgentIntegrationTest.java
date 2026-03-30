package com.bearify.music.player.agent;

import com.bearify.discord.api.gateway.*;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.Interaction;
import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.discord.api.voice.VoiceSessionListener;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

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
            return new DiscordClientFactory() {
                @Override
                public DiscordClient create(List<CommandDefinition> commands, Consumer<Interaction> handler) {
                    return new NoOpDiscordClient();
                }

                @Override
                public DiscordClient create(List<CommandDefinition> commands, Consumer<Interaction> handler, Activity activity) {
                    return new NoOpDiscordClient();
                }
            };
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
        public Guild guild(String guildId) {
            return new Guild() {
                @Override
                public Optional<com.bearify.discord.api.voice.VoiceSession> voice() {
                    return Optional.empty();
                }

                @Override
                public void join(String channelId, AudioProvider provider, VoiceSessionListener onJoined) {
                }
            };
        }

        @Override
        public TextChannel textChannel(String channelId) {
            return new TextChannel() {
                @Override public void send(String message) {}

                @Override
                public SentMessage send(ComponentMessage message) {
                    return null;
                }
            };
        }

        @Override
        public void shutdown() {
        }
    }
}
