package com.bearify.discord.jda;

import com.bearify.discord.api.message.ComponentMessage;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdaReplyBuilderTest {

    private static final String MESSAGE = "Something went wrong.";
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

        new JdaReplyBuilder(event, MESSAGE, false).ephemeral().send();

        verify(followUp).setEphemeral(true);
        verify(followUp).queue(isNull(), any());
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

        new JdaReplyBuilder(event, MESSAGE, true).ephemeral().send();

        verify(edit).queue(isNull(), any());
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

        new JdaReplyBuilder(event, CARD, false).ephemeral().send();

        verify(followUp).useComponentsV2();
        verify(followUp).setEphemeral(true);
        verify(followUp).queue(isNull(), any());
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

        new JdaReplyBuilder(event, CARD, true).ephemeral().send();

        verify(edit).useComponentsV2();
        verify(edit).queue(isNull(), any());
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

        new JdaReplyBuilder(event, CARD, false).ephemeral().send();

        verify(reply).useComponentsV2();
        verify(reply).setEphemeral(true);
        verify(reply).queue(isNull(), any());
        verify(event, never()).getHook();
    }

    @Test
    void repliesDirectlyWhenNotAcknowledged() {
        IReplyCallback event = mock(IReplyCallback.class);
        ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
        when(event.isAcknowledged()).thenReturn(false);
        when(event.reply(MESSAGE)).thenReturn(reply);
        when(reply.setEphemeral(false)).thenReturn(reply);

        new JdaReplyBuilder(event, MESSAGE, false).send();

        verify(reply).setEphemeral(false);
        verify(reply).queue(isNull(), any());
        verify(event, never()).getHook();
    }
}
