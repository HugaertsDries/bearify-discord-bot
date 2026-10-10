package com.bearify.discord.jda;

import com.bearify.discord.api.voice.AudioProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdaGuildTest {

    private static final String GUILD_ID = "guild-1";
    private static final String VOICE_CHANNEL_ID = "voice-1";

    @Test
    void removesJoinListenerWhenOpeningAudioConnectionFails() {
        JDA jda = mock(JDA.class);
        Guild guild = mock(Guild.class);
        AudioChannel channel = mock(AudioChannel.class);
        AudioManager audioManager = mock(AudioManager.class);
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
        when(guild.getChannelById(AudioChannel.class, VOICE_CHANNEL_ID)).thenReturn(channel);
        when(guild.getAudioManager()).thenReturn(audioManager);
        doThrow(new IllegalStateException("Channel is full")).when(audioManager).openAudioConnection(channel);

        assertThatThrownBy(() -> new JdaGuild(jda, GUILD_ID).join(VOICE_CHANNEL_ID, mock(AudioProvider.class), _ -> {}))
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<Object> listener = ArgumentCaptor.forClass(Object.class);
        verify(jda).addEventListener(listener.capture());
        verify(jda).removeEventListener(listener.getValue());
    }
}
