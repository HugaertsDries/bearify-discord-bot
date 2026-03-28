package com.bearify.discord.api.gateway;

public record Activity(Type type, String text) {

    public enum Type {
        PLAYING,
        LISTENING,
        WATCHING,
        COMPETING
    }
}
