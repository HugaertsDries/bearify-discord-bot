package com.bearify.music.player.agent.redis;

import com.bearify.music.player.agent.AbstractAgentIntegrationTest;
import com.bearify.music.player.agent.RecordingVoiceConnectionManager;
import com.bearify.music.player.agent.domain.VoiceConnectionManager;
import com.bearify.music.player.agent.port.RecordingMusicPlayerInteractionDispatcher;
import com.bearify.music.player.agent.port.MusicPlayerInteractionDispatcher;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Import(MusicPlayerInteractionChannelListenerIntegrationTest.TestConfig.class)
class MusicPlayerInteractionChannelListenerIntegrationTest extends AbstractAgentIntegrationTest {

    private static final String PLAYER_ID        = "test-player";
    private static final String REQUEST_ID       = "req-1";
    private static final String VOICE_CHANNEL_ID = "vc-123";
    private static final String GUILD_ID         = "guild-456";

    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper objectMapper;
    @Autowired RecordingVoiceConnectionManager recordingVoiceConnectionManager;
    @Autowired RecordingMusicPlayerInteractionDispatcher dispatcher;

    @BeforeEach
    void resetStub() {
        recordingVoiceConnectionManager.reset();
        dispatcher.reset();
    }

    // --- HAPPY PATH ---

    @Test
    void connectsToVoiceChannelWhenConnectInteractionIsReceived() throws Exception {
        MusicPlayerInteraction interaction = new MusicPlayerInteraction.Connect(PLAYER_ID, REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID);
        redis.convertAndSend(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), objectMapper.writeValueAsString(interaction));

        await().atMost(2, TimeUnit.SECONDS).until(() -> !recordingVoiceConnectionManager.getCalls().isEmpty());

        assertThat(recordingVoiceConnectionManager.getCalls()).hasSize(1);
        assertThat(recordingVoiceConnectionManager.getCalls().getFirst().requestId()).isEqualTo(REQUEST_ID);
        assertThat(recordingVoiceConnectionManager.getCalls().getFirst().voiceChannelId()).isEqualTo(VOICE_CHANNEL_ID);
        assertThat(recordingVoiceConnectionManager.getCalls().getFirst().guildId()).isEqualTo(GUILD_ID);
    }

    @Test
    void disconnectsWhenStopInteractionIsReceived() throws Exception {
        MusicPlayerInteraction interaction = new MusicPlayerInteraction.Stop(PLAYER_ID, REQUEST_ID, GUILD_ID);
        redis.convertAndSend(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), objectMapper.writeValueAsString(interaction));

        await().atMost(2, TimeUnit.SECONDS).until(recordingVoiceConnectionManager::isDisconnected);

        assertThat(recordingVoiceConnectionManager.getDisconnectedGuilds()).containsExactly(GUILD_ID);
    }

    @Test
    void handlesSearchInteractionsPublishedOnSharedSearchChannel() throws Exception {
        MusicPlayerInteraction interaction = new MusicPlayerInteraction.Search(REQUEST_ID, GUILD_ID, "daft punk", 5);
        redis.convertAndSend(PlayerRedisProtocol.Channels.SEARCH, objectMapper.writeValueAsString(interaction));

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(dispatcher.interactions()).anySatisfy(candidate -> {
                    assertThat(candidate).isInstanceOf(MusicPlayerInteraction.Search.class);
                    MusicPlayerInteraction.Search search = (MusicPlayerInteraction.Search) candidate;
                    assertThat(search.requestId()).isEqualTo(REQUEST_ID);
                    assertThat(search.query()).isEqualTo("daft punk");
                    assertThat(search.limit()).isEqualTo(5);
                }));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        RecordingVoiceConnectionManager recordingVoiceConnectionManager() {
            return new RecordingVoiceConnectionManager();
        }

        @Bean
        @Primary
        VoiceConnectionManager primaryVoiceConnectionManager(RecordingVoiceConnectionManager recording) {
            return recording;
        }

        @Bean
        @Primary
        MusicPlayerInteractionDispatcher primaryMusicPlayerInteractionDispatcher(RecordingMusicPlayerInteractionDispatcher recording) {
            return recording;
        }
    }
}
