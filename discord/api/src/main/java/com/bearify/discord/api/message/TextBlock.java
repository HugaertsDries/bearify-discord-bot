package com.bearify.discord.api.message;

public record TextBlock(String text) implements ContainerChild, TopLevelItem {
    @Override
    public Kind kind() {
        return Kind.TEXT;
    }
}
