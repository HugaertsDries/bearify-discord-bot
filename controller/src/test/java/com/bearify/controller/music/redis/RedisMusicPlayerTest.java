package com.bearify.controller.music.redis;

import com.bearify.controller.AbstractControllerIntegrationTest;
import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.music.player.bridge.events.JoinRequest;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "music-player.pool.connect-request-ttl=500ms")
class RedisMusicPlayerTest extends AbstractControllerIntegrationTest {

    private static final String PLAYER_ID = "player-1";
    private static final String GUILD_ID = "guild-1";
    private static final String VOICE_CHANNEL_ID = "voice-1";

    @Autowired MusicPlayerPool pool;
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
                    objectMapper.writeValueAsString(new MusicPlayerEvent.Ready(PLAYER_ID, request.requestId())));

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
                    objectMapper.writeValueAsString(new MusicPlayerEvent.Ready(PLAYER_ID, request.requestId())));

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(readyCalled.get()).isTrue());

            // Now verify the player is in Connected state by calling stop() and checking it routes to the player channel
            var stopContainer = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), interactionQueue);
            try {
                player.stop();
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
            player.stop();

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
        player.stop();

        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID))).isEqualTo(PLAYER_ID);
    }
}
