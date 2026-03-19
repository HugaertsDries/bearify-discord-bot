package com.bearify.music.player.agent.port;

import com.bearify.music.player.agent.RecordingVoiceConnectionManager;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerInteractionDispatcherTest {

    private static final String PLAYER_ID = "player-1";
    private static final String REQUEST_ID = "req-1";
    private static final String VOICE_CHANNEL_ID = "voice-1";
    private static final String GUILD_ID = "guild-1";

    @Test
    void connectsVoiceManagerWhenConnectInteractionIsHandled() {
        RecordingVoiceConnectionManager voiceConnectionManager = new RecordingVoiceConnectionManager();
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(voiceConnectionManager);

        dispatcher.handle(new MusicPlayerInteraction.Connect(PLAYER_ID, REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));

        assertThat(voiceConnectionManager.getCalls()).hasSize(1);
        assertThat(voiceConnectionManager.getCalls().getFirst().requestId()).isEqualTo(REQUEST_ID);
        assertThat(voiceConnectionManager.getCalls().getFirst().voiceChannelId()).isEqualTo(VOICE_CHANNEL_ID);
        assertThat(voiceConnectionManager.getCalls().getFirst().guildId()).isEqualTo(GUILD_ID);
        assertThat(voiceConnectionManager.getDisconnectedGuilds()).isEmpty();
    }

    @Test
    void disconnectsWhenStopInteractionIsHandled() {
        RecordingVoiceConnectionManager voiceConnectionManager = new RecordingVoiceConnectionManager();
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(voiceConnectionManager);

        dispatcher.handle(new MusicPlayerInteraction.Stop(PLAYER_ID, REQUEST_ID, GUILD_ID));

        assertThat(voiceConnectionManager.getDisconnectedGuilds()).containsExactly(GUILD_ID);
    }
}
