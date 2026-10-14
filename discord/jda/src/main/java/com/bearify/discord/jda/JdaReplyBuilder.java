package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.ReplyBuilder;
import com.bearify.discord.api.message.ComponentMessage;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

class JdaReplyBuilder implements ReplyBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(JdaReplyBuilder.class);

    private final IReplyCallback event;
    private final Content content;
    private final Optional<JdaEditableMessage> deferredReply;
    private final Optional<Duration> deleteAfter;
    private boolean ephemeral = false;
    private boolean kept = false;

    /**
     * @param deferredReply present when the command deferred: its "thinking…" message is then replaced
     *                      instead of left hanging next to a follow-up
     * @param deleteAfter   how long after sending an ephemeral reply deletes itself; empty keeps them
     */
    JdaReplyBuilder(IReplyCallback event, String message, Optional<JdaEditableMessage> deferredReply, Optional<Duration> deleteAfter) {
        this(event, new Text(message), deferredReply, deleteAfter);
    }

    JdaReplyBuilder(IReplyCallback event, ComponentMessage message, Optional<JdaEditableMessage> deferredReply, Optional<Duration> deleteAfter) {
        this(event, new Components(message), deferredReply, deleteAfter);
    }

    private JdaReplyBuilder(IReplyCallback event, Content content, Optional<JdaEditableMessage> deferredReply, Optional<Duration> deleteAfter) {
        this.event = event;
        this.content = content;
        this.deferredReply = deferredReply;
        this.deleteAfter = deleteAfter;
    }

    @Override
    public ReplyBuilder ephemeral() {
        this.ephemeral = true;
        return this;
    }

    @Override
    public ReplyBuilder keep() {
        this.kept = true;
        return this;
    }

    @Override
    public void send() {
        // A deferred interaction can no longer be replied to, only edited or followed up through its hook
        if (deferredReply.isPresent()) {
            // the deferred reply settled at defer time whether it is ephemeral, and so whether it deletes itself
            JdaEditableMessage deferred = deferredReply.get();
            if (kept) {
                deferred.keep();
            }
            // through the deferred message, so an answer that comes after it deleted itself is posted afresh
            try {
                switch (content) {
                    case Text text -> deferred.edit(text.message());
                    case Components components -> deferred.edit(components.message());
                }
            } catch (RuntimeException e) {
                LOG.warn("Failed to edit deferred reply: {}", e.getMessage(), e);
            }
        } else if (event.isAcknowledged()) {
            InteractionHook hook = event.getHook();
            // after a button's acknowledge() the original is the message holding the button, so only the follow-up goes
            content.followUp(hook).setEphemeral(ephemeral)
                    .queue(followUp -> deleteDelay().ifPresent(delay -> JdaEditableMessage.deleteLater(hook.deleteMessageById(followUp.getId()), delay)),
                            failure -> LOG.warn("Failed to send follow-up reply: {}", failure.getMessage(), failure));
        } else {
            content.reply(event).setEphemeral(ephemeral)
                    .queue(hook -> deleteDelay().ifPresent(delay -> JdaEditableMessage.deleteLater(hook.deleteOriginal(), delay)),
                            failure -> LOG.warn("Failed to send interaction reply: {}", failure.getMessage(), failure));
        }
    }

    private Optional<Duration> deleteDelay() {
        return deleteAfter.filter(delay -> ephemeral && !kept);
    }

    /** What the reply carries: plain text, or a Components V2 message. */
    private sealed interface Content {
        WebhookMessageCreateAction<Message> followUp(InteractionHook hook);

        ReplyCallbackAction reply(IReplyCallback event);
    }

    private record Text(String message) implements Content {
        @Override
        public WebhookMessageCreateAction<Message> followUp(InteractionHook hook) {
            return hook.sendMessage(message);
        }

        @Override
        public ReplyCallbackAction reply(IReplyCallback event) {
            return event.reply(message);
        }
    }

    /** Maps the message for each request, since a sent file upload can't be sent again. */
    private record Components(ComponentMessage message) implements Content {
        @Override
        public WebhookMessageCreateAction<Message> followUp(InteractionHook hook) {
            ComponentMessageMapper mapper = new ComponentMessageMapper();
            List<FileUpload> files = mapper.filesFor(message);
            WebhookMessageCreateAction<Message> followUp = hook.sendMessageComponents(mapper.toJdaComponents(message)).useComponentsV2();
            return files.isEmpty() ? followUp : followUp.addFiles(files);
        }

        @Override
        public ReplyCallbackAction reply(IReplyCallback event) {
            ComponentMessageMapper mapper = new ComponentMessageMapper();
            List<FileUpload> files = mapper.filesFor(message);
            ReplyCallbackAction reply = event.replyComponents(mapper.toJdaComponents(message)).useComponentsV2();
            return files.isEmpty() ? reply : reply.addFiles(files);
        }
    }
}
