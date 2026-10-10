package com.bearify.discord.jda;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdaReplyBuilderTest {

    private static final String MESSAGE = "Something went wrong.";

    // --- HAPPY PATH ---

    @Test
    @SuppressWarnings("unchecked")
    void sendsFollowUpWhenInteractionAlreadyAcknowledged() {
        IReplyCallback event = mock(IReplyCallback.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageCreateAction<Message> followUp = mock(WebhookMessageCreateAction.class);
        when(event.isAcknowledged()).thenReturn(true);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessage(MESSAGE)).thenReturn(followUp);
        when(followUp.setEphemeral(true)).thenReturn(followUp);

        new JdaReplyBuilder(event, MESSAGE).ephemeral().send();

        verify(followUp).setEphemeral(true);
        verify(followUp).queue(isNull(), any());
        verify(event, never()).reply(anyString());
    }

    @Test
    void repliesDirectlyWhenNotAcknowledged() {
        IReplyCallback event = mock(IReplyCallback.class);
        ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
        when(event.isAcknowledged()).thenReturn(false);
        when(event.reply(MESSAGE)).thenReturn(reply);
        when(reply.setEphemeral(false)).thenReturn(reply);

        new JdaReplyBuilder(event, MESSAGE).send();

        verify(reply).setEphemeral(false);
        verify(reply).queue(isNull(), any());
        verify(event, never()).getHook();
    }
}
