package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.spring.annotation.Command;
import com.bearify.discord.spring.annotation.Interaction;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationScannerTest {

    @Command
    static class SomeCommand {
        @Interaction("ping")
        void ping(CommandInteraction interaction) {}

        @Interaction("pong")
        void pong(CommandInteraction interaction) {}

        void notAHandler() {}
    }

    @Command
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
            new AnnotationScanner().scan(ctx, Command.class, Interaction.class,
                    (ann, _, _) -> names.add(ann.value()));

            assertThat(names).containsExactlyInAnyOrder("ping", "pong", "foo");
        }
    }

    @Test
    void passesCorrectBeanAndMethodToHandler() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(SomeCommand.class);
            ctx.refresh();

            List<Object> beans = new ArrayList<>();
            List<Method> methods = new ArrayList<>();
            new AnnotationScanner().scan(ctx, Command.class, Interaction.class,
                    (_, bean, method) -> { beans.add(bean); methods.add(method); });

            assertThat(beans).allMatch(bean -> bean instanceof SomeCommand);
            assertThat(methods).extracting(Method::getName)
                    .containsExactlyInAnyOrder("ping", "pong");
        }
    }

    // --- EDGE CASES ---

    @Test
    void ignoresBeansNotAnnotatedWithCommand() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(NotACommand.class);
            ctx.refresh();

            List<String> names = new ArrayList<>();
            new AnnotationScanner().scan(ctx, Command.class, Interaction.class,
                    (ann, _, _) -> names.add(ann.value()));

            assertThat(names).isEmpty();
        }
    }

    @Test
    void ignoresMethodsNotAnnotatedWithInteraction() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(SomeCommand.class);
            ctx.refresh();

            List<Method> methods = new ArrayList<>();
            new AnnotationScanner().scan(ctx, Command.class, Interaction.class,
                    (_, _, method) -> methods.add(method));

            assertThat(methods).hasSize(2);
        }
    }
}
