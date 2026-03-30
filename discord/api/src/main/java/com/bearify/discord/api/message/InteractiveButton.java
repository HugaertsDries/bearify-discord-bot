package com.bearify.discord.api.message;

public record InteractiveButton(ButtonStyle style, String id, String label) implements SectionAccessory {
}
