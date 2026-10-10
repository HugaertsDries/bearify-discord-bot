package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.ReplyBuilder;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class JdaReplyBuilder implements ReplyBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(JdaReplyBuilder.class);

    private final IReplyCallback event;
    private final String message;
    private final boolean editsDeferredReply;
    private boolean ephemeral = false;

    /**
     * @param editsDeferredReply true when the command deferred: its "thinking…" message is then replaced
     *                           instead of left hanging next to a follow-up
     */
    JdaReplyBuilder(IReplyCallback event, String message, boolean editsDeferredReply) {
        this.event = event;
        this.message = message;
        this.editsDeferredReply = editsDeferredReply;
    }

    @Override
    public ReplyBuilder ephemeral() {
        this.ephemeral = true;
        return this;
    }

    @Override
    public void send() {
        // A deferred interaction can no longer be replied to, only edited or followed up through its hook
        if (editsDeferredReply) {
            event.getHook().editOriginal(message)
                    .queue(null, failure -> LOG.warn("Failed to edit deferred reply: {}", failure.getMessage(), failure));
        } else if (event.isAcknowledged()) {
            event.getHook().sendMessage(message).setEphemeral(ephemeral)
                    .queue(null, failure -> LOG.warn("Failed to send follow-up reply: {}", failure.getMessage(), failure));
        } else {
            event.reply(message).setEphemeral(ephemeral)
                    .queue(null, failure -> LOG.warn("Failed to send interaction reply: {}", failure.getMessage(), failure));
        }
    }
}
