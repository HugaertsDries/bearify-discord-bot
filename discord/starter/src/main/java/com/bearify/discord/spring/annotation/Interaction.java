package com.bearify.discord.spring.annotation;

import com.bearify.discord.api.interaction.InteractionType;

import java.lang.annotation.*;

/**
 * Marks a method inside a {@link Command} as a handler for a Discord interaction.
 *
 * <p>The method must accept a single {@link com.bearify.discord.api.interaction.CommandInteraction}
 * parameter. Return values are ignored; use the interaction object to send replies.
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
    /** The interaction name (e.g. "play"). Must be lowercase, no spaces. */
    String value();
    String description() default "";
}
