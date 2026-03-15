package com.bearify.controller.music.domain.redis;

import com.bearify.controller.AbstractControllerIntegrationTest;
import com.bearify.controller.music.domain.MusicPlayerEvent;
import com.bearify.controller.music.domain.MusicPlayerEventHandler;
import com.bearify.controller.music.domain.MusicPlayerRequestRegistry;
import com.bearify.discord.testing.MockEditableMessage;
import com.bearify.shared.events.PlayerEvent;
import com.bearify.shared.player.PlayerMessageCodec;
import com.bearify.shared.player.PlayerRedisProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RedisMusicPlayerEventSubscriptionIntegrationTest extends AbstractControllerIntegrationTest {

    private static final String PLAYER_ID = "player-1";
    private static final String REQUEST_ID = "req-1";

    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper objectMapper;
    @Autowired MusicPlayerRequestRegistry requests;

    // --- HAPPY PATH ---

    @Test
    void routesPlayerReadyEventsToPendingRequests() {
        MockEditableMessage message = new MockEditableMessage();
        requests.register(REQUEST_ID, new MusicPlayerEventHandler() {
            @Override
            public void onReady(MusicPlayerEvent.Ready event) {
                message.edit("handled " + event.playerId());
            }
        });

        PlayerEvent event = new PlayerEvent.PlayerReady(PLAYER_ID, REQUEST_ID);
        String json = PlayerMessageCodec.writeEvent(objectMapper, event);
        redis.convertAndSend(PlayerRedisProtocol.EVENTS_CHANNEL, json);

        await().atMost(2, TimeUnit.SECONDS).until(message::hasEdits);

        assertThat(message.getLastEdit().orElseThrow()).contains(PLAYER_ID);
    }
}
