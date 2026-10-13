package com.bearify.discord.jda;

import com.bearify.discord.api.message.ComponentMessage;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageDeleteAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.FileUpload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdaReplyBuilderTest {

    private static final String MESSAGE = "Something went wrong.";
    private static final String FOLLOW_UP_ID = "1234";
    private static final Duration DELETE_AFTER = Duration.ofSeconds(30);
    private static final ComponentMessage CARD = ComponentMessage.builder("card")
            .container(container -> container.text("### Up Next"))
            .build();

    // --- HAPPY PATH ---

    @Test
    @SuppressWarnings("unchecked")
    void sendsFollowUpWhenInteractionWasAcknowledgedWithoutDeferring() {
        IReplyCallback event = mock(IReplyCallback.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageCreateAction<Message> followUp = mock(WebhookMessageCreateAction.class);
        when(event.isAcknowledged()).thenReturn(true);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessage(MESSAGE)).thenReturn(followUp);
        when(followUp.setEphemeral(true)).thenReturn(followUp);

        new JdaReplyBuilder(event, MESSAGE, Optional.empty(), Optional.empty()).ephemeral().send();

        verify(followUp).setEphemeral(true);
        verify(followUp).queue(any(), any());
        verify(event, never()).reply(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void editsOriginalMessageWhenReplyingToDeferredCommand() {
        IReplyCallback event = mock(IReplyCallback.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        when(event.isAcknowledged()).thenReturn(true);
        when(event.getHook()).thenReturn(hook);
        when(hook.editOriginal(MESSAGE)).thenReturn(edit);

        new JdaReplyBuilder(event, MESSAGE, deferred(hook, Optional.empty()), Optional.empty()).ephemeral().send();

        verify(edit).queue(any(), any());
        verify(hook, never()).sendMessage(anyString());
        verify(event, never()).reply(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsComponentsV2FollowUpWhenInteractionWasAcknowledgedWithoutDeferring() {
        IReplyCallback event = mock(IReplyCallback.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageCreateAction<Message> followUp = mock(WebhookMessageCreateAction.class);
        when(event.isAcknowledged()).thenReturn(true);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessageComponents(anyCollection())).thenReturn(followUp);
        when(followUp.useComponentsV2()).thenReturn(followUp);
        when(followUp.setEphemeral(true)).thenReturn(followUp);

        new JdaReplyBuilder(event, CARD, Optional.empty(), Optional.empty()).ephemeral().send();

        verify(followUp).useComponentsV2();
        verify(followUp, never()).addFiles(anyCollection());
        verify(followUp).setEphemeral(true);
        verify(followUp).queue(any(), any());
        verify(hook, never()).sendMessage(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void editsOriginalIntoComponentsV2WhenReplyingToDeferredCommand() {
        IReplyCallback event = mock(IReplyCallback.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        when(event.isAcknowledged()).thenReturn(true);
        when(event.getHook()).thenReturn(hook);
        when(hook.editOriginalComponents(anyCollection())).thenReturn(edit);
        when(edit.useComponentsV2()).thenReturn(edit);
        when(edit.setFiles(anyCollection())).thenReturn(edit);

        new JdaReplyBuilder(event, CARD, deferred(hook, Optional.empty()), Optional.empty()).ephemeral().send();

        verify(edit).useComponentsV2();
        verify(edit).queue(any(), any());
        verify(hook, never()).sendMessageComponents(anyCollection());
    }

    @Test
    void repliesWithComponentsV2WhenNotAcknowledged() {
        IReplyCallback event = mock(IReplyCallback.class);
        ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
        when(event.isAcknowledged()).thenReturn(false);
        when(event.replyComponents(anyCollection())).thenReturn(reply);
        when(reply.useComponentsV2()).thenReturn(reply);
        when(reply.setEphemeral(true)).thenReturn(reply);

        new JdaReplyBuilder(event, CARD, Optional.empty(), Optional.empty()).ephemeral().send();

        verify(reply).useComponentsV2();
        verify(reply).setEphemeral(true);
        verify(reply).queue(any(), any());
        verify(event, never()).getHook();
    }

    @Test
    void repliesDirectlyWhenNotAcknowledged() {
        IReplyCallback event = mock(IReplyCallback.class);
        ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
        when(event.isAcknowledged()).thenReturn(false);
        when(event.reply(MESSAGE)).thenReturn(reply);
        when(reply.setEphemeral(false)).thenReturn(reply);

        new JdaReplyBuilder(event, MESSAGE, Optional.empty(), Optional.empty()).send();

        verify(reply).setEphemeral(false);
        verify(reply).queue(any(), any());
        verify(event, never()).getHook();
    }

    // --- CLEANUP ---

    @Test
    @SuppressWarnings("unchecked")
    void deletesEphemeralReplyAfterTheDelay() {
        IReplyCallback event = mock(IReplyCallback.class);
        ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);
        RestAction<Void> delete = mock(RestAction.class);
        when(event.reply(MESSAGE)).thenReturn(reply);
        when(reply.setEphemeral(true)).thenReturn(reply);
        when(hook.deleteOriginal()).thenReturn(delete);

        new JdaReplyBuilder(event, MESSAGE, Optional.empty(), Optional.of(DELETE_AFTER)).ephemeral().send();
        sent(reply).accept(hook);

        verify(delete).queueAfter(eq(DELETE_AFTER.toMillis()), eq(TimeUnit.MILLISECONDS), isNull(), any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deletesOnlyTheFollowUpAfterAButtonWasAcknowledged() {
        IReplyCallback event = mock(IReplyCallback.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageCreateAction<Message> followUp = mock(WebhookMessageCreateAction.class);
        Message sentFollowUp = mock(Message.class);
        WebhookMessageDeleteAction delete = mock(WebhookMessageDeleteAction.class);
        when(event.isAcknowledged()).thenReturn(true);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessage(MESSAGE)).thenReturn(followUp);
        when(followUp.setEphemeral(true)).thenReturn(followUp);
        when(sentFollowUp.getId()).thenReturn(FOLLOW_UP_ID);
        when(hook.deleteMessageById(FOLLOW_UP_ID)).thenReturn(delete);

        new JdaReplyBuilder(event, MESSAGE, Optional.empty(), Optional.of(DELETE_AFTER)).ephemeral().send();
        sent(followUp).accept(sentFollowUp);

        verify(delete).queueAfter(eq(DELETE_AFTER.toMillis()), eq(TimeUnit.MILLISECONDS), isNull(), any(Consumer.class));
        verify(hook, never()).deleteOriginal();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deletesEditedEphemeralDeferredReplyAfterTheDelay() {
        IReplyCallback event = mock(IReplyCallback.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        RestAction<Void> delete = mock(RestAction.class);
        when(event.getHook()).thenReturn(hook);
        when(hook.editOriginal(MESSAGE)).thenReturn(edit);
        when(hook.deleteOriginal()).thenReturn(delete);
        doReturn(mock(ScheduledFuture.class)).when(delete).queueAfter(anyLong(), any(TimeUnit.class), isNull(), any(Consumer.class));

        new JdaReplyBuilder(event, MESSAGE, deferred(hook, Optional.of(DELETE_AFTER)), Optional.of(DELETE_AFTER)).ephemeral().send();
        sent(edit).accept(mock(Message.class));

        verify(delete).queueAfter(eq(DELETE_AFTER.toMillis()), eq(TimeUnit.MILLISECONDS), isNull(), any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsEphemeralReplyThatAskedToBeKept() {
        IReplyCallback event = mock(IReplyCallback.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageCreateAction<Message> followUp = mock(WebhookMessageCreateAction.class);
        when(event.isAcknowledged()).thenReturn(true);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessage(MESSAGE)).thenReturn(followUp);
        when(followUp.setEphemeral(true)).thenReturn(followUp);

        new JdaReplyBuilder(event, MESSAGE, Optional.empty(), Optional.of(DELETE_AFTER)).ephemeral().keep().send();
        sent(followUp).accept(mock(Message.class));

        verify(hook, never()).deleteMessageById(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsEditedDeferredReplyThatAskedToBeKept() {
        IReplyCallback event = mock(IReplyCallback.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        when(event.getHook()).thenReturn(hook);
        when(hook.editOriginal(MESSAGE)).thenReturn(edit);

        new JdaReplyBuilder(event, MESSAGE, deferred(hook, Optional.of(DELETE_AFTER)), Optional.of(DELETE_AFTER)).keep().send();
        sent(edit).accept(mock(Message.class));

        verify(hook, never()).deleteOriginal();
    }

    @Test
    void keepsPublicReply() {
        IReplyCallback event = mock(IReplyCallback.class);
        ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);
        when(event.reply(MESSAGE)).thenReturn(reply);
        when(reply.setEphemeral(false)).thenReturn(reply);

        new JdaReplyBuilder(event, MESSAGE, Optional.empty(), Optional.of(DELETE_AFTER)).send();
        sent(reply).accept(hook);

        verify(hook, never()).deleteOriginal();
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsEditedPublicDeferredReply() {
        IReplyCallback event = mock(IReplyCallback.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        when(event.getHook()).thenReturn(hook);
        when(hook.editOriginal(MESSAGE)).thenReturn(edit);

        // a public defer carries no delay, whatever the reply builder is told
        new JdaReplyBuilder(event, MESSAGE, deferred(hook, Optional.empty()), Optional.of(DELETE_AFTER)).ephemeral().send();
        sent(edit).accept(mock(Message.class));

        verify(hook, never()).deleteOriginal();
    }

    @Test
    void keepsEphemeralReplyWhenNoDelayIsConfigured() {
        IReplyCallback event = mock(IReplyCallback.class);
        ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);
        when(event.reply(MESSAGE)).thenReturn(reply);
        when(reply.setEphemeral(true)).thenReturn(reply);

        new JdaReplyBuilder(event, MESSAGE, Optional.empty(), Optional.empty()).ephemeral().send();
        sent(reply).accept(hook);

        verify(hook, never()).deleteOriginal();
    }

    // --- SPACER ---

    private static final ComponentMessage SPACER_CARD = ComponentMessage.builder("card")
            .container(container -> container.text("### Up Next").spacer())
            .build();

    @Test
    @SuppressWarnings("unchecked")
    void attachesSpacerImageToFollowUp() {
        IReplyCallback event = mock(IReplyCallback.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageCreateAction<Message> followUp = mock(WebhookMessageCreateAction.class);
        when(event.isAcknowledged()).thenReturn(true);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessageComponents(anyCollection())).thenReturn(followUp);
        when(followUp.useComponentsV2()).thenReturn(followUp);
        when(followUp.addFiles(anyCollection())).thenReturn(followUp);
        when(followUp.setEphemeral(true)).thenReturn(followUp);

        new JdaReplyBuilder(event, SPACER_CARD, Optional.empty(), Optional.empty()).ephemeral().send();

        ArgumentCaptor<Collection<FileUpload>> files = ArgumentCaptor.forClass(Collection.class);
        verify(followUp).addFiles(files.capture());
        assertThat(files.getValue()).extracting(FileUpload::getName).containsExactly("spacer.png");
    }

    @Test
    @SuppressWarnings("unchecked")
    void attachesSpacerImageWhenEditingDeferredReply() {
        IReplyCallback event = mock(IReplyCallback.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        when(event.isAcknowledged()).thenReturn(true);
        when(event.getHook()).thenReturn(hook);
        when(hook.editOriginalComponents(anyCollection())).thenReturn(edit);
        when(edit.useComponentsV2()).thenReturn(edit);
        when(edit.setFiles(anyCollection())).thenReturn(edit);

        new JdaReplyBuilder(event, SPACER_CARD, deferred(hook, Optional.empty()), Optional.empty()).send();

        ArgumentCaptor<Collection<FileUpload>> files = ArgumentCaptor.forClass(Collection.class);
        verify(edit).setFiles(files.capture());
        assertThat(files.getValue()).extracting(FileUpload::getName).containsExactly("spacer.png");
    }

    @Test
    @SuppressWarnings("unchecked")
    void attachesSpacerImageToDirectReply() {
        IReplyCallback event = mock(IReplyCallback.class);
        ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
        when(event.isAcknowledged()).thenReturn(false);
        when(event.replyComponents(anyCollection())).thenReturn(reply);
        when(reply.useComponentsV2()).thenReturn(reply);
        when(reply.addFiles(anyCollection())).thenReturn(reply);
        when(reply.setEphemeral(true)).thenReturn(reply);

        new JdaReplyBuilder(event, SPACER_CARD, Optional.empty(), Optional.empty()).ephemeral().send();

        ArgumentCaptor<Collection<FileUpload>> files = ArgumentCaptor.forClass(Collection.class);
        verify(reply).addFiles(files.capture());
        assertThat(files.getValue()).extracting(FileUpload::getName).containsExactly("spacer.png");
    }

    private static Optional<JdaEditableMessage> deferred(InteractionHook hook, Optional<Duration> deleteAfter) {
        return Optional.of(new JdaEditableMessage(hook, Instant.now(), deleteAfter));
    }

    /** What the reply does once Discord confirms it was sent. */
    @SuppressWarnings("unchecked")
    private static <T> Consumer<T> sent(RestAction<T> action) {
        ArgumentCaptor<Consumer<T>> onSent = ArgumentCaptor.forClass(Consumer.class);
        verify(action).queue(onSent.capture(), any());
        return onSent.getValue();
    }
}
