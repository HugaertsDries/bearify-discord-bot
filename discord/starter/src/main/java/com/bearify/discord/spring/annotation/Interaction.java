package com.bearify.discord.spring.annotation;

import com.bearify.discord.api.interaction.InteractionType;

import java.lang.annotation.*;

/**
 * Marks a method inside a {@link DiscordController} as a handler for a Discord interaction.
 *
 * <p>The method must accept a single interaction parameter matching {@link #type()}: a
 * {@link com.bearify.discord.api.interaction.CommandInteraction} for commands, a
 * {@link com.bearify.discord.api.interaction.MessageContextInteraction} for {@code MESSAGE_CONTEXT}, and so on.
 * Return values are ignored; use the interaction object to send replies.
 *
 * <pre>{@code
 * @Interaction("ping")
 * public void ping(CommandInteraction interaction) {
 *     interaction.reply("Pong!").send();
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Interaction {
    InteractionType type() default InteractionType.COMMAND;
    /**
     * The interaction name (e.g. "play"). Must be lowercase, no spaces; a {@code MESSAGE_CONTEXT} name is shown
     * as is under Apps, so it may contain spaces and capitals (e.g. "Play with Bearify").
     */
    String value();
    String description() default "";
}
