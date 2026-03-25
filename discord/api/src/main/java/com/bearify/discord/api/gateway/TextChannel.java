
package com.bearify.discord.api.gateway;

/**
 * Abstraction over a Discord text channel that can receive messages.
 */
public interface TextChannel {

    /** Send a plain text message to this channel. */
    void send(String message);

    /** Send an embed (optionally with file attachments) and return a handle to delete it later. */
    SentMessage send(EmbedMessage embed);
}
