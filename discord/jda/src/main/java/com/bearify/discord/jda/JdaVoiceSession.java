package com.bearify.discord.jda;

import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.discord.api.voice.VoiceSessionListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

class JdaVoiceSession implements VoiceSession {

    private final JDA jda;
    private final String guildId;
    private final List<VoiceSessionListener> joinedListeners = new CopyOnWriteArrayList<>();

    JdaVoiceSession(JDA jda, String guildId) {
        this.jda = jda;
        this.guildId = guildId;
        jda.addEventListener(new ListenerAdapter() {
            @Override
            public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
                if (!guildId.equals(event.getGuild().getId())) {
                    return;
                }
                if (event.getMember().getUser().getIdLong() != jda.getSelfUser().getIdLong()) {
                    return;
                }
                if (event.getChannelJoined() == null) {
                    return;
                }
                joinedListeners.forEach(listener -> listener.onJoined(event.getChannelJoined().getId()));
            }
        });
    }

    @Override
    public void join(String channelId, VoiceSessionListener onJoined) {
        joinedListeners.add(onJoined);
        Guild guild = requireGuild();
        AudioChannel channel = guild.getChannelById(AudioChannel.class, channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Voice channel not found: " + channelId);
        }
        guild.getAudioManager().setAutoReconnect(false);
        guild.getAudioManager().setSendingHandler(new AudioSendHandler() {
            @Override
            public boolean canProvide() {
                return true;
            }

            @Override
            public ByteBuffer provide20MsAudio() {
                return ByteBuffer.wrap(new byte[3840]);
            }
        });
        guild.getAudioManager().openAudioConnection(channel);
    }

    @Override
    public void leave() {
        requireGuild().getAudioManager().closeAudioConnection();
    }

    @Override
    public Optional<String> getConnectedChannelId() {
        AudioChannel channel = requireGuild().getAudioManager().getConnectedChannel();
        return Optional.ofNullable(channel).map(AudioChannel::getId);
    }

    @Override
    public boolean isAlone() {
        AudioChannel channel = requireGuild().getAudioManager().getConnectedChannel();
        if (channel == null) {
            return true;
        }
        return channel.getMembers().stream()
                .filter(member -> !member.getUser().isBot())
                .findAny()
                .isEmpty();
    }

    @Override
    public String getGuildId() {
        return guildId;
    }

    private Guild requireGuild() {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Guild not found: " + guildId);
        }
        return guild;
    }
}
