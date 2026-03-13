package com.bearify.discord.spring.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Marks a class as a global exception handler for Discord commands.
 * Works like Spring MVC's {@code @ControllerAdvice}.
 *
 * <pre>{@code
 * @DiscordControllerAdvice
 * public class GlobalExceptionHandler {
 *
 *     @HandleException(RuntimeException.class)
 *     public void onRuntimeException(CommandInteraction interaction, RuntimeException e) {
 *         interaction.reply("Something went wrong.").ephemeral().send();
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface DiscordControllerAdvice {
    String value() default "";
}
