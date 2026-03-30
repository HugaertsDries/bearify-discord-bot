package com.bearify.discord.api.message;

import java.util.List;

public record Container(Integer accentColor, List<ContainerChild> children) implements TopLevelItem {
    public Container {
        children = List.copyOf(children);
    }
}
