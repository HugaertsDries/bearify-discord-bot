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

import java.util.List;

final class ComponentMessageMapper {

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

    private List<ContainerChildComponent> toContainerChildren(ContainerChild child) {
        return switch (child) {
            case TextBlock textBlock -> List.of(TextDisplay.of(textBlock.text()));
            case Divider divider -> List.of(Separator.createDivider(toJdaSpacing(divider.spacing())));
            case ActionRow actionRow -> List.of(net.dv8tion.jda.api.components.actionrow.ActionRow.of(
                    actionRow.buttons().stream().map(this::toButton).toList()
            ));
            case Section section -> toSection(section);
            case ImageBlock imageBlock -> List.of(toMediaGallery(imageBlock));
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
            case com.bearify.discord.api.message.Thumbnail thumbnail ->
                    Thumbnail.fromUrl(thumbnail.url()).withDescription(thumbnail.description());
        };
    }

    private MediaGallery toMediaGallery(ImageBlock imageBlock) {
        return MediaGallery.of(
                MediaGalleryItem.fromUrl(imageBlock.url()).withDescription(imageBlock.description())
        );
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
