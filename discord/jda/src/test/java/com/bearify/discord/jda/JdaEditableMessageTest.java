package com.bearify.discord.jda;

import com.bearify.discord.api.message.ComponentMessage;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.utils.FileUpload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdaEditableMessageTest {

    @Test
    @SuppressWarnings("unchecked")
    void editsDeferredReplyIntoComponentsV2Message() {
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        when(hook.editOriginalComponents(anyCollection())).thenReturn(edit);
        when(edit.useComponentsV2()).thenReturn(edit);
        when(edit.setFiles(anyCollection())).thenReturn(edit);

        new JdaEditableMessage(hook, Instant.now()).edit(ComponentMessage.builder("card")
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

        new JdaEditableMessage(hook, Instant.now()).edit(ComponentMessage.builder("card")
                .container(container -> container.text("### Up Next").spacer())
                .build());

        ArgumentCaptor<Collection<FileUpload>> files = ArgumentCaptor.forClass(Collection.class);
        verify(edit).setFiles(files.capture());
        assertThat(files.getValue()).extracting(FileUpload::getName).containsExactly("spacer.png");
    }
}
