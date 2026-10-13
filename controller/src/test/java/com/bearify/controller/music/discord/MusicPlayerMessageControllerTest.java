package com.bearify.controller.music.discord;

import com.bearify.controller.AbstractControllerIntegrationTest;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.testing.MockDiscordClient;
import com.bearify.discord.testing.MockMessageContextInteraction;
import com.bearify.music.player.bridge.events.JoinRequest;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "music-player.pool.connect-request-ttl=200ms")
class MusicPlayerMessageControllerTest extends AbstractControllerIntegrationTest {

    private static final String VOICE_CHANNEL_ID = "vc-123";
    private static final String GUILD_ID = "guild-456";
    private static final String TEXT_CHANNEL_ID = "txt-789";
    private static final String PLAYER_ID = "player-1";
    private static final String AUTHOR = "<@42>";
    private static final String FIRST_LINK = "https://youtu.be/dQw4w9WgXcQ";
    private static final String SECOND_LINK = "https://soundcloud.com/rick/never";
    private static final String THIRD_LINK = "https://vimeo.com/123456";
    private static final TrackMetadata TRACK = new TrackMetadata("Never Gonna Give You Up", "Rick Astley", FIRST_LINK, 213_000);

    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper objectMapper;
    @Autowired MockDiscordClient.Factory discordClientFactory;

    private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
    private RedisMessageListenerContainer container;

