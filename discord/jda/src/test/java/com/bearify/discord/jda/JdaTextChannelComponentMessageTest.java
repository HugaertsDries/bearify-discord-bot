package com.bearify.discord.jda;

import com.bearify.discord.api.message.ButtonStyle;
import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.discord.api.message.InteractiveButton;
import com.bearify.discord.api.message.LinkButton;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import net.dv8tion.jda.api.utils.FileUpload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdaTextChannelComponentMessageTest {

    @Test
    void sendUsesComponentsV2AndMapsSectionsAndButtons() {
        MessageChannel channel = mock(MessageChannel.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        Message message = mock(Message.class);
        when(channel.sendMessageComponents(anyTopLevelComponents())).thenReturn(action);
        when(action.useComponentsV2()).thenReturn(action);
        when(action.complete()).thenReturn(message);

        JdaTextChannel textChannel = new JdaTextChannel(channel);

        textChannel.send(ComponentMessage.builder("broadcast")
                .container(container -> container
                        .accentColor(0xFFA500)
                        .text("\uD83D\uDD34 ON THE AIR")
                        .section(section -> section
                                .text("## Track")
                                .button(new InteractiveButton(ButtonStyle.PRIMARY, "pause", "Pause")))
                        .ActionRow(row -> row.primary("pause", "Pause")))
                .build());

        verify(action).useComponentsV2();
        verify(action).complete();
    }

    @Test
    void sendMapsImageWithoutDescription() {
        MessageChannel channel = mock(MessageChannel.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        when(channel.sendMessageComponents(anyTopLevelComponents())).thenReturn(action);
        when(action.useComponentsV2()).thenReturn(action);
        when(action.complete()).thenReturn(mock(Message.class));

        new JdaTextChannel(channel).send(ComponentMessage.builder("spacer")
                .container(container -> container.text("rows").image("https://example.com/spacer.png", ""))
                .build());

        ArgumentCaptor<Collection<MessageTopLevelComponent>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(channel).sendMessageComponents(captor.capture());
        Container container = (Container) List.copyOf(captor.getValue()).getFirst();
        MediaGallery gallery = (MediaGallery) container.getComponents().get(1);
        assertThat(gallery.getItems().getFirst().getDescription()).isNullOrEmpty();
    }

    @Test
    void sendPreservesRootLevelTextContainerImageOrdering() {
        MessageChannel channel = mock(MessageChannel.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        Message message = mock(Message.class);
        when(channel.sendMessageComponents(anyTopLevelComponents())).thenReturn(action);
        when(action.useComponentsV2()).thenReturn(action);
        when(action.complete()).thenReturn(message);

        new JdaTextChannel(channel).send(ComponentMessage.builder("preview")
                .text("intro")
                .container(container -> container.ActionRow(row -> row.primary("pause", "Pause")))
                .image("https://example.com/art.png", "Artwork")
                .text("outro")
                .build());

        ArgumentCaptor<Collection<MessageTopLevelComponent>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(channel).sendMessageComponents(captor.capture());

        List<MessageTopLevelComponent> topLevelComponents = List.copyOf(captor.getValue());
        assertThat(topLevelComponents).hasSize(4);
        assertThat(topLevelComponents.get(0)).isInstanceOf(TextDisplay.class);
        assertThat(topLevelComponents.get(1)).isInstanceOf(Container.class);
        assertThat(topLevelComponents.get(2)).isInstanceOf(MediaGallery.class);
        assertThat(topLevelComponents.get(3)).isInstanceOf(TextDisplay.class);
    }

    @Test
    void sendSupportsSectionsWithButtons() {
        MessageChannel channel = mock(MessageChannel.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        Message message = mock(Message.class);
        when(channel.sendMessageComponents(anyTopLevelComponents())).thenReturn(action);
        when(action.useComponentsV2()).thenReturn(action);
        when(action.complete()).thenReturn(message);

        new JdaTextChannel(channel).send(ComponentMessage.builder("preview")
                .container(container -> container.section(section -> section
                        .text("## Track")
                        .button(new InteractiveButton(ButtonStyle.SECONDARY, "track", "Track"))))
                .build());

        verify(action).useComponentsV2();
        verify(action).complete();
    }

    @Test
    void sendSupportsSectionAccessoriesAndActionRowButtons() {
        MessageChannel channel = mock(MessageChannel.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        Message message = mock(Message.class);
        when(channel.sendMessageComponents(anyTopLevelComponents())).thenReturn(action);
        when(action.useComponentsV2()).thenReturn(action);
        when(action.complete()).thenReturn(message);

        new JdaTextChannel(channel).send(ComponentMessage.builder("preview")
                .container(container -> container.section(section -> section
                        .text("## Track")
                        .accessory(LinkButton.of("https://example.com", "Source"))))
                .build());

        verify(action).useComponentsV2();
        verify(action).complete();
    }

    @Test
    void sendMapsSectionThumbnailAccessoryToJdaThumbnail() {
        MessageChannel channel = mock(MessageChannel.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        Message message = mock(Message.class);
        when(channel.sendMessageComponents(anyTopLevelComponents())).thenReturn(action);
        when(action.useComponentsV2()).thenReturn(action);
        when(action.complete()).thenReturn(message);

        new JdaTextChannel(channel).send(ComponentMessage.builder("preview")
                .container(container -> container.section(section -> section
                        .text("## Track")
                        .thumbnail("https://example.com/thumb.png", "Cover art")))
                .build());

        ArgumentCaptor<Collection<MessageTopLevelComponent>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(channel).sendMessageComponents(captor.capture());

        List<MessageTopLevelComponent> topLevelComponents = List.copyOf(captor.getValue());
        assertThat(topLevelComponents).singleElement().isInstanceOf(Container.class);

        Container jdaContainer = (Container) topLevelComponents.getFirst();
        assertThat(jdaContainer.getComponents()).singleElement().isInstanceOf(Section.class);

        Section jdaSection = jdaContainer.getComponents().getFirst().asSection();
        assertThat(jdaSection.getAccessory()).isInstanceOf(Thumbnail.class);

        Thumbnail thumbnail = (Thumbnail) jdaSection.getAccessory();
        assertThat(thumbnail.getUrl()).isEqualTo("https://example.com/thumb.png");
        assertThat(thumbnail.getDescription()).isEqualTo("Cover art");
    }

    @Test
    void sendSupportsTextOnlySectionsAndSkipsEmptyContainers() {
        MessageChannel channel = mock(MessageChannel.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        Message message = mock(Message.class);
        when(channel.sendMessageComponents(anyTopLevelComponents())).thenReturn(action);
        when(action.useComponentsV2()).thenReturn(action);
        when(action.complete()).thenReturn(message);

        new JdaTextChannel(channel).send(ComponentMessage.builder("preview")
                .container(container -> {
                })
                .container(container -> container.section(section -> section.text("## Track")))
                .build());

        ArgumentCaptor<Collection<MessageTopLevelComponent>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(channel).sendMessageComponents(captor.capture());

        List<MessageTopLevelComponent> topLevelComponents = List.copyOf(captor.getValue());
        assertThat(topLevelComponents).hasSize(1);
        assertThat(topLevelComponents.getFirst()).isInstanceOf(Container.class);
    }

    @Test
    void updateUsesComponentsV2EditPath() {
        MessageChannel channel = mock(MessageChannel.class);
        MessageCreateAction createAction = mock(MessageCreateAction.class);
        MessageEditAction editAction = mock(MessageEditAction.class);
        Message message = mock(Message.class);
        when(channel.sendMessageComponents(anyTopLevelComponents())).thenReturn(createAction);
        when(createAction.useComponentsV2()).thenReturn(createAction);
        when(createAction.complete()).thenReturn(message);
        when(message.editMessageComponents(anyTopLevelComponents())).thenReturn(editAction);
        when(editAction.useComponentsV2()).thenReturn(editAction);
        when(editAction.setFiles(anyCollection())).thenReturn(editAction);
        when(editAction.complete()).thenReturn(message);

        var sent = new JdaTextChannel(channel).send(ComponentMessage.builder("broadcast")
                .container(container -> container.text("one"))
                .build());
        sent.update(ComponentMessage.builder("queue-focus")
                .container(container -> container.text("two"))
                .build());

        verify(editAction).useComponentsV2();
        verify(editAction).setFiles(List.<FileUpload>of());
        verify(editAction).complete();
        verify(createAction, never()).addFiles(anyCollection());
    }

    @SuppressWarnings("unchecked")
    private static Collection<MessageTopLevelComponent> anyTopLevelComponents() {
        return any(Collection.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendMapsSpacerToAttachedImage() {
        MessageChannel channel = mock(MessageChannel.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        when(channel.sendMessageComponents(anyTopLevelComponents())).thenReturn(action);
        when(action.useComponentsV2()).thenReturn(action);
        when(action.addFiles(anyCollection())).thenReturn(action);
        when(action.complete()).thenReturn(mock(Message.class));

        new JdaTextChannel(channel).send(ComponentMessage.builder("player")
                .container(container -> container.text("rows").spacer())
                .build());

        ArgumentCaptor<Collection<MessageTopLevelComponent>> components = ArgumentCaptor.forClass(Collection.class);
        verify(channel).sendMessageComponents(components.capture());
        Container container = (Container) List.copyOf(components.getValue()).getFirst();
        MediaGallery spacer = (MediaGallery) container.getComponents().get(1);
        assertThat(spacer.getItems().getFirst().getUrl()).isEqualTo("attachment://spacer.png");
        ArgumentCaptor<Collection<FileUpload>> files = ArgumentCaptor.forClass(Collection.class);
        verify(action).addFiles(files.capture());
        assertThat(files.getValue()).extracting(FileUpload::getName).containsExactly("spacer.png");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateReplacesAttachmentsWithSpacerImage() {
        MessageChannel channel = mock(MessageChannel.class);
        MessageCreateAction createAction = mock(MessageCreateAction.class);
        MessageEditAction editAction = mock(MessageEditAction.class);
        Message message = mock(Message.class);
        when(channel.sendMessageComponents(anyTopLevelComponents())).thenReturn(createAction);
        when(createAction.useComponentsV2()).thenReturn(createAction);
        when(createAction.addFiles(anyCollection())).thenReturn(createAction);
        when(createAction.complete()).thenReturn(message);
        when(message.editMessageComponents(anyTopLevelComponents())).thenReturn(editAction);
        when(editAction.useComponentsV2()).thenReturn(editAction);
        when(editAction.setFiles(anyCollection())).thenReturn(editAction);
        when(editAction.complete()).thenReturn(message);
        ComponentMessage player = ComponentMessage.builder("player")
                .container(container -> container.text("rows").spacer())
                .build();

        new JdaTextChannel(channel).send(player).update(player);

        ArgumentCaptor<Collection<FileUpload>> files = ArgumentCaptor.forClass(Collection.class);
        verify(editAction).setFiles(files.capture());
        assertThat(files.getValue()).extracting(FileUpload::getName).containsExactly("spacer.png");
    }

}
