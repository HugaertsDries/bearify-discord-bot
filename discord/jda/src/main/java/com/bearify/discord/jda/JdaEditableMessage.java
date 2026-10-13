package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.EditableMessage;
import com.bearify.discord.api.message.ComponentMessage;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

class JdaEditableMessage implements EditableMessage {

    private static final Logger LOG = LoggerFactory.getLogger(JdaEditableMessage.class);

    private final InteractionHook hook;
    private final Instant createdAt;
    private final Optional<Duration> deleteAfter;
    private boolean kept = false;
    private Optional<ScheduledFuture<?>> pendingDelete = Optional.empty();

    /**
     * @param deleteAfter how long after the last write this reply deletes itself; empty for a public reply,
     *                    or when ephemeral replies are kept
     */
    JdaEditableMessage(InteractionHook hook, Instant createdAt, Optional<Duration> deleteAfter) {
        this.hook = hook;
        this.createdAt = createdAt;
        this.deleteAfter = deleteAfter;
    }

    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public synchronized EditableMessage keep() {
        kept = true;
        pendingDelete.ifPresent(delete -> delete.cancel(false));
        pendingDelete = Optional.empty();
        return this;
    }

    @Override
    public void edit(String message) {
        complete(hook.editOriginal(message), () -> hook.sendMessage(message));
    }

    @Override
    public void edit(ComponentMessage message) {
        ComponentMessageMapper mapper = new ComponentMessageMapper();
        complete(hook.editOriginalComponents(mapper.toJdaComponents(message)).useComponentsV2().setFiles(mapper.filesFor(message)),
                () -> hook.sendMessageComponents(mapper.toJdaComponents(message)).useComponentsV2().addFiles(mapper.filesFor(message)));
    }

    private void complete(RestAction<?> edit, Supplier<WebhookMessageCreateAction<Message>> repost) {
        try {
            edit.complete();
        } catch (ErrorResponseException e) {
            if (!isGone(e)) {
                throw e;
            }
            // the reply deleted itself before this later answer arrived (a slow join, several links), so post it afresh
            Message followUp = repost.get().setEphemeral(true).complete();
            deleteAfter.ifPresent(delay -> deleteLater(hook.deleteMessageById(followUp.getId()), delay));
            return;
        }
        deleteLater();
    }

    /** (Re)starts the countdown, so the reply deletes itself {@code deleteAfter} after the last write. */
    synchronized void deleteLater() {
        if (kept) {
            return;
        }
        deleteAfter.ifPresent(delay -> {
            pendingDelete.ifPresent(delete -> delete.cancel(false));
            pendingDelete = Optional.of(deleteLater(hook.deleteOriginal(), delay));
        });
    }

    static ScheduledFuture<?> deleteLater(RestAction<Void> delete, Duration delay) {
        return delete.queueAfter(delay.toMillis(), TimeUnit.MILLISECONDS, null, failure -> {
            if (isGone(failure)) {
                LOG.debug("Ephemeral reply was already gone: {}", failure.getMessage());
            } else {
                LOG.warn("Failed to delete ephemeral reply: {}", failure.getMessage(), failure);
            }
        });
    }

    private static boolean isGone(Throwable failure) {
        return failure instanceof ErrorResponseException e && e.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE;
    }
}
