package com.bearify.discord.api.message;

/**
 * Stretches its container to the full message width; Discord otherwise sizes a container that only holds text to that text.
 */
public record Spacer() implements ContainerChild {
    @Override
    public Kind kind() {
        return Kind.SPACER;
    }
}
