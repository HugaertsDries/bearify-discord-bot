package com.bearify.music.player.agent.redis;

import com.bearify.music.player.agent.AbstractAgentIntegrationTest;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerRegistrationIntegrationTest extends AbstractAgentIntegrationTest {

    private static final String PLAYER_ID = "test-player";

    @Autowired StringRedisTemplate redis;
    @Autowired MusicPlayerRegistration registration;

    @BeforeEach
    void setup() {
        redis.delete(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS);
        registration.start();
    }

    @AfterEach
    void cleanup() {
        redis.delete(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS);
    }

    // --- LIFECYCLE ---

    @Test
    void registersPlayerOnStartup() {
        assertThat(redis.opsForSet().isMember(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS, PLAYER_ID)).isTrue();
    }

    @Test
    void deregistersPlayerOnStop() {
        registration.stop();

        assertThat(redis.opsForSet().isMember(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS, PLAYER_ID)).isFalse();
    }

    @Test
    void reregistersPlayerAfterStop() {
        registration.stop();
        registration.start();

        assertThat(redis.opsForSet().isMember(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS, PLAYER_ID)).isTrue();
    }
}
