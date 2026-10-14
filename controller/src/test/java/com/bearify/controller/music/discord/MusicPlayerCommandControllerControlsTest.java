package com.bearify.controller.music.discord;

import com.bearify.controller.AbstractControllerIntegrationTest;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.controller.music.domain.NoOpMusicPlayer;
import com.bearify.controller.music.domain.SinglePlayerPool;
import com.bearify.discord.api.message.TextBlock;
import com.bearify.controller.music.spotify.SpotifyLinkResolver;
import com.bearify.discord.spring.CommandRegistry;
import com.bearify.discord.testing.MockCommandInteraction;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.StopReason;
import com.bearify.music.player.bridge.model.LoopMode;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.model.Request;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MusicPlayerCommandControllerControlsTest extends AbstractControllerIntegrationTest {

    private static final String VOICE_CHANNEL_ID = "vc-123";
    private static final String GUILD_ID = "guild-456";
    private static final String TEXT_CHANNEL_ID = "txt-789";
    private static final String PLAYER_ID = "player-1";
    private static final String FAILURE_REASON = "Request timed out";
    private static final String REQUESTER_TAG = "<@user-1>";
    private static final String RESTARTED_MESSAGE = "\u23EE\uFE0F | Back to the start of this track.";
    private static final String NOTHING_PLAYING_MESSAGE = "\uD83C\uDF6F | Nothing's playing right now. Queue something with `/player play`!";
    private static final String QUEUE_EMPTY_MESSAGE = "\uD83C\uDF6F | This is where the trail ends. Try adding more using `/player play` command!";
    private static final String NOT_SEEKABLE_MESSAGE = "\uD83D\uDED1\uFE0F | I can't paw back and forth through a live stream.";
    private static final String NOT_PLAYING_HINT = "not even playing songs";
    private static final TrackMetadata AFRICA = new TrackMetadata("Africa", "TOTO", "https://example.com/africa", 295_000);
    private static final String REMOVED_MESSAGE = "🗑️ | Removed **Africa** from the queue.";
    private static final String TRACK_NOT_IN_QUEUE_MESSAGE = "🛑️ | That track isn't in the queue anymore. Run `/player queue` to see what's left.";
    private static final String PICK_A_TRACK_MESSAGE = "🛑️ | Pick a track from the list so I know which one to drop.";
    private static final String SHUFFLE_ON_MESSAGE = "🔀 | You turned shuffle on.";
    private static final String SHUFFLE_OFF_MESSAGE = "🔀 | You turned shuffle off.";
    private static final String LOOP_QUEUE_MESSAGE = "🔁 | You looped the queue.";
    private static final String LOOP_MODE_MESSAGE = "🛑️ | Pick a loop mode: `off`, `queue` or `track`.";
    private static final String VOLUME_SET_MESSAGE = "🔊 | Volume set to 40%. Only too loud for you? Right-click the bear → User Volume.";
    private static final String VOLUME_OUT_OF_RANGE_MESSAGE = "🛑️ | Volume goes from 0 to 100, bear with me.";
    private static final String JUMPED_MESSAGE = "⏩ | Jumped to 1m 23s.";
    private static final String TIME_FORMAT_MESSAGE = "🛑️ | Use a time like `1:23`, `1h30m` or `83` (seconds).";
    private static final String TRACK_TOO_SHORT_MESSAGE = "🛑️ | That track is only 3m 45s long.";
    private static final SpotifyLinkResolver NO_SPOTIFY = new SpotifyLinkResolver(uri -> Optional.empty());

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

    // --- ANSWERS ---

    @Test
    void answersPreviousWithRestartTextWhenBearRestartsTrack() {
        RedisMessageListenerContainer bear = answerAs(interaction ->
                new MusicPlayerEvent.Restarted(PLAYER_ID, new Request(interaction.requestId(), REQUESTER_TAG), GUILD_ID));

        try {
            MockCommandInteraction interaction = buildInteraction("previous");
            commandRegistry.handle(interaction);

            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getEdits()).containsExactly(RESTARTED_MESSAGE));
        } finally {
            bear.stop();
        }
    }

    @Test
    void answersPauseWithNothingPlayingTextWhenNothingPlays() {
        RedisMessageListenerContainer bear = answerAs(interaction ->
                new MusicPlayerEvent.NothingPlaying(PLAYER_ID, interaction.requestId(), GUILD_ID));

        try {
            MockCommandInteraction interaction = buildInteraction("pause");
            commandRegistry.handle(interaction);

            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getEdits()).containsExactly(NOTHING_PLAYING_MESSAGE));
        } finally {
            bear.stop();
        }
    }

    @Test
    void explainsThatLiveStreamsCannotBeRewound() {
        RedisMessageListenerContainer bear = answerAs(interaction ->
                new MusicPlayerEvent.NotSeekable(PLAYER_ID, interaction.requestId(), GUILD_ID));

        try {
            MockCommandInteraction interaction = buildInteraction("rewind");
            commandRegistry.handle(interaction);

            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getEdits()).containsExactly(NOT_SEEKABLE_MESSAGE));
        } finally {
            bear.stop();
        }
    }

    @Test
    void answersRewindWithTheSecondsTheBearApplied() {
        RedisMessageListenerContainer bear = answerAs(interaction ->
                new MusicPlayerEvent.Rewound(PLAYER_ID, new Request(interaction.requestId(), REQUESTER_TAG), GUILD_ID, 30_000));

        try {
            MockCommandInteraction interaction = buildInteraction("rewind");
            commandRegistry.handle(interaction);

            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                            .contains("Rewound the current track by 30s for you."));
        } finally {
            bear.stop();
        }
    }

    @Test
    void neverWritesSkipTextWhenBearHasNothingToSkipTo() {
        RedisMessageListenerContainer bear = answerAs(interaction ->
                new MusicPlayerEvent.NothingToAdvance(PLAYER_ID, interaction.requestId(), GUILD_ID));

        try {
            MockCommandInteraction interaction = buildInteraction("next");
            commandRegistry.handle(interaction);

            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getEdits()).containsExactly(QUEUE_EMPTY_MESSAGE));
        } finally {
            bear.stop();
        }
    }

    @Test
    void answersClearAfterBearConfirms() {
        RedisMessageListenerContainer bear = answerAs(interaction ->
                new MusicPlayerEvent.Cleared(PLAYER_ID, new Request(interaction.requestId(), REQUESTER_TAG), GUILD_ID, java.util.List.of()));

        try {
            MockCommandInteraction interaction = buildInteraction("clear");
            commandRegistry.handle(interaction);

            assertThat(interaction.isDeferredEphemeral()).isTrue();
            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                            .contains("Cleared the playlist. Fresh paws."));
        } finally {
            bear.stop();
        }
    }

    @Test
    void showsQueueAsEphemeralCard() {
        TrackMetadata current = new TrackMetadata("Never Gonna Give You Up", "Rick Astley", "https://example.com/rick", 213_000);
        TrackMetadata next = new TrackMetadata("Africa", "TOTO", "https://example.com/africa", 295_000);
        RedisMessageListenerContainer bear = answerAs(interaction ->
                new MusicPlayerEvent.QueueSnapshot(PLAYER_ID, interaction.requestId(), GUILD_ID, Optional.of(current), List.of(next)));

        try {
            MockCommandInteraction interaction = buildInteraction("queue");
            commandRegistry.handle(interaction);

            assertThat(interaction.isDeferredEphemeral()).isTrue();
            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getLastComponentEdit().orElseThrow()
                            .containers().getFirst().children())
                            .contains(
                                    new TextBlock("### Up Next"),
                                    new TextBlock("-# Now playing: Never Gonna Give You Up — Rick Astley"),
                                    new TextBlock("Africa\n-# TOTO • 4m 55s\n")));
            assertThat(interaction.getDeferredMessage().orElseThrow().isKept()).isTrue();
        } finally {
            bear.stop();
        }
    }

    @Test
    void repliesNoPlayerWhenBearHasNoPlayerForQueue() {
        RedisMessageListenerContainer bear = answerAs(interaction ->
                new MusicPlayerEvent.PlayerNotFound(PLAYER_ID, interaction.requestId(), GUILD_ID));

        try {
            MockCommandInteraction interaction = buildInteraction("queue");
            commandRegistry.handle(interaction);

            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                            .contains(NOT_PLAYING_HINT));
        } finally {
            bear.stop();
        }
    }

    @Test
    void repliesTimedOutWhenBearDoesNotAnswerQueue() {
        MusicPlayerCommandController controller = new MusicPlayerCommandController(new SinglePlayerPool(new TimingOutMusicPlayer()), NO_SPOTIFY);
        MockCommandInteraction interaction = buildInteraction("queue");

        controller.queue(interaction);

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow()).contains("didn't answer in time");
        assertThat(interaction.getDeferredMessage().orElseThrow().isKept()).isFalse();
    }

    @Test
    void removesPickedTrack() throws Exception {
        BlockingQueue<MusicPlayerInteraction> sent = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer bear = answerAs(interaction -> {
            sent.add(interaction);
            return new MusicPlayerEvent.Removed(PLAYER_ID, new Request(interaction.requestId(), REQUESTER_TAG), GUILD_ID,
                    AFRICA, List.of(), 0, 0L);
        });

        try {
            MockCommandInteraction interaction = buildInteractionWithOption("remove", "track", "3|" + AFRICA.uri());
            commandRegistry.handle(interaction);

            assertThat(interaction.isDeferredEphemeral()).isTrue();
            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getEdits()).containsExactly(REMOVED_MESSAGE));
            assertThat(sent.poll(2, TimeUnit.SECONDS)).isInstanceOfSatisfying(MusicPlayerInteraction.Remove.class, remove -> {
                assertThat(remove.position()).isEqualTo(3);
                assertThat(remove.uriPrefix()).isEqualTo(AFRICA.uri());
            });
        } finally {
            bear.stop();
        }
    }

    @Test
    void removesTypedPosition() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            commandRegistry.handle(buildInteractionWithOption("remove", "track", "2"));

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();
            assertThat(objectMapper.readValue(json, MusicPlayerInteraction.class))
                    .isInstanceOfSatisfying(MusicPlayerInteraction.Remove.class, remove -> {
                        assertThat(remove.position()).isEqualTo(2);
                        assertThat(remove.uriPrefix()).isEmpty();
                    });
        } finally {
            container.stop();
        }
    }

    @Test
    void answersRemoveWhenTrackLeftTheQueue() {
        RedisMessageListenerContainer bear = answerAs(interaction ->
                new MusicPlayerEvent.TrackNotInQueue(PLAYER_ID, interaction.requestId(), GUILD_ID));

        try {
            MockCommandInteraction interaction = buildInteractionWithOption("remove", "track", "3|" + AFRICA.uri());
            commandRegistry.handle(interaction);

            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getEdits()).containsExactly(TRACK_NOT_IN_QUEUE_MESSAGE));
        } finally {
            bear.stop();
        }
    }

    @Test
    void setsVolumeAndRepliesWithUserVolumeTip() {
        BlockingQueue<MusicPlayerInteraction> sent = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer bear = answerAs(interaction -> {
            sent.offer(interaction);
            MusicPlayerInteraction.SetVolume setVolume = (MusicPlayerInteraction.SetVolume) interaction;
            return new MusicPlayerEvent.VolumeChanged(PLAYER_ID, setVolume.request(), GUILD_ID, setVolume.volume());
        });

        try {
            MockCommandInteraction interaction = buildInteractionWithOption("volume", "level", "40");
            commandRegistry.handle(interaction);

            assertThat(interaction.isDeferredEphemeral()).isTrue();
            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getEdits()).containsExactly(VOLUME_SET_MESSAGE));
            assertThat(sent).singleElement()
                    .isInstanceOfSatisfying(MusicPlayerInteraction.SetVolume.class, setVolume -> {
                        assertThat(setVolume.volume()).isEqualTo(40);
                        assertThat(setVolume.guildId()).isEqualTo(GUILD_ID);
                    });
        } finally {
            bear.stop();
        }
    }

    @Test
    void turnsShuffleOn() {
        RedisMessageListenerContainer bear = answerAs(interaction ->
                new MusicPlayerEvent.ShuffleChanged(PLAYER_ID, new Request(interaction.requestId(), REQUESTER_TAG), GUILD_ID, true, List.of(AFRICA), 12));

        try {
            MockCommandInteraction interaction = buildInteraction("shuffle");
            commandRegistry.handle(interaction);

            assertThat(interaction.isDeferredEphemeral()).isTrue();
            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getEdits()).containsExactly(SHUFFLE_ON_MESSAGE));
        } finally {
            bear.stop();
        }
    }

    @Test
    void repliesTimedOutWhenBearDoesNotAnswerVolume() {
        MusicPlayerCommandController controller = new MusicPlayerCommandController(new SinglePlayerPool(new TimingOutMusicPlayer()), NO_SPOTIFY);
        MockCommandInteraction interaction = buildInteractionWithOption("volume", "level", "40");

        controller.volume(interaction, 40);

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow()).contains("didn't answer in time");
    }

    @Test
    void jumpsAndConfirmsPosition() throws Exception {
        BlockingQueue<MusicPlayerInteraction> sent = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer bear = answerAs(interaction -> {
            sent.add(interaction);
            MusicPlayerInteraction.Jump jump = (MusicPlayerInteraction.Jump) interaction;
            return new MusicPlayerEvent.Jumped(PLAYER_ID, jump.request(), GUILD_ID, jump.positionMs());
        });

        try {
            MockCommandInteraction interaction = buildInteractionWithOption("jump", "time", "1:23");
            commandRegistry.handle(interaction);

            assertThat(interaction.isDeferredEphemeral()).isTrue();
            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getEdits()).containsExactly(JUMPED_MESSAGE));
            assertThat(sent.poll(2, TimeUnit.SECONDS)).isInstanceOfSatisfying(MusicPlayerInteraction.Jump.class, jump -> {
                assertThat(jump.positionMs()).isEqualTo(83_000);
                assertThat(jump.guildId()).isEqualTo(GUILD_ID);
            });
        } finally {
            bear.stop();
        }
    }

    @Test
    void explainsTrackIsShorter() {
        RedisMessageListenerContainer bear = answerAs(interaction ->
                new MusicPlayerEvent.JumpOutOfRange(PLAYER_ID, interaction.requestId(), GUILD_ID, 225_000));

        try {
            MockCommandInteraction interaction = buildInteractionWithOption("jump", "time", "9:00");
            commandRegistry.handle(interaction);

            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getEdits()).containsExactly(TRACK_TOO_SHORT_MESSAGE));
        } finally {
            bear.stop();
        }
    }

    @Test
    void explainsThatLiveStreamsCannotBeJumpedIn() {
        RedisMessageListenerContainer bear = answerAs(interaction ->
                new MusicPlayerEvent.NotSeekable(PLAYER_ID, interaction.requestId(), GUILD_ID));

        try {
            MockCommandInteraction interaction = buildInteractionWithOption("jump", "time", "1:23");
            commandRegistry.handle(interaction);

            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getEdits()).containsExactly(NOT_SEEKABLE_MESSAGE));
        } finally {
            bear.stop();
        }
    }

    @Test
    void answersLeavePubliclyAfterBearConfirms() {
        RedisMessageListenerContainer bear = answerAs(interaction ->
                new MusicPlayerEvent.Stopped(PLAYER_ID, interaction.requestId(), GUILD_ID, StopReason.REQUESTED));

        try {
            MockCommandInteraction interaction = buildInteraction("leave");
            commandRegistry.handle(interaction);

            assertThat(interaction.isDeferredEphemeral()).isFalse();
            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                            .contains("cleaning up after myself"));
        } finally {
            bear.stop();
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
        assertThat(interaction.getReplies().getFirst().getContent()).contains(NOT_PLAYING_HINT);
    }

    @Test
    void rejectsNextWhenNoPlayerInChannel() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        MockCommandInteraction interaction = buildInteraction("next");

        commandRegistry.handle(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains(NOT_PLAYING_HINT);
    }

    @Test
    void rejectsPreviousWhenNoPlayerInChannel() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        MockCommandInteraction interaction = buildInteraction("previous");

        commandRegistry.handle(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains(NOT_PLAYING_HINT);
    }

    @Test
    void rejectsRewindWhenNoPlayerInChannel() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        MockCommandInteraction interaction = buildInteraction("rewind");

        commandRegistry.handle(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains(NOT_PLAYING_HINT);
    }

    @Test
    void rejectsForwardWhenNoPlayerInChannel() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        MockCommandInteraction interaction = buildInteraction("forward");

        commandRegistry.handle(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains(NOT_PLAYING_HINT);
    }

    @Test
    void rejectsFreeTextRemoveWithoutContactingBear() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            MockCommandInteraction interaction = buildInteractionWithOption("remove", "track", "africa");
            commandRegistry.handle(interaction);

            assertThat(interaction.getReplies()).hasSize(1);
            assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
            assertThat(interaction.getReplies().getFirst().getContent()).isEqualTo(PICK_A_TRACK_MESSAGE);
            assertThat(received.poll(500, TimeUnit.MILLISECONDS)).isNull();
        } finally {
            container.stop();
        }
    }

    @Test
    void setsLoopModeAndConfirmsIt() {
        BlockingQueue<MusicPlayerInteraction> sent = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer bear = answerAs(interaction -> {
            sent.offer(interaction);
            MusicPlayerInteraction.SetLoop setLoop = (MusicPlayerInteraction.SetLoop) interaction;
            return new MusicPlayerEvent.LoopModeChanged(PLAYER_ID, setLoop.request(), GUILD_ID, setLoop.mode());
        });

        try {
            MockCommandInteraction interaction = buildInteractionWithOption("loop", "mode", "Queue");
            commandRegistry.handle(interaction);

            assertThat(interaction.isDeferredEphemeral()).isTrue();
            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getEdits()).containsExactly(LOOP_QUEUE_MESSAGE));
            assertThat(sent).singleElement()
                    .isInstanceOfSatisfying(MusicPlayerInteraction.SetLoop.class, setLoop -> assertThat(setLoop.mode()).isEqualTo(LoopMode.QUEUE));
        } finally {
            bear.stop();
        }
    }

    @Test
    void rejectsUnknownLoopModeWithoutContactingBear() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            MockCommandInteraction interaction = buildInteractionWithOption("loop", "mode", "forever");
            commandRegistry.handle(interaction);

            assertThat(interaction.getReplies()).singleElement().satisfies(reply -> {
                assertThat(reply.isEphemeral()).isTrue();
                assertThat(reply.getContent()).isEqualTo(LOOP_MODE_MESSAGE);
            });
            assertThat(received.poll(500, TimeUnit.MILLISECONDS)).isNull();
        } finally {
            container.stop();
        }
    }

    @Test
    void rejectsVolumeOutOfRangeWithoutContactingBear() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            MockCommandInteraction tooLoud = buildInteractionWithOption("volume", "level", "101");
            MockCommandInteraction tooQuiet = buildInteractionWithOption("volume", "level", "-1");
            commandRegistry.handle(tooLoud);
            commandRegistry.handle(tooQuiet);

            for (MockCommandInteraction interaction : List.of(tooLoud, tooQuiet)) {
                assertThat(interaction.getReplies()).singleElement().satisfies(reply -> {
                    assertThat(reply.isEphemeral()).isTrue();
                    assertThat(reply.getContent()).isEqualTo(VOLUME_OUT_OF_RANGE_MESSAGE);
                });
                assertThat(interaction.getDeferredMessage()).isEmpty();
            }
            assertThat(received.poll(500, TimeUnit.MILLISECONDS)).isNull();
        } finally {
            container.stop();
        }
    }

    @Test
    void explainsTimeFormatWithoutContactingTheBear() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            MockCommandInteraction interaction = buildInteractionWithOption("jump", "time", "1:5");
            commandRegistry.handle(interaction);

            assertThat(interaction.getReplies()).singleElement().satisfies(reply -> {
                assertThat(reply.isEphemeral()).isTrue();
                assertThat(reply.getContent()).isEqualTo(TIME_FORMAT_MESSAGE);
            });
            assertThat(interaction.getDeferredMessage()).isEmpty();
            assertThat(received.poll(500, TimeUnit.MILLISECONDS)).isNull();
        } finally {
            container.stop();
        }
    }

    @Test
    void turnsShuffleOff() {
        RedisMessageListenerContainer bear = answerAs(interaction ->
                new MusicPlayerEvent.ShuffleChanged(PLAYER_ID, new Request(interaction.requestId(), REQUESTER_TAG), GUILD_ID, false, List.of(), 0));

        try {
            MockCommandInteraction interaction = buildInteraction("shuffle");
            commandRegistry.handle(interaction);

            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getEdits()).containsExactly(SHUFFLE_OFF_MESSAGE));
        } finally {
            bear.stop();
        }
    }

    @Test
    void editsPauseReplyWhenPlayerFails() {
        MusicPlayerCommandController controller = new MusicPlayerCommandController(new SinglePlayerPool(new FailingMusicPlayer()), NO_SPOTIFY);
        MockCommandInteraction interaction = buildInteraction("pause");

        controller.pause(interaction);

        assertThat(interaction.getDeferredMessage()).isPresent();
        assertThat(interaction.getDeferredMessage().get().getLastEdit()).hasValueSatisfying(edit -> assertThat(edit).contains(NOT_PLAYING_HINT));
    }

    @Test
    void editsPauseReplyWithTimeoutMessageWhenPlayerDoesNotAnswer() {
        MusicPlayerCommandController controller = new MusicPlayerCommandController(new SinglePlayerPool(new TimingOutMusicPlayer()), NO_SPOTIFY);
        MockCommandInteraction interaction = buildInteraction("pause");

        controller.pause(interaction);

        assertThat(interaction.getDeferredMessage()).isPresent();
        assertThat(interaction.getDeferredMessage().get().getLastEdit()).hasValueSatisfying(edit -> assertThat(edit).contains("didn't answer in time"));
    }

    // --- HELPERS ---

    /** Plays the bear: answers every request on the player's channel with {@code answer}. */
    private RedisMessageListenerContainer answerAs(Function<MusicPlayerInteraction, MusicPlayerEvent> answer) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener((message, pattern) -> {
            MusicPlayerInteraction interaction = objectMapper.readValue(message.getBody(), MusicPlayerInteraction.class);
            redis.convertAndSend(PlayerRedisProtocol.Channels.EVENTS, objectMapper.writeValueAsString(answer.apply(interaction)));
        }, new ChannelTopic(PlayerRedisProtocol.Channels.interactions(PLAYER_ID)));
        container.afterPropertiesSet();
        container.start();
        return container;
    }

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

    private static final class FailingMusicPlayer extends NoOpMusicPlayer {
        @Override public void togglePause(String requesterTag, MusicPlayerEventListener handler) { handler.onFailed(FAILURE_REASON); }
    }

    private static final class TimingOutMusicPlayer extends NoOpMusicPlayer {
        @Override public void togglePause(String requesterTag, MusicPlayerEventListener handler) { handler.onTimedOut(); }
        @Override public void setVolume(int volume, String requesterTag, MusicPlayerEventListener handler) { handler.onTimedOut(); }
    }
}
