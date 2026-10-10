package com.bearify.controller.music.discord;

import com.bearify.controller.AbstractControllerIntegrationTest;
import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.discord.spring.CommandRegistry;
import com.bearify.discord.testing.MockCommandInteraction;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.model.TrackRequest;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerCommandControllerControlsTest extends AbstractControllerIntegrationTest {

    private static final String VOICE_CHANNEL_ID = "vc-123";
    private static final String GUILD_ID = "guild-456";
    private static final String TEXT_CHANNEL_ID = "txt-789";
    private static final String PLAYER_ID = "player-1";
    private static final String FAILURE_REASON = "Request timed out";

    @Autowired CommandRegistry commandRegistry;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void seedPlayer() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
    }

    // --- HAPPY PATH ---

    @Test
    void togglesPauseWhenPlayerInChannel() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            commandRegistry.handle(buildInteraction("pause"));

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();
            MusicPlayerInteraction interaction = objectMapper.readValue(json, MusicPlayerInteraction.class);
            assertThat(interaction).isInstanceOf(MusicPlayerInteraction.TogglePause.class);
            assertThat(((MusicPlayerInteraction.TogglePause) interaction).guildId()).isEqualTo(GUILD_ID);
        } finally {
            container.stop();
        }
    }

    @Test
    void skipsToNextTrackWhenPlayerInChannel() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            commandRegistry.handle(buildInteraction("next"));

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();
            MusicPlayerInteraction interaction = objectMapper.readValue(json, MusicPlayerInteraction.class);
            assertThat(interaction).isInstanceOf(MusicPlayerInteraction.Next.class);
            assertThat(((MusicPlayerInteraction.Next) interaction).guildId()).isEqualTo(GUILD_ID);
        } finally {
            container.stop();
        }
    }

    @Test
    void goesToPreviousTrackWhenPlayerInChannel() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            commandRegistry.handle(buildInteraction("previous"));

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();
            MusicPlayerInteraction interaction = objectMapper.readValue(json, MusicPlayerInteraction.class);
            assertThat(interaction).isInstanceOf(MusicPlayerInteraction.Previous.class);
            assertThat(((MusicPlayerInteraction.Previous) interaction).guildId()).isEqualTo(GUILD_ID);
        } finally {
            container.stop();
        }
    }

    @Test
    void rewindsWithDefaultSeekWhenPlayerInChannel() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            commandRegistry.handle(buildInteraction("rewind"));

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();
            MusicPlayerInteraction interaction = objectMapper.readValue(json, MusicPlayerInteraction.class);
            assertThat(interaction).isInstanceOf(MusicPlayerInteraction.Rewind.class);
            MusicPlayerInteraction.Rewind rewind = (MusicPlayerInteraction.Rewind) interaction;
            assertThat(rewind.seekMs()).isEqualTo(0);
            assertThat(rewind.guildId()).isEqualTo(GUILD_ID);
        } finally {
            container.stop();
        }
    }

    @Test
    void rewindsWithSpecifiedSeekWhenPlayerInChannel() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            commandRegistry.handle(buildInteractionWithOption("rewind", "seconds", "30"));

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();
            MusicPlayerInteraction interaction = objectMapper.readValue(json, MusicPlayerInteraction.class);
            assertThat(interaction).isInstanceOf(MusicPlayerInteraction.Rewind.class);
            assertThat(((MusicPlayerInteraction.Rewind) interaction).seekMs()).isEqualTo(30_000L);
        } finally {
            container.stop();
        }
    }

    @Test
    void forwardsWithDefaultSeekWhenPlayerInChannel() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            commandRegistry.handle(buildInteraction("forward"));

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();
            MusicPlayerInteraction interaction = objectMapper.readValue(json, MusicPlayerInteraction.class);
            assertThat(interaction).isInstanceOf(MusicPlayerInteraction.Forward.class);
            MusicPlayerInteraction.Forward forward = (MusicPlayerInteraction.Forward) interaction;
            assertThat(forward.seekMs()).isEqualTo(0);
            assertThat(forward.guildId()).isEqualTo(GUILD_ID);
        } finally {
            container.stop();
        }
    }

    // --- EDGE CASES ---

    @Test
    void rejectsPauseWhenNoPlayerInChannel() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        MockCommandInteraction interaction = buildInteraction("pause");

        commandRegistry.handle(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("not even playing songs");
    }

    @Test
    void rejectsNextWhenNoPlayerInChannel() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        MockCommandInteraction interaction = buildInteraction("next");

        commandRegistry.handle(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("not even playing songs");
    }

    @Test
    void rejectsPreviousWhenNoPlayerInChannel() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        MockCommandInteraction interaction = buildInteraction("previous");

        commandRegistry.handle(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("not even playing songs");
    }

    @Test
    void rejectsRewindWhenNoPlayerInChannel() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        MockCommandInteraction interaction = buildInteraction("rewind");

        commandRegistry.handle(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("not even playing songs");
    }

    @Test
    void rejectsForwardWhenNoPlayerInChannel() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        MockCommandInteraction interaction = buildInteraction("forward");

        commandRegistry.handle(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("not even playing songs");
    }

    @Test
    void editsPauseReplyWhenPlayerFails() {
        MusicPlayerCommandController controller = new MusicPlayerCommandController(new SinglePlayerPool(new FailingMusicPlayer()));
        MockCommandInteraction interaction = buildInteraction("pause");

        controller.pause(interaction);

        assertThat(interaction.getDeferredMessage()).isPresent();
        assertThat(interaction.getDeferredMessage().get().getLastEdit()).hasValueSatisfying(edit -> assertThat(edit).contains("not even playing songs"));
    }

    // --- HELPERS ---

    private MockCommandInteraction buildInteraction(String subcommand) {
        return MockCommandInteraction.forCommand("player")
                .subcommand(subcommand)
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .textChannelId(TEXT_CHANNEL_ID)
                .build();
    }

    private MockCommandInteraction buildInteractionWithOption(String subcommand, String optionKey, String optionValue) {
        return MockCommandInteraction.forCommand("player")
                .subcommand(subcommand)
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .textChannelId(TEXT_CHANNEL_ID)
                .option(optionKey, optionValue)
                .build();
    }

    private record SinglePlayerPool(MusicPlayer player) implements MusicPlayerPool {
        @Override public MusicPlayer acquire(String guildId, String voiceChannelId) { return player; }
        @Override public Optional<MusicPlayer> find(String guildId, String voiceChannelId) { return Optional.of(player); }
        @Override public boolean hasActiveSessionFor(String guildId) { return true; }
    }

    private static final class FailingMusicPlayer implements MusicPlayer {
        @Override public void join(MusicPlayerEventListener handler) {}
        @Override public void stop() {}
        @Override public void play(TrackRequest request, MusicPlayerEventListener handler) {}
        @Override public void togglePause(String requesterTag, MusicPlayerEventListener handler) { handler.onFailed(FAILURE_REASON); }
        @Override public void previous(String requesterTag, MusicPlayerEventListener handler) {}
        @Override public void next(String requesterTag, MusicPlayerEventListener handler) {}
        @Override public void rewind(Duration seek, String requesterTag) {}
        @Override public void forward(Duration seek, String requesterTag, MusicPlayerEventListener handler) {}
        @Override public void clear(String requesterTag) {}
    }
}
