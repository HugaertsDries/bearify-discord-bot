package com.bearify.music.player.agent;

import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationIntegrationTest extends AbstractAgentIntegrationTest {

    private static final String PLAYER_ID = "test-player";
    // what AbstractAgentIntegrationTest's Discord client says it is logged in as
    private static final String BOT_USER_ID = "test-bot-user";

    @Autowired StringRedisTemplate redis;
    @Autowired @Qualifier("cleanup") SmartLifecycle cleanup;

    @BeforeEach
    void setUp() {
        redis.delete(PlayerRedisProtocol.Keys.bot(PLAYER_ID));
    }

    @AfterEach
    void tearDown() {
        redis.delete(PlayerRedisProtocol.Keys.bot(PLAYER_ID));
    }

    @Test
    void writesItsDiscordUserIdWhenStarted() {
        cleanup.start();

        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.bot(PLAYER_ID))).isEqualTo(BOT_USER_ID);
    }
}
