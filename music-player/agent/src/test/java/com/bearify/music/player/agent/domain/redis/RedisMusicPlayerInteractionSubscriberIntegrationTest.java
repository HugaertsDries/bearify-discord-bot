package com.bearify.music.player.agent.domain.redis;

import com.bearify.music.player.agent.AbstractAgentIntegrationTest;
import com.bearify.music.player.agent.StubVoiceConnectionManager;
import com.bearify.music.player.agent.domain.VoiceConnectionManager;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.protocol.PlayerMessageCodec;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RedisMusicPlayerInteractionSubscriberIntegrationTest extends AbstractAgentIntegrationTest {

    private static final String PLAYER_ID        = "test-player";
    private static final String REQUEST_ID       = "req-1";
    private static final String VOICE_CHANNEL_ID = "vc-123";
    private static final String GUILD_ID         = "guild-456";

    @Autowired StringRedisTemplate redis;
    @Autowired PlayerMessageCodec codec;
    @Autowired VoiceConnectionManager voiceConnectionManager;

    @BeforeEach
    void resetStub() {
        ((StubVoiceConnectionManager) voiceConnectionManager).reset();
    }

    // --- HAPPY PATH ---

    @Test
    void connectsToVoiceChannelWhenConnectCommandIsReceived() throws Exception {
        MusicPlayerInteraction command = new MusicPlayerInteraction.Connect(PLAYER_ID, REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID);
        redis.convertAndSend(PlayerRedisProtocol.Channels.commands(PLAYER_ID), codec.serialize(command));

        StubVoiceConnectionManager stub = (StubVoiceConnectionManager) voiceConnectionManager;
        await().atMost(2, TimeUnit.SECONDS).until(() -> !stub.getCalls().isEmpty());

        assertThat(stub.getCalls()).hasSize(1);
        assertThat(stub.getCalls().getFirst().requestId()).isEqualTo(REQUEST_ID);
        assertThat(stub.getCalls().getFirst().voiceChannelId()).isEqualTo(VOICE_CHANNEL_ID);
        assertThat(stub.getCalls().getFirst().guildId()).isEqualTo(GUILD_ID);
    }

    @Test
    void disconnectsWhenStopCommandIsReceived() throws Exception {
        MusicPlayerInteraction command = new MusicPlayerInteraction.Stop(PLAYER_ID, REQUEST_ID, GUILD_ID);
        redis.convertAndSend(PlayerRedisProtocol.Channels.commands(PLAYER_ID), codec.serialize(command));

        StubVoiceConnectionManager stub = (StubVoiceConnectionManager) voiceConnectionManager;
        await().atMost(2, TimeUnit.SECONDS).until(stub::isDisconnected);

        assertThat(stub.getDisconnectedGuilds()).containsExactly(GUILD_ID);
    }
}
