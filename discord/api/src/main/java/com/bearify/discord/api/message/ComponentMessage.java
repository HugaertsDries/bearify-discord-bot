package com.bearify.discord.api.message;

import java.util.List;

public record ComponentMessage(String label, List<TopLevelItem> items) {
    public ComponentMessage {
        items = List.copyOf(items);
    }

    public static ComponentMessageBuilder builder(String label) {
        return new ComponentMessageBuilder(label);
    }

    public List<Container> containers() {
        return items.stream()
                .filter(Container.class::isInstance)
                .map(Container.class::cast)
                .toList();
    }
}
