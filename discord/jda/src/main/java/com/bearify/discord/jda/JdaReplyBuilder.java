package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.ReplyBuilder;
import com.bearify.discord.api.message.ComponentMessage;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
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
        this(event, components(message), deferredReply, deleteAfter);
    }

    private static Components components(ComponentMessage message) {
        ComponentMessageMapper mapper = new ComponentMessageMapper();
        return new Components(mapper.toJdaComponents(message), mapper.filesFor(message));
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
            content.editOriginal(event.getHook())
                    .queue(edited -> deferred.deleteLater(),
                            failure -> LOG.warn("Failed to edit deferred reply: {}", failure.getMessage(), failure));
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
        WebhookMessageEditAction<Message> editOriginal(InteractionHook hook);

        WebhookMessageCreateAction<Message> followUp(InteractionHook hook);

        ReplyCallbackAction reply(IReplyCallback event);
    }

    private record Text(String message) implements Content {
        @Override
        public WebhookMessageEditAction<Message> editOriginal(InteractionHook hook) {
            return hook.editOriginal(message);
        }

        @Override
        public WebhookMessageCreateAction<Message> followUp(InteractionHook hook) {
            return hook.sendMessage(message);
        }

        @Override
        public ReplyCallbackAction reply(IReplyCallback event) {
            return event.reply(message);
        }
    }

    private record Components(List<MessageTopLevelComponent> components, List<FileUpload> files) implements Content {
        @Override
        public WebhookMessageEditAction<Message> editOriginal(InteractionHook hook) {
            // replaces the attachments, so the "thinking…" message ends up with exactly the files this message needs
            return hook.editOriginalComponents(components).useComponentsV2().setFiles(files);
        }

        @Override
        public WebhookMessageCreateAction<Message> followUp(InteractionHook hook) {
            WebhookMessageCreateAction<Message> followUp = hook.sendMessageComponents(components).useComponentsV2();
            return files.isEmpty() ? followUp : followUp.addFiles(files);
        }

        @Override
        public ReplyCallbackAction reply(IReplyCallback event) {
            ReplyCallbackAction reply = event.replyComponents(components).useComponentsV2();
            return files.isEmpty() ? reply : reply.addFiles(files);
        }
    }
}
