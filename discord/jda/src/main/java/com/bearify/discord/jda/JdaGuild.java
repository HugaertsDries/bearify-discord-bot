package com.bearify.discord.jda;

import com.bearify.discord.api.gateway.Guild;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.discord.api.voice.VoiceSessionListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.nio.ByteBuffer;
import java.util.Optional;

class JdaGuild implements Guild {

    private final JDA jda;
    private final String guildId;

    JdaGuild(JDA jda, String guildId) {
        this.jda = jda;
        this.guildId = guildId;
    }

    @Override
    public Optional<VoiceSession> voice() {
        var guild = requireGuild();
        AudioChannel channel = guild.getAudioManager().getConnectedChannel();
        return Optional.ofNullable(channel)
                .map(c -> new JdaVoiceSession(c, guild.getAudioManager()));
    }

    @Override
    public void join(String channelId, VoiceSessionListener onJoined) {
        var guild = requireGuild();
        AudioChannel channel = guild.getChannelById(AudioChannel.class, channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Voice channel not found: " + channelId);
        }
        jda.addEventListener(new ListenerAdapter() {
            @Override
            public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
                if (!guildId.equals(event.getGuild().getId())) return;
                if (event.getMember().getUser().getIdLong() != jda.getSelfUser().getIdLong()) return;
                if (event.getChannelJoined() == null) return;
                jda.removeEventListener(this);
                onJoined.onJoined(event.getChannelJoined().getId());
            }
        });
        guild.getAudioManager().setAutoReconnect(false);
        guild.getAudioManager().setSelfDeafened(true);
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

    private net.dv8tion.jda.api.entities.Guild requireGuild() {
        net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Guild not found: " + guildId);
        }
        return guild;
    }
}
