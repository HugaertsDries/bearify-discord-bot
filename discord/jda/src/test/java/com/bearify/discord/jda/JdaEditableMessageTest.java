package com.bearify.discord.jda;

import com.bearify.discord.api.message.ComponentMessage;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageDeleteAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.utils.FileUpload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdaEditableMessageTest {

    private static final String MESSAGE = "Sending a bear your way!";
    private static final String NEXT_MESSAGE = "Queued **Africa**.";
    private static final Duration DELETE_AFTER = Duration.ofSeconds(30);

    // --- HAPPY PATH ---

    @Test
    @SuppressWarnings("unchecked")
    void editsDeferredReplyIntoComponentsV2Message() {
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        when(hook.editOriginalComponents(anyCollection())).thenReturn(edit);
        when(edit.useComponentsV2()).thenReturn(edit);
        when(edit.setFiles(anyCollection())).thenReturn(edit);

        new JdaEditableMessage(hook, Instant.now(), Optional.empty()).edit(ComponentMessage.builder("card")
                .container(container -> container.text("### Up Next"))
                .build());

        verify(edit).useComponentsV2();
        verify(edit).setFiles(List.<FileUpload>of());
        verify(edit).complete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void editsDeferredReplyWithSpacerImageWhenCardHasSpacer() {
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        when(hook.editOriginalComponents(anyCollection())).thenReturn(edit);
        when(edit.useComponentsV2()).thenReturn(edit);
        when(edit.setFiles(anyCollection())).thenReturn(edit);

        new JdaEditableMessage(hook, Instant.now(), Optional.empty()).edit(ComponentMessage.builder("card")
                .container(container -> container.text("### Up Next").spacer())
                .build());

        ArgumentCaptor<Collection<FileUpload>> files = ArgumentCaptor.forClass(Collection.class);
        verify(edit).setFiles(files.capture());
        assertThat(files.getValue()).extracting(FileUpload::getName).containsExactly("spacer.png");
    }

    // --- CLEANUP ---

    @Test
    @SuppressWarnings("unchecked")
    void deletesReplyAfterTheDelay() {
        InteractionHook hook = mock(InteractionHook.class);
        RestAction<Void> delete = deletableReply(hook);

        new JdaEditableMessage(hook, Instant.now(), Optional.of(DELETE_AFTER)).edit(MESSAGE);

        verify(delete).queueAfter(eq(DELETE_AFTER.toMillis()), eq(TimeUnit.MILLISECONDS), isNull(), any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void restartsTheCountdownOnEveryEdit() {
        InteractionHook hook = mock(InteractionHook.class);
        RestAction<Void> delete = deletableReply(hook);
        ScheduledFuture<?> firstCountdown = mock(ScheduledFuture.class);
        ScheduledFuture<?> secondCountdown = mock(ScheduledFuture.class);
        doReturn(firstCountdown, secondCountdown).when(delete).queueAfter(anyLong(), any(TimeUnit.class), isNull(), any(Consumer.class));
        JdaEditableMessage message = new JdaEditableMessage(hook, Instant.now(), Optional.of(DELETE_AFTER));

        message.edit(MESSAGE);
        message.edit(NEXT_MESSAGE);

        verify(firstCountdown).cancel(false);
        verify(secondCountdown, never()).cancel(anyBoolean());
        verify(delete, times(2)).queueAfter(eq(DELETE_AFTER.toMillis()), eq(TimeUnit.MILLISECONDS), isNull(), any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsReplyThatAskedToBeKept() {
        InteractionHook hook = mock(InteractionHook.class);
        deletableReply(hook);

        new JdaEditableMessage(hook, Instant.now(), Optional.of(DELETE_AFTER)).keep().edit(MESSAGE);

        verify(hook, never()).deleteOriginal();
    }

    @Test
    @SuppressWarnings("unchecked")
    void stopsAPendingCountdownWhenKept() {
        InteractionHook hook = mock(InteractionHook.class);
        RestAction<Void> delete = deletableReply(hook);
        ScheduledFuture<?> countdown = mock(ScheduledFuture.class);
        doReturn(countdown).when(delete).queueAfter(anyLong(), any(TimeUnit.class), isNull(), any(Consumer.class));
        JdaEditableMessage message = new JdaEditableMessage(hook, Instant.now(), Optional.of(DELETE_AFTER));

        message.edit(MESSAGE);
        message.keep();

        verify(countdown).cancel(false);
    }

    @Test
    void keepsReplyWithoutADelay() {
        InteractionHook hook = mock(InteractionHook.class);
        deletableReply(hook);

        new JdaEditableMessage(hook, Instant.now(), Optional.empty()).edit(MESSAGE);

        verify(hook, never()).deleteOriginal();
    }

    // --- EDGE CASES ---

    @Test
    @SuppressWarnings("unchecked")
    void repostsAnAnswerWhoseReplyAlreadyDeletedItself() {
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        ErrorResponseException gone = mock(ErrorResponseException.class);
        when(gone.getErrorResponse()).thenReturn(ErrorResponse.UNKNOWN_MESSAGE);
        when(hook.editOriginal(anyString())).thenReturn(edit);
        when(edit.complete()).thenThrow(gone);
        WebhookMessageCreateAction<Message> repost = mock(WebhookMessageCreateAction.class);
        Message followUp = mock(Message.class);
        when(followUp.getId()).thenReturn("follow-up-1");
        when(hook.sendMessage(MESSAGE)).thenReturn(repost);
        when(repost.setEphemeral(true)).thenReturn(repost);
        when(repost.complete()).thenReturn(followUp);
        WebhookMessageDeleteAction deleteFollowUp = mock(WebhookMessageDeleteAction.class);
        when(hook.deleteMessageById("follow-up-1")).thenReturn(deleteFollowUp);

        assertThatCode(() -> new JdaEditableMessage(hook, Instant.now(), Optional.of(DELETE_AFTER)).edit(MESSAGE))
                .doesNotThrowAnyException();

        verify(repost).setEphemeral(true);
        verify(deleteFollowUp).queueAfter(eq(DELETE_AFTER.toMillis()), eq(TimeUnit.MILLISECONDS), isNull(), any(Consumer.class));
        verify(hook, never()).deleteOriginal();
    }

    @SuppressWarnings("unchecked")
    private static RestAction<Void> deletableReply(InteractionHook hook) {
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        RestAction<Void> delete = mock(RestAction.class);
        when(hook.editOriginal(anyString())).thenReturn(edit);
        when(hook.deleteOriginal()).thenReturn(delete);
        doReturn(mock(ScheduledFuture.class)).when(delete).queueAfter(anyLong(), any(TimeUnit.class), isNull(), any(Consumer.class));
        return delete;
    }
}
