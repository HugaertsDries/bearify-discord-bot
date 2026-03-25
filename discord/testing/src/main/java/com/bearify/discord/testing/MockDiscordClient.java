package com.bearify.discord.testing;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.DiscordClientFactory;
import com.bearify.discord.api.gateway.EmbedMessage;
import com.bearify.discord.api.gateway.Guild;
import com.bearify.discord.api.gateway.SentMessage;
import com.bearify.discord.api.gateway.TextChannel;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.discord.api.voice.VoiceSessionListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class MockDiscordClient implements DiscordClient {

    private final Consumer<CommandInteraction> handler;
    private boolean started = false;
    private String startedWithToken;
    private String startedWithGuildId;
    private final Map<String, List<EmbedMessage>> sentEmbedsByChannel = new ConcurrentHashMap<>();

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
    public TextChannel textChannel(String channelId) {
        return new MockTextChannel(channelId);
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

    public List<EmbedMessage> sentEmbeds(String channelId) {
        return List.copyOf(sentEmbedsByChannel.getOrDefault(channelId, List.of()));
    }

    private static final class MockGuild implements Guild {

        @Override
        public Optional<VoiceSession> voice() {
            return Optional.empty();
        }

        @Override
        public void join(String channelId, AudioProvider provider, VoiceSessionListener onJoined) {
        }
    }

    private final class MockTextChannel implements TextChannel {

        private final String channelId;

        private MockTextChannel(String channelId) {
            this.channelId = channelId;
        }

        @Override
        public void send(String message) {
        }

        @Override
        public SentMessage send(EmbedMessage embed) {
            sentEmbedsByChannel.computeIfAbsent(channelId, ignored -> new ArrayList<>()).add(embed);
            return new SentMessage() {
                @Override public void delete() {}
                @Override
                public void update(EmbedMessage updated) {
                    List<EmbedMessage> embeds = sentEmbedsByChannel.get(channelId);
                    embeds.set(embeds.size() - 1, updated);
                }
            };
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
