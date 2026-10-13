package com.bearify.discord.api.interaction;

/**
 * An interaction that is answered with a reply, straight away or after deferring it.
 */
public interface RepliableInteraction extends Interaction {

    default EditableMessage defer() {
        return defer(false);
    }

    EditableMessage defer(boolean ephemeral);

    ReplyBuilder reply(String message);
}
