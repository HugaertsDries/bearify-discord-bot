package com.bearify.discord.api.interaction;

public interface ReplyBuilder {

    ReplyBuilder ephemeral();

    /** Leaves an ephemeral reply until the user dismisses it, instead of letting it clean itself up. */
    ReplyBuilder keep();

    void send();
}
