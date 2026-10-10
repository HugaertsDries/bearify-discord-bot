package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.ReplyBuilder;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class JdaReplyBuilder implements ReplyBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(JdaReplyBuilder.class);

    private final IReplyCallback event;
    private final String message;
    private boolean ephemeral = false;

    JdaReplyBuilder(IReplyCallback event, String message) {
        this.event = event;
        this.message = message;
    }

    @Override
    public ReplyBuilder ephemeral() {
        this.ephemeral = true;
        return this;
    }

    @Override
    public void send() {
        // A deferred interaction can no longer be replied to, only followed up through its hook
        if (event.isAcknowledged()) {
            event.getHook().sendMessage(message).setEphemeral(ephemeral)
                    .queue(null, failure -> LOG.warn("Failed to send follow-up reply: {}", failure.getMessage(), failure));
        } else {
            event.reply(message).setEphemeral(ephemeral)
                    .queue(null, failure -> LOG.warn("Failed to send interaction reply: {}", failure.getMessage(), failure));
        }
    }
}
