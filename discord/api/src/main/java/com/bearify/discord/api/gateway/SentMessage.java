package com.bearify.discord.api.gateway;

/**
 * A reference to a message that was sent to a Discord channel.
 */
public interface SentMessage {

    /** Deletes this message from the channel. */
    void delete();

    /** Replaces this message's embed content in-place. */
    void update(EmbedMessage embed);
}
