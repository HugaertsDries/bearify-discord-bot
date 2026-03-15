package com.bearify.discord.jda;

import com.bearify.discord.api.gateway.GuildClient;
import com.bearify.discord.api.voice.VoiceSession;
import net.dv8tion.jda.api.JDA;

class JdaGuildClient implements GuildClient {

    private final JDA jda;
    private final String guildId;

    JdaGuildClient(JDA jda, String guildId) {
        this.jda = jda;
        this.guildId = guildId;
    }

    @Override
    public VoiceSession voice() {
        return new JdaVoiceSession(jda, guildId);
    }
}
