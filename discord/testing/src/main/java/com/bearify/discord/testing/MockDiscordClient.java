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
import com.bearify.discord.api.interaction.MessageContextInteraction;
import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.discord.api.voice.VoiceSessionListener;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class MockDiscordClient implements DiscordClient {

    private final List<CommandDefinition> commands;
    private final Consumer<Interaction> handler;
    private boolean started = false;
    private String startedWithToken;
    private String startedWithGuildId;
    private final Map<String, List<ComponentMessage>> sentComponentsByChannel = new ConcurrentHashMap<>();
    private final Map<String, List<String>> sentMessagesByChannel = new ConcurrentHashMap<>();
    private final Map<String, List<String>> deletedMessageIdsByChannel = new ConcurrentHashMap<>();
    private final AtomicLong nextMessageId = new AtomicLong(1);
    private final Set<String> membersInVoice = ConcurrentHashMap.newKeySet();
    private volatile String selfUserId = "mock-bot-user";

    private MockDiscordClient(List<CommandDefinition> commands,
                              Consumer<Interaction> handler) {
        this.commands = List.copyOf(commands);
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
        return new MockGuild(guildId);
    }

    @Override
    public String selfUserId() {
        return selfUserId;
    }

    public void setSelfUserId(String userId) {
        this.selfUserId = userId;
    }

    /** Puts the member in, or takes them out of, a voice channel of this guild as {@link Guild#isInVoice} sees it. */
    public void setInVoice(String guildId, String userId, boolean inVoice) {
        if (inVoice) {
            membersInVoice.add(guildId + ":" + userId);
        } else {
            membersInVoice.remove(guildId + ":" + userId);
        }
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

    public void dispatchMessageContext(MessageContextInteraction interaction) {
        handler.accept(interaction);
    }

    /** The commands this client was created to register with Discord. */
    public List<CommandDefinition> getCommands() {
        return commands;
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

    public List<String> sentMessages(String channelId) {
        return List.copyOf(sentMessagesByChannel.getOrDefault(channelId, List.of()));
    }

    /** IDs of the messages deleted in this channel, through a {@link SentMessage} handle or by ID. */
    public List<String> deletedMessageIds(String channelId) {
        return List.copyOf(deletedMessageIdsByChannel.getOrDefault(channelId, List.of()));
    }

    private final class MockGuild implements Guild {

        private final String guildId;

        private MockGuild(String guildId) {
            this.guildId = guildId;
        }

        @Override
        public Optional<VoiceSession> voice() {
            return Optional.empty();
        }

        @Override
        public boolean isInVoice(String userId) {
            return membersInVoice.contains(guildId + ":" + userId);
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
            sentMessagesByChannel.computeIfAbsent(channelId, ignored -> new ArrayList<>()).add(message);
        }

        @Override
        public SentMessage send(ComponentMessage message) {
            sentComponentsByChannel.computeIfAbsent(channelId, ignored -> new ArrayList<>()).add(message);
            String messageId = "message-" + nextMessageId.getAndIncrement();
            return new SentMessage() {
                @Override
                public String id() {
                    return messageId;
                }

                @Override
                public void delete() {
                    MockTextChannel.this.delete(messageId);
                }

                @Override
                public void update(ComponentMessage updated) {
                    List<ComponentMessage> messages = sentComponentsByChannel.get(channelId);
                    messages.set(messages.size() - 1, updated);
                }
            };
        }

        @Override
        public void delete(String messageId) {
            deletedMessageIdsByChannel.computeIfAbsent(channelId, ignored -> new ArrayList<>()).add(messageId);
        }
    }

    public static class Factory implements DiscordClientFactory {

        private MockDiscordClient lastCreated;
        private Optional<Activity> lastCreatedActivity = Optional.empty();
        private Optional<Duration> lastCreatedEphemeralDeleteAfter = Optional.empty();

        @Override
        public MockDiscordClient create(List<CommandDefinition> commands,
                                        Consumer<Interaction> handler,
                                        Optional<Activity> activity,
                                        Optional<Duration> ephemeralDeleteAfter) {
            lastCreatedActivity = activity;
            lastCreatedEphemeralDeleteAfter = ephemeralDeleteAfter;
            lastCreated = new MockDiscordClient(commands, handler);
            return lastCreated;
        }

        public Optional<MockDiscordClient> getLastCreated() {
            return Optional.ofNullable(lastCreated);
        }

        public Optional<Activity> getLastCreatedActivity() {
            return lastCreatedActivity;
        }

        public Optional<Duration> getLastCreatedEphemeralDeleteAfter() {
            return lastCreatedEphemeralDeleteAfter;
        }

    }
}
