package com.bearify.discord.spring.annotation;

import java.lang.annotation.*;

/**
 * Binds a method parameter to a Discord slash command option.
 *
 * <p>Supported parameter types: {@code String}, {@code int}/{@code Integer},
 * {@code long}/{@code Long}, {@code boolean}/{@code Boolean}.
 *
 * <pre>{@code
 * @Interaction(value = "poke", description = "Poke the bear.")
 * public void poke(CommandInteraction interaction,
 *                  @Option(name = "pokes", description = "How many times", defaultValue = "4") int pokes) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Option {
    String name();
    String description() default "";
    boolean required() default false;
    String defaultValue() default "";
}
