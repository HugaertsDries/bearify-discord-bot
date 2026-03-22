
package com.bearify.discord.api.gateway;

/**
 * Abstraction over a Discord text channel that can receive messages.
 */
public interface TextChannel {

    /** Send a message to this channel. */
    void send(String message);
}
