package com.bearify.discord.testing;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.DiscordClientFactory;
import com.bearify.discord.api.gateway.GuildClient;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.discord.api.voice.VoiceSessionListener;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class MockDiscordClient implements DiscordClient {

    private final Consumer<CommandInteraction> handler;
    private boolean started = false;
    private String startedWithToken;
    private String startedWithGuildId;

    private MockDiscordClient(List<CommandDefinition> commands, Consumer<CommandInteraction> handler) {
        this.handler = handler;
    }

    @Override
    public void start(String token) {
        this.startedWithToken = token;
        this.started = true;
    }

    @Override
    public void start(String token, String guildId) {
        this.startedWithToken = token;
        this.startedWithGuildId = guildId;
        this.started = true;
    }

    @Override
    public GuildClient guild(String guildId) {
        return new MockGuildClient(guildId);
    }

    @Override
    public void shutdown() {
        this.started = false;
    }

    public void dispatch(CommandInteraction interaction) {
        handler.accept(interaction);
    }

    public boolean isStarted() {
        return started;
    }

    public Optional<String> getStartedWithToken() {
        return Optional.ofNullable(startedWithToken);
    }

    public Optional<String> getStartedWithGuildId() {
        return Optional.ofNullable(startedWithGuildId);
    }

    private static final class MockGuildClient implements GuildClient {

        private final String guildId;

        private MockGuildClient(String guildId) {
            this.guildId = guildId;
        }

        @Override
        public VoiceSession voice() {
            return new MockVoiceSession(guildId);
        }
    }

    private static final class MockVoiceSession implements VoiceSession {

        private final String guildId;

        private MockVoiceSession(String guildId) {
            this.guildId = guildId;
        }

        @Override
        public void joinChannel(String channelId) {
        }

        @Override
        public void leave() {
        }

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public String guildId() {
            return guildId;
        }

        @Override
        public void onJoined(VoiceSessionListener listener) {
        }
    }

    public static class Factory implements DiscordClientFactory {

        private MockDiscordClient lastCreated;

        @Override
        public MockDiscordClient create(List<CommandDefinition> commands, Consumer<CommandInteraction> handler) {
            lastCreated = new MockDiscordClient(commands, handler);
            return lastCreated;
        }

        public Optional<MockDiscordClient> getLastCreated() {
            return Optional.ofNullable(lastCreated);
        }
    }
}
