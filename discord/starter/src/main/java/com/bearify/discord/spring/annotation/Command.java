package com.bearify.discord.spring.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Marks a class as a Discord command.
 * Implies {@code @Component} so Spring picks it up automatically.
 *
 * <p>Without a name, methods annotated with {@link Interaction} are registered as top-level commands:
 * <pre>{@code
 * @Command
 * public class PingCommand {
 *     @Interaction("ping")
 *     public void ping(CommandInteraction interaction) { ... }
 * }
 * }</pre>
 *
 * <p>With a name, methods become subcommands grouped under it:
 * <pre>{@code
 * @Command("music")
 * public class MusicCommand {
 *     @Interaction("play")
 *     public void play(CommandInteraction interaction) { ... }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Command {
    String value() default "";
    String description() default "";
}
