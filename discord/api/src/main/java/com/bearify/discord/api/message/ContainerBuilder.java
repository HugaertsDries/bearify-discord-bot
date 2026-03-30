package com.bearify.discord.api.message;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ContainerBuilder {
    private Integer accentColor;
    private final List<ContainerChild> children = new ArrayList<>();

    public ContainerBuilder accentColor(int color) {
        this.accentColor = color;
        return this;
    }

    public ContainerBuilder image(String url, String description) {
        children.add(new ImageBlock(url, description));
        return this;
    }

    public ContainerBuilder text(String text) {
        children.add(new TextBlock(text));
        return this;
    }

    public ContainerBuilder separator() {
        children.add(new Divider(Spacing.SMALL));
        return this;
    }

    public ContainerBuilder separator(Spacing spacing) {
        children.add(new Divider(spacing));
        return this;
    }

    public ContainerBuilder section(Consumer<SectionBuilder> consumer) {
        SectionBuilder builder = new SectionBuilder();
        consumer.accept(builder);
        children.add(builder.build());
        return this;
    }

    public ContainerBuilder ActionRow(Consumer<ActionRowBuilder> consumer) {
        ActionRowBuilder builder = new ActionRowBuilder();
        consumer.accept(builder);
        children.add(new ActionRow(builder.build()));
        return this;
    }

    Container build() {
        return new Container(accentColor, children);
    }
}
