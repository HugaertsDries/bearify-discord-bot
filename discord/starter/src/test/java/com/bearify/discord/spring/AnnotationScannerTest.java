package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.spring.annotation.DiscordController;
import com.bearify.discord.spring.annotation.Interaction;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationScannerTest {

    @DiscordController
    static class SomeCommand {
        @Interaction("ping")
        void ping(CommandInteraction interaction) {}

        @Interaction("pong")
        void pong(CommandInteraction interaction) {}

        void notAHandler() {}
    }

    @DiscordController
    static class AnotherCommand {
        @Interaction("foo")
        void foo(CommandInteraction interaction) {}
    }

    static class NotACommand {
        @Interaction("bar")
        void bar() {}
    }

    // --- HAPPY PATH ---

    @Test
    void scansInteractionMethodsFromAllCommandBeans() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(SomeCommand.class, AnotherCommand.class, NotACommand.class);
            ctx.refresh();

            List<String> names = new ArrayList<>();
            new AnnotationScanner().scan(ctx, DiscordController.class, Interaction.class,
                    (_, ann, _) -> names.add(ann.value()));

            assertThat(names).containsExactlyInAnyOrder("ping", "pong", "foo");
        }
    }

    @Test
    void passesCorrectBeanNameAndMethodToHandler() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(SomeCommand.class);
            ctx.refresh();

            List<String> beanNames = new ArrayList<>();
            List<Method> methods = new ArrayList<>();
            new AnnotationScanner().scan(ctx, DiscordController.class, Interaction.class,
                    (beanName, _, method) -> { beanNames.add(beanName); methods.add(method); });

            assertThat(beanNames).containsOnly("annotationScannerTest.SomeCommand");
            assertThat(methods).extracting(Method::getName)
                    .containsExactlyInAnyOrder("ping", "pong");
        }
    }

    // --- EDGE CASES ---

    @Test
    void ignoresBeansNotAnnotatedWithDiscordController() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(NotACommand.class);
            ctx.refresh();

            List<String> names = new ArrayList<>();
            new AnnotationScanner().scan(ctx, DiscordController.class, Interaction.class,
                    (_, ann, _) -> names.add(ann.value()));

            assertThat(names).isEmpty();
        }
    }

    @Test
    void ignoresMethodsNotAnnotatedWithInteraction() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(SomeCommand.class);
            ctx.refresh();

            List<Method> methods = new ArrayList<>();
            new AnnotationScanner().scan(ctx, DiscordController.class, Interaction.class,
                    (_, __, method) -> methods.add(method));

            assertThat(methods).hasSize(2);
        }
    }
}
