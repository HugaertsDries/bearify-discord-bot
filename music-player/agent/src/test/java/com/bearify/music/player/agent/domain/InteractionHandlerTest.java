package com.bearify.music.player.agent.domain;

import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InteractionHandlerTest {

    private static final String PLAYER_ID = "player-1";
    private static final String REQUEST_ID = "req-1";
    private static final String VOICE_CHANNEL_ID = "voice-1";
    private static final String GUILD_ID = "guild-1";

    @Test
    void connectsVoiceManagerWhenConnectCommandIsHandled() {
        RecordingVoiceConnectionManager voiceConnectionManager = new RecordingVoiceConnectionManager();
        InteractionHandler handler = new InteractionHandler(voiceConnectionManager);

        handler.handle(new MusicPlayerInteraction.Connect(PLAYER_ID, REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));

        assertThat(voiceConnectionManager.received).isNotNull();
        assertThat(voiceConnectionManager.received.requestId()).isEqualTo(REQUEST_ID);
        assertThat(voiceConnectionManager.received.voiceChannelId()).isEqualTo(VOICE_CHANNEL_ID);
        assertThat(voiceConnectionManager.received.guildId()).isEqualTo(GUILD_ID);
        assertThat(voiceConnectionManager.disconnectedGuildId).isNull();
    }

    @Test
    void disconnectsWhenStopCommandIsHandled() {
        RecordingVoiceConnectionManager voiceConnectionManager = new RecordingVoiceConnectionManager();
        InteractionHandler handler = new InteractionHandler(voiceConnectionManager);

        handler.handle(new MusicPlayerInteraction.Stop(PLAYER_ID, REQUEST_ID, GUILD_ID));

        assertThat(voiceConnectionManager.disconnectedGuildId).isEqualTo(GUILD_ID);
    }

    private static final class RecordingVoiceConnectionManager implements VoiceConnectionManager {
        private ConnectionRequest received;
        private String disconnectedGuildId;

        @Override
        public void connect(ConnectionRequest request) {
            this.received = request;
        }

        @Override
        public void disconnect(String guildId) {
            this.disconnectedGuildId = guildId;
        }
    }
}
