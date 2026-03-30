
package com.bearify.discord.api.gateway;

import com.bearify.discord.api.message.ComponentMessage;

/**
 * Abstraction over a Discord text channel that can receive messages.
 */
public interface TextChannel {

    /** Send a plain text message to this channel. */
    void send(String message);

    /** Send a Components V2 message and return a handle to delete it later. */
    SentMessage send(ComponentMessage message);
}
