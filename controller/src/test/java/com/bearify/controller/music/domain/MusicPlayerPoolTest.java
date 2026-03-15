package com.bearify.controller.music.domain;

import com.bearify.controller.music.domain.redis.MusicPlayerAllocator;
import com.bearify.controller.music.domain.redis.MusicPlayerInteractionPublisher;
import com.bearify.shared.events.PlayerInteraction;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerPoolTest {

    private static final String GUILD_ID = "guild-1";
    private static final String VOICE_CHANNEL_ID = "voice-1";

    // --- HAPPY PATH ---

    @Test
    void returnsExistingAssignedPlayerForVoiceChannel() {
        FakeAllocator allocator = new FakeAllocator();
        allocator.assigned.put(key(GUILD_ID, VOICE_CHANNEL_ID), "player-1");
        MusicPlayerPool pool = new MusicPlayerPool(allocator, new MusicPlayerRequestRegistry(), new NoOpInteractionPublisher());

        Optional<MusicPlayer> result = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);

        assertThat(result).isPresent();
        assertThat(allocator.claimed).isEmpty();
    }

    @Test
    void claimsAvailablePlayerWhenAssignmentDoesNotExist() {
        FakeAllocator allocator = new FakeAllocator();
        allocator.available.add("player-1");
        MusicPlayerPool pool = new MusicPlayerPool(allocator, new MusicPlayerRequestRegistry(), new NoOpInteractionPublisher());

        Optional<MusicPlayer> result = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);

        assertThat(result).isPresent();
        assertThat(allocator.assigned.get(key(GUILD_ID, VOICE_CHANNEL_ID))).isEqualTo("player-1");
        assertThat(allocator.available).doesNotContain("player-1");
    }

    // --- EDGE CASES ---

    @Test
    void returnsEmptyWhenNoPlayersAreAvailable() {
        MusicPlayerPool pool = new MusicPlayerPool(new FakeAllocator(), new MusicPlayerRequestRegistry(), new NoOpInteractionPublisher());

        assertThat(pool.acquire(GUILD_ID, VOICE_CHANNEL_ID)).isEmpty();
    }

    @Test
    void returnsAssignedPlayerWhenGuildWasClaimedConcurrently() {
        FakeAllocator allocator = new FakeAllocator();
        allocator.available.add("player-1");
        allocator.assignResult = false;
        allocator.assignedAfterFailedAssign.put(key(GUILD_ID, VOICE_CHANNEL_ID), "player-2");
        MusicPlayerPool pool = new MusicPlayerPool(allocator, new MusicPlayerRequestRegistry(), new NoOpInteractionPublisher());

        Optional<MusicPlayer> result = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID);

        assertThat(result).isPresent();
        assertThat(allocator.available).contains("player-1");
        assertThat(allocator.released).contains("player-1");
        assertThat(allocator.assigned.get(key(GUILD_ID, VOICE_CHANNEL_ID))).isEqualTo("player-2");
    }

    private static String key(String guildId, String voiceChannelId) {
        return guildId + "::" + voiceChannelId;
    }

    private static final class FakeAllocator implements MusicPlayerAllocator {
        private final Map<String, String> assigned = new HashMap<>();
        private final Map<String, String> assignedAfterFailedAssign = new HashMap<>();
        private final Set<String> available = new HashSet<>();
        private final Set<String> claimed = new HashSet<>();
        private final Set<String> released = new HashSet<>();
        private boolean assignResult = true;

        @Override
        public Optional<String> findAssignedTo(String guildId, String voiceChannelId) {
            return Optional.ofNullable(assigned.get(key(guildId, voiceChannelId)));
        }

        @Override
        public Optional<String> claim() {
            String playerId = available.stream().findFirst().orElse(null);
            if (playerId == null) {
                return Optional.empty();
            }
            available.remove(playerId);
            claimed.add(playerId);
            return Optional.of(playerId);
        }

        @Override
        public boolean assign(String guildId, String voiceChannelId, String playerId) {
            if (assignResult) {
                assigned.put(key(guildId, voiceChannelId), playerId);
            } else {
                String key = key(guildId, voiceChannelId);
                if (assignedAfterFailedAssign.containsKey(key)) {
                    assigned.put(key, assignedAfterFailedAssign.get(key));
                }
            }
            return assignResult;
        }

        @Override
        public void release(String playerId) {
            available.add(playerId);
            released.add(playerId);
        }
    }

    private static final class NoOpInteractionPublisher implements MusicPlayerInteractionPublisher {
        @Override
        public void connect(PlayerInteraction.Connect interaction) {
        }
    }
}
