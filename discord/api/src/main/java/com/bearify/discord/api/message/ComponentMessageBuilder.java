package com.bearify.discord.api.message;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ComponentMessageBuilder {
    private final String label;
    private final List<TopLevelItem> items = new ArrayList<>();

    ComponentMessageBuilder(String label) {
        this.label = label;
    }

    public ComponentMessageBuilder text(String text) {
        items.add(new TextBlock(text));
        return this;
    }

    public ComponentMessageBuilder image(String url, String description) {
        items.add(new ImageBlock(url, description));
        return this;
    }

    public ComponentMessageBuilder container(Consumer<ContainerBuilder> consumer) {
        ContainerBuilder builder = new ContainerBuilder();
        consumer.accept(builder);
        Container container = builder.build();
        if (!container.children().isEmpty()) {
            items.add(container);
        }
        return this;
    }

    public ComponentMessage build() {
        return new ComponentMessage(label, items);
    }
}
