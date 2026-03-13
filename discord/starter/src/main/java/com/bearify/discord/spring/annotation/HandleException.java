package com.bearify.discord.spring.annotation;

import java.lang.annotation.*;

/**
 * Marks a method inside a {@link DiscordControllerAdvice} as the handler for a specific exception type.
 *
 * <p>The method must accept a {@link com.bearify.discord.api.interaction.CommandInteraction}
 * and a throwable parameter matching the declared exception type.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HandleException {
    Class<? extends Throwable> value();
}
