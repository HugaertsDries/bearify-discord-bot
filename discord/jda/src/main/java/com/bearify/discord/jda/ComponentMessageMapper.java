package com.bearify.discord.jda;

import com.bearify.discord.api.message.ActionRow;
import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.discord.api.message.Container;
import com.bearify.discord.api.message.ContainerChild;
import com.bearify.discord.api.message.Divider;
import com.bearify.discord.api.message.ImageBlock;
import com.bearify.discord.api.message.InteractiveButton;
import com.bearify.discord.api.message.LinkButton;
import com.bearify.discord.api.message.Section;
import com.bearify.discord.api.message.SectionAccessory;
import com.bearify.discord.api.message.Spacer;
import com.bearify.discord.api.message.Spacing;
import com.bearify.discord.api.message.TextBlock;
import com.bearify.discord.api.message.TopLevelItem;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.section.SectionAccessoryComponent;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.utils.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

final class ComponentMessageMapper {

    // shortcut: spacer is an uploaded transparent image, Discord has no width setting; if edits stop resolving attachment://, fall back to a hosted URL behind the same Spacer mapping
    private static final String SPACER_FILE = "spacer.png";
    private static final byte[] SPACER_IMAGE = readSpacerImage();

    List<MessageTopLevelComponent> toJdaComponents(ComponentMessage message) {
        return message.items().stream()
                .flatMap(item -> toTopLevelComponents(item).stream())
                .toList();
    }

    private List<MessageTopLevelComponent> toTopLevelComponents(TopLevelItem item) {
        return switch (item) {
            case TextBlock textBlock -> List.of(TextDisplay.of(textBlock.text()));
            case Container container -> {
                List<ContainerChildComponent> children = container.children().stream()
                        .flatMap(child -> toContainerChildren(child).stream())
                        .toList();
                if (children.isEmpty()) {
                    yield List.of();
                }
                net.dv8tion.jda.api.components.container.Container jdaContainer =
                        net.dv8tion.jda.api.components.container.Container.of(children);
                if (container.accentColor() != null) {
                    jdaContainer = jdaContainer.withAccentColor(container.accentColor());
                }
                yield List.of(jdaContainer);
            }
            case ImageBlock imageBlock -> List.of(toMediaGallery(imageBlock));
        };
    }

    /** The files a message must carry: the spacer image when it uses a {@link Spacer}, nothing otherwise. */
    List<FileUpload> filesFor(ComponentMessage message) {
        boolean usesSpacer = message.containers().stream()
                .flatMap(container -> container.children().stream())
                .anyMatch(Spacer.class::isInstance);
        // a FileUpload is read once when sent, so every message gets a fresh one
        return usesSpacer ? List.of(FileUpload.fromData(SPACER_IMAGE, SPACER_FILE)) : List.of();
    }

    private List<ContainerChildComponent> toContainerChildren(ContainerChild child) {
        return switch (child) {
            case TextBlock textBlock -> List.of(TextDisplay.of(textBlock.text()));
            case Divider divider -> List.of(Separator.createDivider(toJdaSpacing(divider.spacing())));
            case ActionRow actionRow -> List.of(net.dv8tion.jda.api.components.actionrow.ActionRow.of(
                    actionRow.buttons().stream().map(this::toButton).toList()
            ));
            case Section section -> toSection(section);
            case ImageBlock imageBlock -> List.of(toMediaGallery(imageBlock));
            // a wide transparent image stretches the container; the image itself travels as an attachment, see filesFor
            case Spacer ignored -> List.of(MediaGallery.of(MediaGalleryItem.fromUrl("attachment://" + SPACER_FILE)));
        };
    }

    private List<ContainerChildComponent> toSection(Section section) {
        List<TextDisplay> texts = section.texts().stream()
                .map(TextDisplay::of)
                .toList();
        return section.accessory()
                .map(accessory -> List.of(net.dv8tion.jda.api.components.section.Section.of(toJdaAccessory(accessory), texts)))
                .map(components -> components.stream()
                        .map(component -> (ContainerChildComponent) component)
                        .toList())
                .orElseGet(() -> texts.stream()
                        .map(text -> (ContainerChildComponent) text)
                        .toList());
    }

    private SectionAccessoryComponent toJdaAccessory(SectionAccessory accessory) {
        return switch (accessory) {
            case InteractiveButton button -> toButton(button);
            case LinkButton linkButton -> Button.link(linkButton.url(), linkButton.label());
            case com.bearify.discord.api.message.Thumbnail thumbnail -> {
                Thumbnail jdaThumbnail = Thumbnail.fromUrl(thumbnail.url());
                yield isBlank(thumbnail.description()) ? jdaThumbnail : jdaThumbnail.withDescription(thumbnail.description());
            }
        };
    }

    private MediaGallery toMediaGallery(ImageBlock imageBlock) {
        MediaGalleryItem item = MediaGalleryItem.fromUrl(imageBlock.url());
        return MediaGallery.of(isBlank(imageBlock.description()) ? item : item.withDescription(imageBlock.description()));
    }

    // Discord's description is optional, but JDA rejects a blank one, so a decorative image simply has none
    private static boolean isBlank(String description) {
        return description == null || description.isBlank();
    }

    private static byte[] readSpacerImage() {
        try (InputStream image = ComponentMessageMapper.class.getResourceAsStream(SPACER_FILE)) {
            if (image == null) {
                throw new IllegalStateException(SPACER_FILE + " is missing from the classpath");
            }
            return image.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Separator.Spacing toJdaSpacing(Spacing spacing) {
        return switch (spacing) {
            case SMALL -> Separator.Spacing.SMALL;
            case LARGE -> Separator.Spacing.LARGE;
        };
    }

    private Button toButton(InteractiveButton button) {
        return switch (button.style()) {
            case PRIMARY -> Button.primary(button.id(), button.label());
            case SECONDARY -> Button.secondary(button.id(), button.label());
        };
    }
}
