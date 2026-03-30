package com.bearify.discord.testing;

import com.bearify.discord.api.gateway.Activity;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.DiscordClientFactory;
import com.bearify.discord.api.gateway.Guild;
import com.bearify.discord.api.gateway.SentMessage;
import com.bearify.discord.api.gateway.TextChannel;
import com.bearify.discord.api.interaction.ButtonInteraction;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.Interaction;
import com.bearify.discord.api.message.ComponentMessage;
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

    private final Consumer<Interaction> handler;
    private boolean started = false;
    private String startedWithToken;
    private String startedWithGuildId;
    private final Map<String, List<ComponentMessage>> sentComponentsByChannel = new ConcurrentHashMap<>();

    private MockDiscordClient(List<CommandDefinition> commands,
                              Consumer<Interaction> handler) {
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

    public void dispatchButton(ButtonInteraction interaction) {
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

    public List<ComponentMessage> sentComponents(String channelId) {
        return List.copyOf(sentComponentsByChannel.getOrDefault(channelId, List.of()));
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
        public SentMessage send(ComponentMessage message) {
            sentComponentsByChannel.computeIfAbsent(channelId, ignored -> new ArrayList<>()).add(message);
            return new SentMessage() {
                @Override public void delete() {}

                @Override
                public void update(ComponentMessage updated) {
                    List<ComponentMessage> messages = sentComponentsByChannel.get(channelId);
                    messages.set(messages.size() - 1, updated);
                }
            };
        }
    }

    public static class Factory implements DiscordClientFactory {

        private MockDiscordClient lastCreated;
        private Optional<Activity> lastCreatedActivity = Optional.empty();

        @Override
        public MockDiscordClient create(List<CommandDefinition> commands,
                                        Consumer<Interaction> handler) {
            lastCreatedActivity = Optional.empty();
            lastCreated = new MockDiscordClient(commands, handler);
            return lastCreated;
        }

        @Override
        public MockDiscordClient create(List<CommandDefinition> commands,
                                        Consumer<Interaction> handler,
                                        Activity activity) {
            lastCreatedActivity = Optional.of(activity);
            lastCreated = new MockDiscordClient(commands, handler);
            return lastCreated;
        }

        public Optional<MockDiscordClient> getLastCreated() {
            return Optional.ofNullable(lastCreated);
        }

        public Optional<Activity> getLastCreatedActivity() {
            return lastCreatedActivity;
        }

    }
}
