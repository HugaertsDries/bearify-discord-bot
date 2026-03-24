package com.bearify.music.player.agent.domain;

import com.bearify.music.player.agent.config.PlayerProperties;
import com.bearify.music.player.agent.port.MusicPlayerEventDispatcher;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AudioPlayerPoolTest {

    @Test
    void returnsSnapshotOfActiveGuildIds() {
        var pool = new AudioPlayerPool(
                mock(MusicPlayerEventDispatcher.class),
                new PlayerProperties(
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(5),
                        new PlayerProperties.Assignment(
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(10)),
                        new PlayerProperties.Engine(new PlayerProperties.Engine.Youtube(null))),
                "player-1");

        pool.getOrCreate("guild-1");
        pool.getOrCreate("guild-2");

        var ids = pool.activeGuildIds();
        assertThat(ids).containsExactlyInAnyOrder("guild-1", "guild-2");

        // Verify it's a snapshot — modifying the returned set doesn't affect the pool
        ids.clear();
        assertThat(pool.activeGuildIds()).containsExactlyInAnyOrder("guild-1", "guild-2");
    }

    @Test
    void returnsEmptySetWhenNoActiveGuilds() {
        var pool = new AudioPlayerPool(
                mock(MusicPlayerEventDispatcher.class),
                new PlayerProperties(
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(5),
                        new PlayerProperties.Assignment(
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(10)),
                        new PlayerProperties.Engine(new PlayerProperties.Engine.Youtube(null))),
                "player-1");

        assertThat(pool.activeGuildIds()).isEmpty();
    }
}