    @BeforeEach
    void listenToPlayer() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);
    }

    @AfterEach
    void stopListening() {
        container.stop();
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
    }

    // happy path

    @Test
    void registersPlayWithBearifyAsMessageCommand() {
        assertThat(client().getCommands()).contains(CommandDefinition.message("Play with Bearify"));
    }

    @Test
    void playsSingleLinkFromMessage() throws Exception {
        assignPlayer();
        MockMessageContextInteraction interaction = interaction("listen to this " + FIRST_LINK);

        client().dispatchMessageContext(interaction);

        MusicPlayerInteraction.Play play = nextPlay();
        assertThat(play.trackRequest().query()).isEqualTo(FIRST_LINK);
        assertThat(play.trackRequest().textChannelId()).isEqualTo(TEXT_CHANNEL_ID);
        queue(play, 1);
        awaitReply(interaction, "🎵 | Queued **Never Gonna Give You Up**.");
        assertThat(interaction.isDeferredEphemeral()).isTrue();
    }

    @Test
    void queuesMultipleLinksInMessageOrder() throws Exception {
        assignPlayer();
        MockMessageContextInteraction interaction = interaction(FIRST_LINK + " then " + SECOND_LINK + " and " + THIRD_LINK);

        client().dispatchMessageContext(interaction);

        List<String> queries = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            MusicPlayerInteraction.Play play = nextPlay();
            // the next link is only sent once the bear answered this one
            assertThat(received.poll(200, TimeUnit.MILLISECONDS)).isNull();
            queries.add(play.trackRequest().query());
            queue(play, 1);
        }

        assertThat(queries).containsExactly(FIRST_LINK, SECOND_LINK, THIRD_LINK);
        awaitReply(interaction, "🎵 | Queued 3 tracks from " + AUTHOR + "'s message.");
    }

    @Test
    void searchesMessageTextWhenNoLinks() throws Exception {
        assignPlayer();
        MockMessageContextInteraction interaction = interaction("we should listen to Bohemian Rhapsody <@123>");

        client().dispatchMessageContext(interaction);

        MusicPlayerInteraction.Play play = nextPlay();
        assertThat(play.trackRequest().query()).isEqualTo("ytsearch:we should listen to Bohemian Rhapsody");
        queue(play, 1);
        awaitReply(interaction, "🎵 | Queued **Never Gonna Give You Up**. Wrong one? `/player remove` it.");
    }

    @Test
    void summonsBearWhenNoneInChannel() throws Exception {
        BlockingQueue<String> requests = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer requestsContainer = startListener(PlayerRedisProtocol.Channels.REQUESTS, requests);
        try {
            MockMessageContextInteraction interaction = interaction(FIRST_LINK);

            client().dispatchMessageContext(interaction);

            assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow()).contains("Sending a bear your way");
            JoinRequest request = objectMapper.readValue(requests.poll(2, TimeUnit.SECONDS), JoinRequest.class);
            assertThat(request.voiceChannelId()).isEqualTo(VOICE_CHANNEL_ID);
            redis.convertAndSend(PlayerRedisProtocol.Channels.EVENTS, objectMapper.writeValueAsString(
                    new MusicPlayerEvent.Ready(PLAYER_ID, request.requestId(), GUILD_ID)));

            MusicPlayerInteraction.Play play = nextPlay();
            assertThat(play.trackRequest().query()).isEqualTo(FIRST_LINK);
            queue(play, 1);
            awaitReply(interaction, "🎵 | Queued **Never Gonna Give You Up**.");
        } finally {
            requestsContainer.stop();
        }
    }

    // exceptions

    @Test
    void rejectsUserOutsideVoice() throws Exception {
        MockMessageContextInteraction interaction = MockMessageContextInteraction.forMessageCommand("Play with Bearify")
                .targetMessage(FIRST_LINK)
                .guildId(GUILD_ID)
                .textChannelId(TEXT_CHANNEL_ID)
                .build();

        client().dispatchMessageContext(interaction);

        assertThat(interaction.getReplies()).singleElement().satisfies(reply -> {
            assertThat(reply.getContent()).contains("not in a voice channel");
            assertThat(reply.isEphemeral()).isTrue();
        });
        assertThat(received.poll(200, TimeUnit.MILLISECONDS)).isNull();
    }

    // edge cases

    @Test
    void explainsNothingToPlay() throws Exception {
        assignPlayer();
        MockMessageContextInteraction interaction = interaction("https://example.com/song.mp3");

        client().dispatchMessageContext(interaction);

        assertThat(interaction.getReplies()).singleElement().satisfies(reply -> {
            assertThat(reply.getContent()).isEqualTo("🛑️ | Nothing to play in that message.");
            assertThat(reply.isEphemeral()).isTrue();
        });
        assertThat(interaction.getDeferredMessage()).isEmpty();
        assertThat(received.poll(200, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void reportsLinksThatCouldNotBePlayed() throws Exception {
        assignPlayer();
        MockMessageContextInteraction interaction = interaction(FIRST_LINK + " " + SECOND_LINK);

        client().dispatchMessageContext(interaction);

        MusicPlayerInteraction.Play first = nextPlay();
        redis.convertAndSend(PlayerRedisProtocol.Channels.EVENTS, objectMapper.writeValueAsString(
                new MusicPlayerEvent.TrackNotFound(PLAYER_ID, first.requestId(), GUILD_ID, FIRST_LINK)));
        MusicPlayerInteraction.Play second = nextPlay();
        assertThat(second.trackRequest().query()).isEqualTo(SECOND_LINK);
        queue(second, 1);

        awaitReply(interaction, "🎵 | Queued 1 track from " + AUTHOR + "'s message. (1 link couldn't be played.)");
    }

    // helpers

    private MockDiscordClient client() {
        return discordClientFactory.getLastCreated().orElseThrow();
    }

    private void assignPlayer() {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
    }

    private static MockMessageContextInteraction interaction(String content) {
        return MockMessageContextInteraction.forMessageCommand("Play with Bearify")
                .targetMessage(content)
                .targetAuthorMention(AUTHOR)
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .textChannelId(TEXT_CHANNEL_ID)
                .build();
    }

    private MusicPlayerInteraction.Play nextPlay() throws Exception {
        String json = received.poll(2, TimeUnit.SECONDS);
        assertThat(json).isNotNull();
        return (MusicPlayerInteraction.Play) objectMapper.readValue(json, MusicPlayerInteraction.class);
    }

    private void queue(MusicPlayerInteraction.Play play, int queuedCount) {
        redis.convertAndSend(PlayerRedisProtocol.Channels.EVENTS, objectMapper.writeValueAsString(
                new MusicPlayerEvent.TrackQueued(PLAYER_ID, play.requestId(), GUILD_ID, TRACK, queuedCount)));
    }

    private static void awaitReply(MockMessageContextInteraction interaction, String reply) {
        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow()).isEqualTo(reply));
    }
}
