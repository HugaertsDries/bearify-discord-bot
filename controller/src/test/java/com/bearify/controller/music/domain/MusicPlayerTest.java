package com.bearify.controller.music.domain;

import com.bearify.controller.music.domain.redis.MusicPlayerInteractionPublisher;
import com.bearify.shared.events.PlayerInteraction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerTest {

    private static final String PLAYER_ID = "player-1";
    private static final String GUILD_ID = "guild-1";
    private static final String VOICE_CHANNEL_ID = "voice-1";

    // --- HAPPY PATH ---

    @Test
    void registersHandlerAndPublishesConnectInteractionWhenJoining() {
        MusicPlayerRequestRegistry requests = new MusicPlayerRequestRegistry();
        RecordingInteractionPublisher interactionPublisher = new RecordingInteractionPublisher();
        MusicPlayer player = new MusicPlayer(PLAYER_ID, GUILD_ID, VOICE_CHANNEL_ID, requests, interactionPublisher);
        MusicPlayerEventHandler handler = new MusicPlayerEventHandler() {};

        player.join(handler);

        assertThat(interactionPublisher.connect).isNotNull();
        assertThat(interactionPublisher.connect.playerId()).isEqualTo(PLAYER_ID);
        assertThat(interactionPublisher.connect.guildId()).isEqualTo(GUILD_ID);
        assertThat(interactionPublisher.connect.voiceChannelId()).isEqualTo(VOICE_CHANNEL_ID);
        assertThat(interactionPublisher.connect.requestId()).isNotBlank();
        assertThat(requests.consume(interactionPublisher.connect.requestId())).containsSame(handler);
    }

    private static final class RecordingInteractionPublisher implements MusicPlayerInteractionPublisher {
        private PlayerInteraction.Connect connect;

        @Override
        public void connect(PlayerInteraction.Connect interaction) {
            this.connect = interaction;
        }
    }
}
