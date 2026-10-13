package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.InteractionType;
import com.bearify.discord.api.interaction.MessageContextInteraction;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.spring.annotation.DiscordController;
import com.bearify.discord.spring.annotation.Interaction;
import com.bearify.discord.spring.annotation.InteractionGroup;
import com.bearify.discord.testing.MockMessageContextInteraction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageContextRegistryTest {

    private static final String PLAY_WITH_BEARIFY = "Play with Bearify";

    @DiscordController
    @InteractionGroup(value = "player", description = "Player commands")
    static class MessageController {
        boolean invoked;

        @Interaction(type = InteractionType.MESSAGE_CONTEXT, value = PLAY_WITH_BEARIFY)
        void play(MessageContextInteraction interaction) {
            invoked = true;
            interaction.reply("ok").ephemeral().send();
        }

        @Interaction(type = InteractionType.MESSAGE_CONTEXT, value = PLAY_WITH_BEARIFY)
        void playAgain(MessageContextInteraction interaction) {
        }

        @Interaction(value = "play", description = "Play a song")
        void slash(MessageContextInteraction interaction) {
        }
    }

    private AnnotationConfigApplicationContext context;
    private MessageContextRegistry registry;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean("messageController", MessageController.class, MessageController::new);
        context.refresh();
        registry = new MessageContextRegistry(context);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    // happy path

    @Test
    void registersMessageCommandDefinition() throws NoSuchMethodException {
        register("play");

        assertThat(registry.getDefinitions()).containsExactly(CommandDefinition.message(PLAY_WITH_BEARIFY));
    }

    @Test
    void dispatchesMessageContextInteractionToMatchingHandler() throws NoSuchMethodException {
        register("play");
        MockMessageContextInteraction interaction = MockMessageContextInteraction.forMessageCommand(PLAY_WITH_BEARIFY).build();

        registry.handle(interaction);

        assertThat(context.getBean(MessageController.class).invoked).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).isEqualTo("ok");
    }

    // exceptions

    @Test
    void rejectsDuplicateMessageCommands() throws NoSuchMethodException {
        register("play");

        assertThatThrownBy(() -> register("playAgain"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PLAY_WITH_BEARIFY);
    }

    // edge cases

    @Test
    void ignoresInteractionGroupForMessageCommands() throws NoSuchMethodException {
        register("play");

        assertThat(registry.getDefinitions()).extracting(CommandDefinition::name).containsExactly(PLAY_WITH_BEARIFY);
    }

    @Test
    void ignoresOtherInteractionTypes() throws NoSuchMethodException {
        register("slash");

        assertThat(registry.getDefinitions()).isEmpty();
    }

    @Test
    void repliesUnsupportedWhenNoHandlerFound() {
        MockMessageContextInteraction interaction = MockMessageContextInteraction.forMessageCommand("Unknown").build();

        registry.handle(interaction);

        assertThat(interaction.getReplies()).singleElement().satisfies(reply -> {
            assertThat(reply.getContent()).isEqualTo("This command is not supported.");
            assertThat(reply.isEphemeral()).isTrue();
        });
    }

    private void register(String methodName) throws NoSuchMethodException {
        Method method = MessageController.class.getDeclaredMethod(methodName, MessageContextInteraction.class);
        registry.register("messageController", method.getAnnotation(Interaction.class), method);
    }
}
