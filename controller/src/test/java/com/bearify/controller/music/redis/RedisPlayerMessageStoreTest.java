package com.bearify.controller.music.redis;

import com.bearify.controller.AbstractControllerIntegrationTest;
import com.bearify.controller.music.domain.PlayerMessageStore.PlayerMessage;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RedisPlayerMessageStoreTest extends AbstractControllerIntegrationTest {

    private static final String PLAYER_ID = "player-message-store";
    // a player ID may contain ':', which must not leak into the guild ID
    private static final String PLAYER_ID_WITH_COLON = "bear:2";
    private static final String GUILD_ID = "111";
    private static final String OTHER_GUILD_ID = "222";

    @Autowired StringRedisTemplate redis;

    private RedisPlayerMessageStore store;

    @BeforeEach
    void setUp() {
        deleteStoredMessages();
        store = new RedisPlayerMessageStore(redis);
    }

    @AfterEach
    void tearDown() {
        deleteStoredMessages();
    }

    @Test
    void remembersAndForgetsPlayerMessages() {
        store.remember(PLAYER_ID, GUILD_ID, "text-1", "message-1");
        store.remember(PLAYER_ID, GUILD_ID, "text-2", "message-2");

        store.forget(PLAYER_ID, GUILD_ID, "text-1", "message-1");

        assertThat(remembered()).containsExactly(new PlayerMessage(PLAYER_ID, GUILD_ID, "text-2", "message-2"));
    }

    @Test
    void listsAllRememberedMessages() {
        store.remember(PLAYER_ID, GUILD_ID, "text-1", "message-1");
        store.remember(PLAYER_ID_WITH_COLON, OTHER_GUILD_ID, "text-2", "message-2");

        assertThat(remembered()).containsExactlyInAnyOrder(
                new PlayerMessage(PLAYER_ID, GUILD_ID, "text-1", "message-1"),
                new PlayerMessage(PLAYER_ID_WITH_COLON, OTHER_GUILD_ID, "text-2", "message-2"));
    }

    @Test
    void replacesTheMessageOfAChannelWhenRememberedAgain() {
        store.remember(PLAYER_ID, GUILD_ID, "text-1", "message-1");
        store.remember(PLAYER_ID, GUILD_ID, "text-1", "message-2");

        assertThat(remembered()).containsExactly(new PlayerMessage(PLAYER_ID, GUILD_ID, "text-1", "message-2"));
    }

    private void deleteStoredMessages() {
        redis.delete(List.of(PlayerRedisProtocol.Keys.messages(PLAYER_ID, GUILD_ID), PlayerRedisProtocol.Keys.messages(PLAYER_ID_WITH_COLON, OTHER_GUILD_ID)));
    }

    // other tests share this Redis, so only this test's bears count
    private List<PlayerMessage> remembered() {
        return store.all().stream().filter(message -> Set.of(PLAYER_ID, PLAYER_ID_WITH_COLON).contains(message.playerId())).toList();
    }


    @Test
    void keepsNewerMessageWhenForgettingAnOlderOne() {
        store.remember(PLAYER_ID, GUILD_ID, "text-1", "message-1");
        store.remember(PLAYER_ID, GUILD_ID, "text-1", "message-2");

        store.forget(PLAYER_ID, GUILD_ID, "text-1", "message-1");

        assertThat(remembered()).containsExactly(new PlayerMessage(PLAYER_ID, GUILD_ID, "text-1", "message-2"));
    }
}
