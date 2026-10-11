package com.bearify.controller.music.redis;

import com.bearify.controller.AbstractControllerIntegrationTest;
import com.bearify.controller.music.discord.DiscordPlaybackAnnouncerFactory;
import com.bearify.controller.music.domain.MusicPlayerAnnouncementRegistry;
import com.bearify.controller.music.domain.PlayerMessageStore.PlayerMessage;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.TextChannel;
import com.bearify.discord.testing.MockDiscordClient;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.Request;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerMessageRecoveryTest extends AbstractControllerIntegrationTest {

    private static final String PLAYER_ID = "player-recovery";
    private static final String GUILD_ID = "333";
    private static final String VOICE_CHANNEL_ID = "voice-1";
    private static final String TEXT_CHANNEL_ID = "text-recovery";
    private static final String OTHER_TEXT_CHANNEL_ID = "text-recovery-2";

    @Autowired StringRedisTemplate redis;
    @Autowired MockDiscordClient.Factory discordClientFactory;
    @Autowired DiscordPlaybackAnnouncerFactory announcerFactory;

    private RedisPlayerMessageStore store;
    private MusicPlayerAnnouncementRegistry registry;

    @BeforeEach
    void setUp() {
        cleanUp();
        store = new RedisPlayerMessageStore(redis);
        registry = new MusicPlayerAnnouncementRegistry();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void deletesRememberedMessagesOnStartup() {
        MockDiscordClient discord = discordClientFactory.getLastCreated().orElseThrow();
        store.remember(PLAYER_ID, GUILD_ID, TEXT_CHANNEL_ID, "message-77");
        // the mock Discord client is shared by the whole test context, so only count this test's deletions
        int deletedBefore = discord.deletedMessageIds(TEXT_CHANNEL_ID).size();

        recovery(discord).recover();

        assertThat(discord.deletedMessageIds(TEXT_CHANNEL_ID)).hasSize(deletedBefore + 1).last().isEqualTo("message-77");
        assertThat(store.all()).isEmpty();
    }

    @Test
    void reattachesAnnouncerWhenBearIsStillAssigned() {
        MockDiscordClient discord = discordClientFactory.getLastCreated().orElseThrow();
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        store.remember(PLAYER_ID, GUILD_ID, TEXT_CHANNEL_ID, "message-77");
        int postedBefore = discord.sentComponents(TEXT_CHANNEL_ID).size();

        recovery(discord).recover();
        registry.findAll(PLAYER_ID, GUILD_ID).forEach(announcer -> announcer.accept(trackStart()));

        assertThat(registry.findAll(PLAYER_ID, GUILD_ID)).hasSize(1);
        assertThat(discord.sentComponents(TEXT_CHANNEL_ID)).hasSize(postedBefore + 1);
    }

    @Test
    void forgetsSessionsWhoseBearIsGone() {
        MockDiscordClient discord = discordClientFactory.getLastCreated().orElseThrow();
        store.remember(PLAYER_ID, GUILD_ID, TEXT_CHANNEL_ID, "message-77");

        recovery(discord).recover();

        assertThat(registry.sessions()).isEmpty();
        assertThat(store.all()).isEmpty();
        assertThat(discord.sentMessages(TEXT_CHANNEL_ID)).isEmpty();
    }

    @Test
    void survivesAlreadyDeletedMessages() {
        DiscordClient discord = mock(DiscordClient.class);
        TextChannel gone = mock(TextChannel.class);
        TextChannel present = mock(TextChannel.class);
        when(discord.textChannel(TEXT_CHANNEL_ID)).thenReturn(gone);
        when(discord.textChannel(OTHER_TEXT_CHANNEL_ID)).thenReturn(present);
        doThrow(new IllegalStateException("Unknown Message")).when(gone).delete("message-77");
        store.remember(PLAYER_ID, GUILD_ID, TEXT_CHANNEL_ID, "message-77");
        store.remember(PLAYER_ID, GUILD_ID, OTHER_TEXT_CHANNEL_ID, "message-78");

        recovery(discord).recover();

        verify(present).delete("message-78");
        assertThat(store.all()).isEmpty();
    }

    private PlayerMessageRecovery recovery(DiscordClient discord) {
        return new PlayerMessageRecovery(store, new RedisPlayerAssignments(redis), registry, announcerFactory, discord);
    }

    private static MusicPlayerEvent.TrackStart trackStart() {
        return new MusicPlayerEvent.TrackStart(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID,
                new TrackMetadata("Song", "Artist", "https://example.com", 60_000), List.of(), 0, 0L);
    }

    private void cleanUp() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        var keys = redis.keys(RedisPlayerMessageStore.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }


    @Test
    void keepsMessagePostedInTheSameChannelWhileStarting() {
        MockDiscordClient discord = discordClientFactory.getLastCreated().orElseThrow();
        store.remember(PLAYER_ID, GUILD_ID, TEXT_CHANNEL_ID, "message-77");
        PlayerMessageRecovery recovery = recovery(discord);
        // a /player play during startup posts a live message in the same channel before recovery runs
        store.remember(PLAYER_ID, GUILD_ID, TEXT_CHANNEL_ID, "message-live");
        int deletedBefore = discord.deletedMessageIds(TEXT_CHANNEL_ID).size();

        recovery.recover();

        assertThat(discord.deletedMessageIds(TEXT_CHANNEL_ID)).hasSize(deletedBefore + 1).last().isEqualTo("message-77");
        assertThat(store.all()).containsExactly(new PlayerMessage(PLAYER_ID, GUILD_ID, TEXT_CHANNEL_ID, "message-live"));
    }

    @Test
    void forgetsMessageWhoseChannelIsGoneAndStillReattaches() {
        DiscordClient discord = mock(DiscordClient.class);
        when(discord.textChannel(TEXT_CHANNEL_ID)).thenThrow(new IllegalArgumentException("Unknown text channel"));
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        store.remember(PLAYER_ID, GUILD_ID, TEXT_CHANNEL_ID, "message-77");

        recovery(discord).recover();

        assertThat(store.all()).isEmpty();
        assertThat(registry.findAll(PLAYER_ID, GUILD_ID)).hasSize(1);
    }
}
