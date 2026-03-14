package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.spring.annotation.Option;
import com.bearify.discord.testing.MockCommandInteraction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandHandlerTest {

    static class TestController {
        final List<Object[]> invocations = new ArrayList<>();

        void withInteraction(CommandInteraction interaction) {
            invocations.add(new Object[]{interaction});
        }

        void withStringOption(@Option(name = "msg") String msg) {
            invocations.add(new Object[]{msg});
        }

        void withIntOption(@Option(name = "count") int count) {
            invocations.add(new Object[]{count});
        }

        void withIntDefault(@Option(name = "count", defaultValue = "5") int count) {
            invocations.add(new Object[]{count});
        }

        void withBooleanOption(@Option(name = "flag") boolean flag) {
            invocations.add(new Object[]{flag});
        }

        void withLongOption(@Option(name = "id") long id) {
            invocations.add(new Object[]{id});
        }

        void withMixed(CommandInteraction interaction,
                       @Option(name = "x") int x,
                       @Option(name = "label") String label) {
            invocations.add(new Object[]{interaction, x, label});
        }

        void withUnsupported(@Option(name = "obj") Object obj) {}
    }

    // --- HAPPY PATH ---

    @Test
    void passesInteractionAsArgument() throws NoSuchMethodException {
        TestController bean = new TestController();
        Method method = TestController.class.getDeclaredMethod("withInteraction", CommandInteraction.class);
        CommandHandler handler = new CommandHandler(bean, method);

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("test").build();
        handler.invoke(interaction);

        assertThat(bean.invocations).hasSize(1);
        assertThat(bean.invocations.getFirst()[0]).isSameAs(interaction);
    }

    @Test
    void resolvesStringOptionFromInteraction() throws NoSuchMethodException {
        TestController bean = new TestController();
        Method method = TestController.class.getDeclaredMethod("withStringOption", String.class);
        CommandHandler handler = new CommandHandler(bean, method);

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("test")
                .option("msg", "hello world")
                .build();
        handler.invoke(interaction);

        assertThat(bean.invocations.getFirst()[0]).isEqualTo("hello world");
    }

    @Test
    void resolvesIntOptionFromInteraction() throws NoSuchMethodException {
        TestController bean = new TestController();
        Method method = TestController.class.getDeclaredMethod("withIntOption", int.class);
        CommandHandler handler = new CommandHandler(bean, method);

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("test")
                .option("count", "42")
                .build();
        handler.invoke(interaction);

        assertThat(bean.invocations.getFirst()[0]).isEqualTo(42);
    }

    @Test
    void resolvesBooleanOptionFromInteraction() throws NoSuchMethodException {
        TestController bean = new TestController();
        Method method = TestController.class.getDeclaredMethod("withBooleanOption", boolean.class);
        CommandHandler handler = new CommandHandler(bean, method);

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("test")
                .option("flag", "true")
                .build();
        handler.invoke(interaction);

        assertThat(bean.invocations.getFirst()[0]).isEqualTo(true);
    }

    @Test
    void resolvesLongOptionFromInteraction() throws NoSuchMethodException {
        TestController bean = new TestController();
        Method method = TestController.class.getDeclaredMethod("withLongOption", long.class);
        CommandHandler handler = new CommandHandler(bean, method);

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("test")
                .option("id", "9876543210")
                .build();
        handler.invoke(interaction);

        assertThat(bean.invocations.getFirst()[0]).isEqualTo(9876543210L);
    }

    @Test
    void resolvesMixedInteractionAndOptionParameters() throws NoSuchMethodException {
        TestController bean = new TestController();
        Method method = TestController.class.getDeclaredMethod("withMixed",
                CommandInteraction.class, int.class, String.class);
        CommandHandler handler = new CommandHandler(bean, method);

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("test")
                .option("x", "7")
                .option("label", "hello")
                .build();
        handler.invoke(interaction);

        assertThat(bean.invocations.getFirst()[0]).isSameAs(interaction);
        assertThat(bean.invocations.getFirst()[1]).isEqualTo(7);
        assertThat(bean.invocations.getFirst()[2]).isEqualTo("hello");
    }

    // --- EXCEPTIONS ---

    @Test
    void rejectsUnsupportedOptionTypeAtConstruction() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("withUnsupported", Object.class);

        assertThatThrownBy(() -> new CommandHandler(new TestController(), method))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported");
    }

    // --- EDGE CASES ---

    @Test
    void usesDefaultValueWhenOptionIsAbsent() throws NoSuchMethodException {
        TestController bean = new TestController();
        Method method = TestController.class.getDeclaredMethod("withIntDefault", int.class);
        CommandHandler handler = new CommandHandler(bean, method);

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("test").build();
        handler.invoke(interaction);

        assertThat(bean.invocations.getFirst()[0]).isEqualTo(5);
    }

    @Test
    void defaultsIntToZeroWhenOptionAndDefaultAreBothAbsent() throws NoSuchMethodException {
        TestController bean = new TestController();
        Method method = TestController.class.getDeclaredMethod("withIntOption", int.class);
        CommandHandler handler = new CommandHandler(bean, method);

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("test").build();
        handler.invoke(interaction);

        assertThat(bean.invocations.getFirst()[0]).isEqualTo(0);
    }

    @Test
    void defaultsLongToZeroWhenOptionAndDefaultAreBothAbsent() throws NoSuchMethodException {
        TestController bean = new TestController();
        Method method = TestController.class.getDeclaredMethod("withLongOption", long.class);
        CommandHandler handler = new CommandHandler(bean, method);

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("test").build();
        handler.invoke(interaction);

        assertThat(bean.invocations.getFirst()[0]).isEqualTo(0L);
    }

    @Test
    void defaultsBooleanToFalseWhenOptionAndDefaultAreBothAbsent() throws NoSuchMethodException {
        TestController bean = new TestController();
        Method method = TestController.class.getDeclaredMethod("withBooleanOption", boolean.class);
        CommandHandler handler = new CommandHandler(bean, method);

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("test").build();
        handler.invoke(interaction);

        assertThat(bean.invocations.getFirst()[0]).isEqualTo(false);
    }
}
