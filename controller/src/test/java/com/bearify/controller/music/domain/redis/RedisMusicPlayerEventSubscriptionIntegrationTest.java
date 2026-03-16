package com.bearify.controller.music.domain.redis;

import com.bearify.controller.AbstractControllerIntegrationTest;
import com.bearify.controller.music.domain.MusicPlayerPendingRequests;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RedisMusicPlayerEventSubscriptionIntegrationTest extends AbstractControllerIntegrationTest {

    private static final String PLAYER_ID = "player-1";

    @Autowired StringRedisTemplate redis;
    @Autowired com.bearify.music.player.bridge.protocol.PlayerMessageCodec codec;
    @Autowired MusicPlayerPendingRequests requests;

    // --- HAPPY PATH ---

    @Test
    void routesPlayerReadyEventsToPendingRequests() throws Exception {
        MusicPlayerPendingRequests.Pending pending = requests.register();

        MusicPlayerEvent event = new MusicPlayerEvent.Ready(PLAYER_ID, pending.requestId());
        redis.convertAndSend(PlayerRedisProtocol.Channels.EVENTS, codec.serialize(event));

        MusicPlayerEvent result = pending.future().get(2, TimeUnit.SECONDS);
        assertThat(result).isInstanceOf(MusicPlayerEvent.Ready.class);
        assertThat(((MusicPlayerEvent.Ready) result).playerId()).isEqualTo(PLAYER_ID);
    }
}
