package com.bearify.controller.music.redis;

import com.bearify.controller.AbstractControllerIntegrationTest;
import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.controller.music.domain.MusicPlayerAnnouncementRegistry;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.music.player.bridge.events.JoinRequest;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.StopReason;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.model.LoopMode;
import com.bearify.music.player.bridge.model.Request;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.bearify.music.player.bridge.model.TrackRequest;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "music-player.pool.connect-request-ttl=500ms",
        "music-player.pool.interaction-timeout=500ms",
        "music-player.pool.queue-timeout=200ms"
})
class RedisMusicPlayerTest extends AbstractControllerIntegrationTest {

    private static final String PLAYER_ID = "player-1";
    private static final String GUILD_ID = "guild-1";
    private static final String VOICE_CHANNEL_ID = "voice-1";
    private static final String TEXT_CHANNEL_ID = "text-1";
    private static final String REQUESTER_TAG = "<@user-1>";
    private static final String QUERY = "ytsearch:daft punk";
    private static final TrackMetadata TRACK = new TrackMetadata("One More Time", "Daft Punk", "https://youtu.be/one-more-time", 320_000);

    @Autowired MusicPlayerPool pool;
    @Autowired MusicPlayerAnnouncementRegistry announcementRegistry;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
    }

    @AfterEach
    void cleanup() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        var livenessKeys = redis.keys(PlayerRedisProtocol.Keys.connectRequest("*"));
        if (livenessKeys != null) redis.delete(livenessKeys);
    }

    // --- PENDING STATE ---

    @Test
    void joinInPendingStatePublishesConnectRequestToBroadcastChannel() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        var container = startListener(PlayerRedisProtocol.Channels.REQUESTS, received);

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
            player.join(new MusicPlayerEventListener() {});

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();

            JoinRequest request = objectMapper.readValue(json, JoinRequest.class);
            assertThat(request.guildId()).isEqualTo(GUILD_ID);
            assertThat(request.voiceChannelId()).isEqualTo(VOICE_CHANNEL_ID);
            assertThat(request.requestId()).isNotBlank();
        } finally {
            container.stop();
        }
    }

    @Test
    void joinInPendingStateSetsLivenessKeyWithTtl() {
        MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
        player.join(new MusicPlayerEventListener() {});

        var keys = redis.keys(PlayerRedisProtocol.Keys.connectRequest("*"));
        assertThat(keys).isNotEmpty();
        String key = keys.iterator().next();
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isGreaterThan(0);
    }

    @Test
    void joinInPendingStateCallsOnReadyWhenPlayerPublishesReadyEvent() throws Exception {
        BlockingQueue<String> requestQueue = new LinkedBlockingQueue<>();
        var container = startListener(PlayerRedisProtocol.Channels.REQUESTS, requestQueue);

        AtomicReference<Boolean> readyCalled = new AtomicReference<>(false);

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
            player.join(new MusicPlayerEventListener() {
                @Override public void onReady() { readyCalled.set(true); }
            });

            String json = requestQueue.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();
            JoinRequest request = objectMapper.readValue(json, JoinRequest.class);

            redis.convertAndSend(PlayerRedisProtocol.Channels.EVENTS,
                    objectMapper.writeValueAsString(new MusicPlayerEvent.Ready(PLAYER_ID, request.requestId(), GUILD_ID)));

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(readyCalled.get()).isTrue());
        } finally {
            container.stop();
        }
    }

    @Test
    void transitionsToConnectedStateOnReady() throws Exception {
        BlockingQueue<String> requestQueue = new LinkedBlockingQueue<>();
        BlockingQueue<String> interactionQueue = new LinkedBlockingQueue<>();
        var requestContainer = startListener(PlayerRedisProtocol.Channels.REQUESTS, requestQueue);

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
            AtomicReference<Boolean> readyCalled = new AtomicReference<>(false);
            player.join(new MusicPlayerEventListener() {
                @Override public void onReady() { readyCalled.set(true); }
            });

            // Get the requestId and simulate player claiming
            String json = requestQueue.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();
            JoinRequest request = objectMapper.readValue(json, JoinRequest.class);
            redis.convertAndSend(PlayerRedisProtocol.Channels.EVENTS,
                    objectMapper.writeValueAsString(new MusicPlayerEvent.Ready(PLAYER_ID, request.requestId(), GUILD_ID)));

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(readyCalled.get()).isTrue());

            // Now verify the player is in Connected state by calling stop() and checking it routes to the player channel
            var stopContainer = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), interactionQueue);
            try {
                player.stop(new MusicPlayerEventListener() {});
                String stopJson = interactionQueue.poll(2, TimeUnit.SECONDS);
                assertThat(stopJson).isNotNull();
                MusicPlayerInteraction interaction = objectMapper.readValue(stopJson, MusicPlayerInteraction.class);
                assertThat(interaction).isInstanceOf(MusicPlayerInteraction.Stop.class);
            } finally {
                stopContainer.stop();
            }
        } finally {
            requestContainer.stop();
        }
    }

    @Test
    void joinInPendingStateCallsOnNoPlayersAvailableAfterTimeout() {
        AtomicReference<Boolean> noPlayersCalled = new AtomicReference<>(false);
        MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
        player.join(new MusicPlayerEventListener() {
            @Override public void onNoPlayersAvailable() { noPlayersCalled.set(true); }
        });

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(noPlayersCalled.get()).isTrue());
    }

    // --- CONNECTED STATE ---

    @Test
    void joinInConnectedStatePublishesConnectToTargetedChannel() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);

        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        var container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
            player.join(new MusicPlayerEventListener() {});

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();
            MusicPlayerInteraction interaction = objectMapper.readValue(json, MusicPlayerInteraction.class);
            assertThat(interaction).isInstanceOf(MusicPlayerInteraction.Connect.class);
            MusicPlayerInteraction.Connect connect = (MusicPlayerInteraction.Connect) interaction;
            assertThat(connect.playerId()).isEqualTo(PLAYER_ID);
            assertThat(connect.requestId()).isNotBlank();
            assertThat(connect.guildId()).isEqualTo(GUILD_ID);
            assertThat(connect.voiceChannelId()).isEqualTo(VOICE_CHANNEL_ID);
        } finally {
            container.stop();
        }
    }

    @Test
    void stopPublishesStopToPlayerChannel() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);

        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        var container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
            player.stop(new MusicPlayerEventListener() {});

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();
            MusicPlayerInteraction interaction = objectMapper.readValue(json, MusicPlayerInteraction.class);
            assertThat(interaction).isInstanceOf(MusicPlayerInteraction.Stop.class);
        } finally {
            container.stop();
        }
    }

    @Test
    void stopDoesNotDeleteAssignmentKey() {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);

        MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
        player.stop(new MusicPlayerEventListener() {});

        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID))).isEqualTo(PLAYER_ID);
    }

    // --- TIMEOUTS ---

    @Test
    void answersQueueWithNothingWhenBearDoesNotAnswerInTime() {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);

        MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);

        assertThat(player.queue()).isEmpty();
    }

    @Test
    void reportsFailureWhenTogglePauseTimesOut() {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        AtomicBoolean timedOut = new AtomicBoolean(false);

        MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
        player.togglePause(REQUESTER_TAG, new MusicPlayerEventListener() {
            @Override public void onTimedOut() { timedOut.set(true); }
        });

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(timedOut.get()).isTrue());
    }

    @Test
    void reportsFailureWhenNextTimesOut() {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        AtomicBoolean timedOut = new AtomicBoolean(false);

        MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
        player.next(REQUESTER_TAG, new MusicPlayerEventListener() {
            @Override public void onTimedOut() { timedOut.set(true); }
        });

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(timedOut.get()).isTrue());
    }

    @Test
    void reportsTimeoutWhenPlayIsNotAnswered() {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        AtomicBoolean timedOut = new AtomicBoolean(false);

        MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
        player.play(new TrackRequest(QUERY, TEXT_CHANNEL_ID, REQUESTER_TAG), new MusicPlayerEventListener() {
            @Override public void onTimedOut() { timedOut.set(true); }
        });

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(timedOut.get()).isTrue());
    }

    // --- REPLIES ---

    @Test
    void reportsQueuedTrackWithoutTimingOutWhenAgentConfirmsPlay() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        var container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);
        AtomicReference<TrackMetadata> queued = new AtomicReference<>();
        AtomicBoolean timedOut = new AtomicBoolean(false);

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
            player.play(new TrackRequest(QUERY, TEXT_CHANNEL_ID, REQUESTER_TAG), new MusicPlayerEventListener() {
                @Override public void onTrackQueued(TrackMetadata track, int queuedCount) { queued.set(track); }
                @Override public void onTimedOut() { timedOut.set(true); }
            });
            publish(new MusicPlayerEvent.TrackQueued(PLAYER_ID, nextInteraction(received).requestId(), GUILD_ID, TRACK, 1));

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(queued.get()).isEqualTo(TRACK));
            // outlasts the 500ms interaction timeout
            await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).until(() -> !timedOut.get());
        } finally {
            container.stop();
        }
    }

    @Test
    void dropsItsAnnouncerWhenBearHasNoPlayerForPlay() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        var container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);
        // other tests leave announcers behind for the same bear
        announcementRegistry.removeAll(PLAYER_ID, GUILD_ID);
        AtomicBoolean failed = new AtomicBoolean(false);

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
            player.play(new TrackRequest(QUERY, TEXT_CHANNEL_ID, REQUESTER_TAG), new MusicPlayerEventListener() {
                @Override public void onFailed(String reason) { failed.set(true); }
            });
            publish(new MusicPlayerEvent.PlayerNotFound(PLAYER_ID, nextInteraction(received).requestId(), GUILD_ID));

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(failed.get()).isTrue());
            assertThat(announcementRegistry.findAll(PLAYER_ID, GUILD_ID)).isEmpty();
        } finally {
            container.stop();
        }
    }

    @Test
    void reportsRestartWhenAgentRestartsTrack() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        var container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);
        AtomicBoolean restarted = new AtomicBoolean(false);

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
            player.previous(REQUESTER_TAG, new MusicPlayerEventListener() {
                @Override public void onRestarted() { restarted.set(true); }
            });
            publish(new MusicPlayerEvent.Restarted(PLAYER_ID, new Request(nextInteraction(received).requestId(), REQUESTER_TAG), GUILD_ID));

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(restarted.get()).isTrue());
        } finally {
            container.stop();
        }
    }

    @Test
    void reportsAppliedSeekWhenAgentRewindsByItsDefault() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        var container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);
        AtomicReference<Duration> rewound = new AtomicReference<>();

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
            player.rewind(Duration.ZERO, REQUESTER_TAG, new MusicPlayerEventListener() {
                @Override public void onRewound(Duration seek) { rewound.set(seek); }
            });
            publish(new MusicPlayerEvent.Rewound(PLAYER_ID, new Request(nextInteraction(received).requestId(), REQUESTER_TAG), GUILD_ID, 10_000));

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(rewound.get()).isEqualTo(Duration.ofSeconds(10)));
        } finally {
            container.stop();
        }
    }

    @Test
    void reportsNothingPlayingWhenAgentHasNoTrackToForward() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        var container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);
        AtomicBoolean nothingPlaying = new AtomicBoolean(false);

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
            player.forward(Duration.ZERO, REQUESTER_TAG, new MusicPlayerEventListener() {
                @Override public void onNothingPlaying() { nothingPlaying.set(true); }
            });
            publish(new MusicPlayerEvent.NothingPlaying(PLAYER_ID, nextInteraction(received).requestId(), GUILD_ID));

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(nothingPlaying.get()).isTrue());
        } finally {
            container.stop();
        }
    }

    @Test
    void reportsNotSeekableWhenAgentCannotForwardALiveStream() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        var container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);
        AtomicBoolean notSeekable = new AtomicBoolean(false);

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
            player.forward(Duration.ZERO, REQUESTER_TAG, new MusicPlayerEventListener() {
                @Override public void onNotSeekable() { notSeekable.set(true); }
            });
            publish(new MusicPlayerEvent.NotSeekable(PLAYER_ID, nextInteraction(received).requestId(), GUILD_ID));

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(notSeekable.get()).isTrue());
        } finally {
            container.stop();
        }
    }

    @Test
    void reportsClearedWhenAgentConfirmsClear() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        var container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);
        AtomicBoolean cleared = new AtomicBoolean(false);

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
            player.clear(REQUESTER_TAG, new MusicPlayerEventListener() {
                @Override public void onCleared() { cleared.set(true); }
            });
            publish(new MusicPlayerEvent.Cleared(PLAYER_ID, new Request(nextInteraction(received).requestId(), REQUESTER_TAG), GUILD_ID, java.util.List.of()));

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(cleared.get()).isTrue());
        } finally {
            container.stop();
        }
    }

    @Test
    void reportsStoppedWhenAgentConfirmsStop() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        var container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);
        AtomicBoolean stopped = new AtomicBoolean(false);

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
            player.stop(new MusicPlayerEventListener() {
                @Override public void onStopped() { stopped.set(true); }
            });
            publish(new MusicPlayerEvent.Stopped(PLAYER_ID, nextInteraction(received).requestId(), GUILD_ID, StopReason.REQUESTED));

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(stopped.get()).isTrue());
        } finally {
            container.stop();
        }
    }

    @Test
    void keepsAnsweringAfterAListenerThrows() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        var container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);
        AtomicBoolean paused = new AtomicBoolean(false);

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
            player.togglePause(REQUESTER_TAG, new MusicPlayerEventListener() {
                @Override public void onPaused() { throw new IllegalStateException("Discord edit failed"); }
            });
            publish(new MusicPlayerEvent.Paused(PLAYER_ID, new Request(nextInteraction(received).requestId(), REQUESTER_TAG), GUILD_ID));
            player.togglePause(REQUESTER_TAG, new MusicPlayerEventListener() {
                @Override public void onPaused() { paused.set(true); }
            });
            publish(new MusicPlayerEvent.Paused(PLAYER_ID, new Request(nextInteraction(received).requestId(), REQUESTER_TAG), GUILD_ID));

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(paused.get()).isTrue());
        } finally {
            container.stop();
        }
    }

    // --- ANSWERS TO THE PLAYER CONTROLS ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("answers")
    void reportsTheBearsAnswerToAControl(Answer answer) throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        var container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);
        AtomicReference<String> answered = new AtomicReference<>();

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);
            answer.control().send(player, recording(answered));
            publish(answer.reply().apply(nextInteraction(received).requestId()));

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(answered.get()).isEqualTo(answer.expected()));
        } finally {
            container.stop();
        }
    }

    static Stream<Answer> answers() {
        return Stream.of(
                new Answer("removed", (player, listener) -> player.remove(1, "", REQUESTER_TAG, listener),
                        id -> new MusicPlayerEvent.Removed(PLAYER_ID, new Request(id, REQUESTER_TAG), GUILD_ID, TRACK, List.of(), 0, 0),
                        "removed One More Time"),
                new Answer("not in queue", (player, listener) -> player.remove(9, "", REQUESTER_TAG, listener),
                        id -> new MusicPlayerEvent.TrackNotInQueue(PLAYER_ID, id, GUILD_ID),
                        "not in queue"),
                new Answer("shuffle toggled", (player, listener) -> player.toggleShuffle(REQUESTER_TAG, listener),
                        id -> new MusicPlayerEvent.ShuffleChanged(PLAYER_ID, new Request(id, REQUESTER_TAG), GUILD_ID, true, List.of(), 4),
                        "shuffle true"),
                new Answer("volume", (player, listener) -> player.setVolume(40, REQUESTER_TAG, listener),
                        id -> new MusicPlayerEvent.VolumeChanged(PLAYER_ID, new Request(id, REQUESTER_TAG), GUILD_ID, 40),
                        "volume 40"),
                new Answer("loop cycled", (player, listener) -> player.cycleLoop(REQUESTER_TAG, listener),
                        id -> new MusicPlayerEvent.LoopModeChanged(PLAYER_ID, new Request(id, REQUESTER_TAG), GUILD_ID, LoopMode.QUEUE),
                        "loop QUEUE"),
                new Answer("loop set", (player, listener) -> player.setLoop(LoopMode.TRACK, REQUESTER_TAG, listener),
                        id -> new MusicPlayerEvent.LoopModeChanged(PLAYER_ID, new Request(id, REQUESTER_TAG), GUILD_ID, LoopMode.TRACK),
                        "loop TRACK"),
                new Answer("jumped", (player, listener) -> player.jump(Duration.ofSeconds(83), REQUESTER_TAG, listener),
                        id -> new MusicPlayerEvent.Jumped(PLAYER_ID, new Request(id, REQUESTER_TAG), GUILD_ID, 83_000),
                        "jumped PT1M23S"),
                new Answer("jump out of range", (player, listener) -> player.jump(Duration.ofHours(1), REQUESTER_TAG, listener),
                        id -> new MusicPlayerEvent.JumpOutOfRange(PLAYER_ID, id, GUILD_ID, 320_000),
                        "out of range PT5M20S"),
                new Answer("no player", (player, listener) -> player.toggleShuffle(REQUESTER_TAG, listener),
                        id -> new MusicPlayerEvent.PlayerNotFound(PLAYER_ID, id, GUILD_ID),
                        "failed No player found in channel"),
                new Answer("unexpected answer", (player, listener) -> player.setVolume(40, REQUESTER_TAG, listener),
                        id -> new MusicPlayerEvent.Cleared(PLAYER_ID, new Request(id, REQUESTER_TAG), GUILD_ID, List.of()),
                        "failed Could not reach the music players"));
    }

    /** A control sent to the bear, the bear's reply to that request, and what the listener should hear. */
    record Answer(String name, Control control, Function<String, MusicPlayerEvent> reply, String expected) {
        @Override
        public String toString() {
            return name;
        }
    }

    interface Control {
        void send(MusicPlayer player, MusicPlayerEventListener listener);
    }

    private static MusicPlayerEventListener recording(AtomicReference<String> answered) {
        return new MusicPlayerEventListener() {
            @Override public void onFailed(String reason) { answered.set("failed " + reason); }
            @Override public void onRemoved(TrackMetadata track) { answered.set("removed " + track.title()); }
            @Override public void onTrackNotInQueue() { answered.set("not in queue"); }
            @Override public void onShuffleChanged(boolean shuffle) { answered.set("shuffle " + shuffle); }
            @Override public void onVolumeChanged(int volume) { answered.set("volume " + volume); }
            @Override public void onLoopModeChanged(LoopMode mode) { answered.set("loop " + mode); }
            @Override public void onJumped(Duration position) { answered.set("jumped " + position); }
            @Override public void onJumpOutOfRange(Duration trackLength) { answered.set("out of range " + trackLength); }
        };
    }

    // --- HELPERS ---

    private MusicPlayerInteraction nextInteraction(BlockingQueue<String> received) throws Exception {
        String json = received.poll(2, TimeUnit.SECONDS);
        assertThat(json).isNotNull();
        return objectMapper.readValue(json, MusicPlayerInteraction.class);
    }

    private void publish(MusicPlayerEvent event) {
        redis.convertAndSend(PlayerRedisProtocol.Channels.EVENTS, objectMapper.writeValueAsString(event));
    }
}
