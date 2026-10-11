package com.bearify.discord.jda;

import com.bearify.discord.api.message.ComponentMessage;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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

        new JdaEditableMessage(hook, Instant.now()).edit(ComponentMessage.builder("card")
                .container(container -> container.text("### Up Next"))
                .build());

        verify(edit).useComponentsV2();
        verify(edit).complete();
    }
}
