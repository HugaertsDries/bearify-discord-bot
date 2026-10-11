package com.bearify.controller.music.redis;

import com.bearify.controller.AbstractControllerIntegrationTest;
import com.bearify.controller.music.domain.PlayerMessageStore.PlayerMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RedisPlayerMessageStoreTest extends AbstractControllerIntegrationTest {

    private static final String PLAYER_ID = "player-1";
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

        assertThat(store.all()).containsExactly(new PlayerMessage(PLAYER_ID, GUILD_ID, "text-2", "message-2"));
    }

    @Test
    void listsAllRememberedMessages() {
        store.remember(PLAYER_ID, GUILD_ID, "text-1", "message-1");
        store.remember(PLAYER_ID_WITH_COLON, OTHER_GUILD_ID, "text-2", "message-2");

        assertThat(store.all()).containsExactlyInAnyOrder(
                new PlayerMessage(PLAYER_ID, GUILD_ID, "text-1", "message-1"),
                new PlayerMessage(PLAYER_ID_WITH_COLON, OTHER_GUILD_ID, "text-2", "message-2"));
    }

    @Test
    void replacesTheMessageOfAChannelWhenRememberedAgain() {
        store.remember(PLAYER_ID, GUILD_ID, "text-1", "message-1");
        store.remember(PLAYER_ID, GUILD_ID, "text-1", "message-2");

        assertThat(store.all()).containsExactly(new PlayerMessage(PLAYER_ID, GUILD_ID, "text-1", "message-2"));
    }

    private void deleteStoredMessages() {
        var keys = redis.keys(RedisPlayerMessageStore.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }


    @Test
    void keepsNewerMessageWhenForgettingAnOlderOne() {
        store.remember(PLAYER_ID, GUILD_ID, "text-1", "message-1");
        store.remember(PLAYER_ID, GUILD_ID, "text-1", "message-2");

        store.forget(PLAYER_ID, GUILD_ID, "text-1", "message-1");

        assertThat(store.all()).containsExactly(new PlayerMessage(PLAYER_ID, GUILD_ID, "text-1", "message-2"));
    }
}
