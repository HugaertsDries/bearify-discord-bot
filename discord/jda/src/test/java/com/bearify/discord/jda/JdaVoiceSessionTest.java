package com.bearify.discord.jda;

import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdaVoiceSessionTest {

    private static final String STATUS = "🎶 Never Gonna Give You Up — Rick Astley";

    @Test
    void hasListenersWhenMutedHumanIsNotDeafened() {
        Member human = member(false, voiceState(false, true));

        assertThat(session(human).hasListeners()).isTrue();
    }

    @Test
    void hasNoListenersWhenEveryHumanIsDeafened() {
        Member deafened = member(false, voiceState(true, false));

        assertThat(session(deafened, member(true, voiceState(false, false))).hasListeners()).isFalse();
    }

    @Test
    void hasNoListenersWhenOnlyBotsArePresent() {
        assertThat(session(member(true, voiceState(false, false))).hasListeners()).isFalse();
    }

    @Test
    void treatsHumanWithoutCachedVoiceStateAsListening() {
        assertThat(session(member(false, null)).hasListeners()).isTrue();
    }

    @Test
    void setsStatusOfVoiceChannel() {
        VoiceChannel channel = voiceChannel();

        assertThat(new JdaVoiceSession(channel, mock(AudioManager.class)).setStatus(STATUS)).isTrue();
        verify(channel).modifyStatus(STATUS);
    }

    @Test
    void truncatesStatusToDiscordLimit() {
        VoiceChannel channel = voiceChannel();

        new JdaVoiceSession(channel, mock(AudioManager.class)).setStatus("a".repeat(600));

        verify(channel).modifyStatus("a".repeat(500));
    }

    @Test
    void refusesStatusInStageChannel() {
        assertThat(new JdaVoiceSession(mock(StageChannel.class), mock(AudioManager.class)).setStatus(STATUS)).isFalse();
    }

    @Test
    void refusesStatusWithoutPermission() {
        VoiceChannel channel = mock(VoiceChannel.class);
        when(channel.modifyStatus(STATUS)).thenThrow(new PermissionException("Missing permission VOICE_SET_STATUS"));

        assertThat(new JdaVoiceSession(channel, mock(AudioManager.class)).setStatus(STATUS)).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static VoiceChannel voiceChannel() {
        VoiceChannel channel = mock(VoiceChannel.class);
        when(channel.modifyStatus(anyString())).thenReturn(mock(AuditableRestAction.class));
        return channel;
    }

    private static JdaVoiceSession session(Member... members) {
        AudioChannel channel = mock(AudioChannel.class);
        when(channel.getMembers()).thenReturn(List.of(members));
        return new JdaVoiceSession(channel, mock(AudioManager.class));
    }

    private static Member member(boolean bot, GuildVoiceState voiceState) {
        User user = mock(User.class);
        when(user.isBot()).thenReturn(bot);
        Member member = mock(Member.class);
        when(member.getUser()).thenReturn(user);
        when(member.getVoiceState()).thenReturn(voiceState);
        return member;
    }

    private static GuildVoiceState voiceState(boolean deafened, boolean muted) {
        GuildVoiceState state = mock(GuildVoiceState.class);
        when(state.isDeafened()).thenReturn(deafened);
        when(state.isMuted()).thenReturn(muted);
        return state;
    }
}
