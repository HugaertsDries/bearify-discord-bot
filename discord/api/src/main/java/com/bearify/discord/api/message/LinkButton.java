package com.bearify.discord.api.message;

public record LinkButton(String url, String label) implements SectionAccessory {

    public static LinkButton of(String url, String label) {
        return new LinkButton(url, label);
    }
}
