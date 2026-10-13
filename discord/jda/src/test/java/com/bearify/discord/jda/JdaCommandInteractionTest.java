package com.bearify.discord.jda;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdaCommandInteractionTest {

    private static final String MESSAGE = "Something went wrong.";
    private static final Duration DELETE_AFTER = Duration.ofSeconds(30);

    // --- HAPPY PATH ---

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
        JdaCommandInteraction interaction = new JdaCommandInteraction(event, Optional.empty());

        interaction.defer(true);
        interaction.reply(MESSAGE).ephemeral().send();

        verify(edit).queue(any(), any());
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
        JdaCommandInteraction interaction = new JdaCommandInteraction(event, Optional.empty());

        interaction.reply(MESSAGE).ephemeral().send();

        verify(followUp).queue(any(), any());
        verify(hook, never()).editOriginal(anyString());
    }

    // --- CLEANUP ---

    @Test
    @SuppressWarnings("unchecked")
    void deletesEphemeralDeferredReplyAfterTheDelay() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        InteractionHook hook = deferredReply(event, true);
        RestAction<Void> delete = mock(RestAction.class);
        when(hook.deleteOriginal()).thenReturn(delete);
        doReturn(mock(ScheduledFuture.class)).when(delete).queueAfter(anyLong(), any(TimeUnit.class), isNull(), any(Consumer.class));

        new JdaCommandInteraction(event, Optional.of(DELETE_AFTER)).defer(true).edit(MESSAGE);

        verify(delete).queueAfter(anyLong(), any(TimeUnit.class), isNull(), any(Consumer.class));
    }

    @Test
    void keepsPublicDeferredReply() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        InteractionHook hook = deferredReply(event, false);

        new JdaCommandInteraction(event, Optional.of(DELETE_AFTER)).defer().edit(MESSAGE);

        verify(hook, never()).deleteOriginal();
    }

    @SuppressWarnings("unchecked")
    private static InteractionHook deferredReply(SlashCommandInteractionEvent event, boolean ephemeral) {
        InteractionHook hook = mock(InteractionHook.class);
        ReplyCallbackAction deferral = mock(ReplyCallbackAction.class);
        when(event.deferReply(ephemeral)).thenReturn(deferral);
        when(deferral.complete()).thenReturn(hook);
        when(hook.editOriginal(MESSAGE)).thenReturn(mock(WebhookMessageEditAction.class));
        return hook;
    }
}
