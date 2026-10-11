package com.bearify.music.player.agent.domain;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.Guild;
import com.bearify.discord.api.voice.VoiceSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceChannelStatusTest {

    private static final String GUILD_ID = "guild-1";

    private final VoiceSession session = mock(VoiceSession.class);
    private final Guild guild = mock(Guild.class);
    private final DiscordClient client = mock(DiscordClient.class);
    private final VoiceChannelStatus status = new VoiceChannelStatus(client, GUILD_ID);

    @BeforeEach
    void connect() {
        when(client.guild(GUILD_ID)).thenReturn(guild);
        when(guild.voice()).thenReturn(Optional.of(session));
        when(session.setStatus(anyString())).thenReturn(true);
    }

    // happy path

    @Test
    void showsTitleAndAuthorWhenTrackStarts() {
        status.show(new AudioPlayerTest.InMemoryTrack("Never Gonna Give You Up", 213_000, "@alice"));

        verify(session).setStatus("🎶 Never Gonna Give You Up — Author");
    }

    @Test
    void clearsStatusWhenAsked() {
        status.clear();

        verify(session).setStatus("");
    }

    // edge cases

    @Test
    void stopsTryingAfterStatusIsRejected() {
        when(session.setStatus(anyString())).thenReturn(false);

        status.show(new AudioPlayerTest.InMemoryTrack("Song A", 120_000, "@alice"));
        status.show(new AudioPlayerTest.InMemoryTrack("Song B", 120_000, "@alice"));
        status.clear();

        verify(session, times(1)).setStatus(anyString());
    }

    @Test
    void ignoresMissingVoiceSession() {
        when(guild.voice()).thenReturn(Optional.empty());

        assertThatCode(status::clear).doesNotThrowAnyException();
        verify(session, never()).setStatus(anyString());
    }

    @Test
    void swallowsExceptionsFromVoiceSession() {
        when(session.setStatus(anyString())).thenThrow(new IllegalStateException("rate limited"));

        assertThatCode(status::clear).doesNotThrowAnyException();
    }
}
