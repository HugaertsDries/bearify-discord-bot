package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.api.model.OptionDefinition;
import com.bearify.discord.api.model.SubcommandDefinition;
import com.bearify.discord.spring.annotation.Command;
import com.bearify.discord.spring.annotation.Interaction;
import com.bearify.discord.spring.annotation.Option;
import com.bearify.discord.testing.MockCommandInteraction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandRegistryTest {

    @Command
    static class TestController {
        @Interaction(value = "greet", description = "Say hello")
        void greet(CommandInteraction interaction) {
            interaction.reply("Hi!").send();
        }

        @Interaction(value = "poke", description = "Poke the bear")
        void poke(CommandInteraction interaction,
                  @Option(name = "times", description = "How many times", required = true) int times,
                  @Option(name = "message", description = "Optional message") String message) {}

        @Interaction(value = "crash", description = "Always throws")
        void crash(CommandInteraction interaction) {
            throw new RuntimeException("boom");
        }
    }

    @Command(value = "music", description = "Music commands")
    static class MusicController {
        @Interaction(value = "play", description = "Play a song")
        void play(CommandInteraction interaction) {
            interaction.reply("Playing!").send();
        }

        @Interaction(value = "stop", description = "Stop playback")
        void stop(CommandInteraction interaction) {
            interaction.reply("Stopped.").send();
        }
    }

    private CommandRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry(new CommandExceptionHandlerRegistry());
    }

    // --- HAPPY PATH ---

    @Test
    void registersCommandWithDefinition() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("greet", CommandInteraction.class);
        registry.register(method.getAnnotation(Interaction.class), new TestController(), method);

        assertThat(registry.getDefinitions()).hasSize(1);
        CommandDefinition def = registry.getDefinitions().getFirst();
        assertThat(def.name()).isEqualTo("greet");
        assertThat(def.description()).isEqualTo("Say hello");
        assertThat(def.options()).isEmpty();
    }

    @Test
    void includesOptionsInCommandDefinition() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("poke", CommandInteraction.class, int.class, String.class);
        registry.register(method.getAnnotation(Interaction.class), new TestController(), method);

        List<OptionDefinition> options = registry.getDefinitions().getFirst().options();
        assertThat(options).hasSize(2);

        OptionDefinition timesOpt = options.getFirst();
        assertThat(timesOpt.name()).isEqualTo("times");
        assertThat(timesOpt.type()).isEqualTo(OptionDefinition.OptionType.INTEGER);
        assertThat(timesOpt.required()).isTrue();

        OptionDefinition messageOpt = options.getLast();
        assertThat(messageOpt.name()).isEqualTo("message");
        assertThat(messageOpt.type()).isEqualTo(OptionDefinition.OptionType.STRING);
        assertThat(messageOpt.required()).isFalse();
    }

    @Test
    void handlesCommandInteraction() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("greet", CommandInteraction.class);
        registry.register(method.getAnnotation(Interaction.class), new TestController(), method);

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("greet").build();
        registry.handle(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().getContent()).isEqualTo("Hi!");
        assertThat(interaction.getReplies().getFirst().isSent()).isTrue();
    }

    // --- CAPABILITIES ---

    @Test
    void exposesUnmodifiableDefinitionList() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("greet", CommandInteraction.class);
        registry.register(method.getAnnotation(Interaction.class), new TestController(), method);

        assertThatThrownBy(() -> registry.getDefinitions().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- EXCEPTIONS ---

    @Test
    void rejectsDuplicateCommandName() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("greet", CommandInteraction.class);
        Interaction annotation = method.getAnnotation(Interaction.class);
        TestController bean = new TestController();

        registry.register(annotation, bean, method);

        assertThatThrownBy(() -> registry.register(annotation, bean, method))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("greet");
    }

    // --- EDGE CASES ---

    @Test
    void doesNotPropagateExceptionThrownByHandler() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("crash", CommandInteraction.class);
        registry.register(method.getAnnotation(Interaction.class), new TestController(), method);

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("crash").build();

        assertThatNoException().isThrownBy(() -> registry.handle(interaction));
    }

    @Test
    void repliesEphemeralWhenCommandIsUnknown() {
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("unknown").build();
        registry.handle(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().getContent()).contains("not supported");
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().isSent()).isTrue();
    }

    // --- SUBCOMMAND GROUPS ---

    @Test
    void groupsInteractionsIntoSubcommandDefinition() throws NoSuchMethodException {
        Method playMethod = MusicController.class.getDeclaredMethod("play", CommandInteraction.class);
        Method stopMethod = MusicController.class.getDeclaredMethod("stop", CommandInteraction.class);
        MusicController bean = new MusicController();

        registry.register(playMethod.getAnnotation(Interaction.class), bean, playMethod);
        registry.register(stopMethod.getAnnotation(Interaction.class), bean, stopMethod);

        assertThat(registry.getDefinitions()).hasSize(1);
        CommandDefinition def = registry.getDefinitions().getFirst();
        assertThat(def.name()).isEqualTo("music");
        assertThat(def.options()).isEmpty();
        assertThat(def.subcommands()).hasSize(2);
        assertThat(def.subcommands().stream().map(SubcommandDefinition::name))
                .containsExactlyInAnyOrder("play", "stop");
    }

    @Test
    void handlesSubcommandInteraction() throws NoSuchMethodException {
        Method playMethod = MusicController.class.getDeclaredMethod("play", CommandInteraction.class);
        registry.register(playMethod.getAnnotation(Interaction.class), new MusicController(), playMethod);

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("music")
                .subcommand("play")
                .build();
        registry.handle(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().getContent()).isEqualTo("Playing!");
    }

    @Test
    void rejectsDuplicateSubcommandName() throws NoSuchMethodException {
        Method method = MusicController.class.getDeclaredMethod("play", CommandInteraction.class);
        Interaction annotation = method.getAnnotation(Interaction.class);
        MusicController bean = new MusicController();

        registry.register(annotation, bean, method);

        assertThatThrownBy(() -> registry.register(annotation, bean, method))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("music/play");
    }
}
