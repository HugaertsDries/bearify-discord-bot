package com.bearify.discord.api.gateway;

import com.bearify.discord.api.message.ComponentMessage;

/**
 * A reference to a message that was sent to a Discord channel.
 */
public interface SentMessage {

    /** Deletes this message from the channel. */
    void delete();

    /** Replaces this message's components content in-place. */
    void update(ComponentMessage message);
}
