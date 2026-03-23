package com.bearify.music.player.agent.domain;

import com.bearify.music.player.agent.AbstractAgentIntegrationTest;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "discord.token=test-token",
        "player.id=test-player",
        "music-player.assignment.ttl=30s",
        "music-player.assignment.heartbeat-interval=10s"
})
class AssignmentHeartbeatTest extends AbstractAgentIntegrationTest {

    private static final String GUILD_ID = "guild-1";
    private static final String CHANNEL_ID = "voice-1";

    @Autowired AssignmentHeartbeat heartbeat;
    @Autowired StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, CHANNEL_ID));
    }

    @Test
    void doesNotRenewWhenNoActiveGuilds() {
        // The AbstractAgentIntegrationTest NoOpDiscordClient has no voice sessions,
        // so heartbeat will not renew any keys.
        heartbeat.renew();
        // No keys should be written since no guilds are active
        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, CHANNEL_ID))).isNull();
    }
}
