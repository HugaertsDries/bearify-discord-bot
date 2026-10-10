package com.bearify.discord.jda;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdaCommandInteractionTest {

    private static final String MESSAGE = "Something went wrong.";

    @Test
    @SuppressWarnings("unchecked")
    void editsDeferredReplyWhenReplyingAfterDeferring() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        InteractionHook hook = mock(InteractionHook.class);
        ReplyCallbackAction deferral = mock(ReplyCallbackAction.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        when(event.deferReply(true)).thenReturn(deferral);
        when(deferral.complete()).thenReturn(hook);
        when(event.isAcknowledged()).thenReturn(true);
        when(event.getHook()).thenReturn(hook);
        when(hook.editOriginal(MESSAGE)).thenReturn(edit);
        JdaCommandInteraction interaction = new JdaCommandInteraction(event);

        interaction.defer(true);
        interaction.reply(MESSAGE).ephemeral().send();

        verify(edit).queue(isNull(), any());
        verify(hook, never()).sendMessage(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsFollowUpWhenReplyingAgainAfterAReply() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageCreateAction<Message> followUp = mock(WebhookMessageCreateAction.class);
        when(event.isAcknowledged()).thenReturn(true);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessage(MESSAGE)).thenReturn(followUp);
        when(followUp.setEphemeral(true)).thenReturn(followUp);
        JdaCommandInteraction interaction = new JdaCommandInteraction(event);

        interaction.reply(MESSAGE).ephemeral().send();

        verify(followUp).queue(isNull(), any());
        verify(hook, never()).editOriginal(anyString());
    }
}
