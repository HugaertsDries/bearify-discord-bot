package com.bearify.discord.jda;

import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdaVoiceSessionTest {

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
