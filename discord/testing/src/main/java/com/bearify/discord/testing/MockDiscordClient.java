package com.bearify.discord.testing;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.DiscordClientFactory;
import com.bearify.discord.api.gateway.Guild;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.model.CommandDefinition;
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
    public Guild guild(String guildId) {
        return new MockGuild();
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

    private static final class MockGuild implements Guild {

        @Override
        public Optional<com.bearify.discord.api.voice.VoiceSession> voice() {
            return Optional.empty();
        }

        @Override
        public void join(String channelId, VoiceSessionListener onJoined) {
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
