package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.spring.annotation.CommandAdvice;
import com.bearify.discord.spring.annotation.HandleException;
import com.bearify.discord.testing.MockCommandInteraction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommandExceptionHandlerRegistryTest {

    @CommandAdvice
    static class TestAdvice {
        final List<Throwable> caught = new ArrayList<>();

        @HandleException(RuntimeException.class)
        void onRuntime(CommandInteraction interaction, RuntimeException e) {
            caught.add(e);
        }

        @HandleException(IllegalArgumentException.class)
        void onIllegalArg(CommandInteraction interaction, IllegalArgumentException e) {
            caught.add(e);
        }
    }

    private AnnotationConfigApplicationContext context;
    private CommandExceptionHandlerRegistry registry;
    private TestAdvice advice;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        context = new AnnotationConfigApplicationContext();
        context.registerBean("testAdvice", TestAdvice.class, TestAdvice::new);
        context.refresh();

        advice = context.getBean(TestAdvice.class);

        registry = new CommandExceptionHandlerRegistry(context);
        Method onRuntime = TestAdvice.class.getDeclaredMethod("onRuntime", CommandInteraction.class, RuntimeException.class);
        registry.register("testAdvice", onRuntime.getAnnotation(HandleException.class), onRuntime);
        Method onIllegalArg = TestAdvice.class.getDeclaredMethod("onIllegalArg", CommandInteraction.class, IllegalArgumentException.class);
        registry.register("testAdvice", onIllegalArg.getAnnotation(HandleException.class), onIllegalArg);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    // --- HAPPY PATH ---

    @Test
    void routesExceptionToExactHandler() {
        RuntimeException ex = new RuntimeException("boom");
        registry.handle(MockCommandInteraction.forCommand("test").build(), ex);

        assertThat(advice.caught).containsExactly(ex);
    }

    @Test
    void prefersSpecificHandlerOverParent() {
        IllegalArgumentException ex = new IllegalArgumentException("bad arg");
        registry.handle(MockCommandInteraction.forCommand("test").build(), ex);

        assertThat(advice.caught).containsExactly(ex);
    }

    // --- EDGE CASES ---

    @Test
    void walksUpHierarchyToFindHandler() {
        // NumberFormatException extends IllegalArgumentException — walks up to find its handler
        NumberFormatException ex = new NumberFormatException("not a number");
        registry.handle(MockCommandInteraction.forCommand("test").build(), ex);

        assertThat(advice.caught).containsExactly(ex);
    }

    @Test
    void fallsBackToEphemeralReplyWhenNoHandlerMatches() {
        // Error does not extend RuntimeException — no handler matches
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("test").build();
        registry.handle(interaction, new Error("fatal"));

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().isSent()).isTrue();
    }

    @Test
    void doesNotSendFallbackReplyWhenHandlerIsFound() {
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("test").build();
        registry.handle(interaction, new RuntimeException("handled"));

        assertThat(interaction.getReplies()).isEmpty();
    }
}
