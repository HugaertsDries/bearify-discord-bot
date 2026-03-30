package com.bearify.discord.api.message;

public record Divider(Spacing spacing) implements ContainerChild {
    @Override
    public Kind kind() {
        return Kind.SEPARATOR;
    }
}
