package com.bearify.discord.api.gateway;

import com.bearify.discord.api.voice.VoiceSession;

/**
 * Guild-scoped Discord capabilities.
 */
public interface GuildClient {

    /** Access the bot's voice session for this guild. */
    VoiceSession voice();
}
