package com.bearify.discord.api.message;

public record ImageBlock(String url, String description) implements TopLevelItem, ContainerChild {
    @Override
    public Kind kind() {
        return Kind.IMAGE_BLOCK;
    }
}
