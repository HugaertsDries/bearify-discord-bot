package com.bearify.discord.api.interaction;

/**
 * A message context-menu command: someone picked it under Apps on a message.
 */
public interface MessageContextInteraction extends RepliableInteraction {

    String getName();

    /** The raw text of the message it was used on, mentions and markdown included; empty when the message has none. */
    String getTargetMessageContent();

    String getTargetAuthorMention();
}
