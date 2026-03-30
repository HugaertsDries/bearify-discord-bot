package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.ButtonInteraction;
import com.bearify.discord.api.interaction.InteractionType;
import com.bearify.discord.spring.annotation.DiscordController;
import com.bearify.discord.spring.annotation.Interaction;
import com.bearify.discord.testing.MockButtonInteraction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ButtonRegistryTest {

    @DiscordController
    static class ButtonController {
        boolean invoked;

        @Interaction(type = InteractionType.BUTTON, value = "player:pause-play")
        void pausePlay(ButtonInteraction interaction) {
            invoked = true;
            interaction.reply("ok").ephemeral().send();
        }
    }

    private AnnotationConfigApplicationContext context;
    private ButtonRegistry registry;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean("buttonController", ButtonController.class, ButtonController::new);
        context.refresh();
        registry = new ButtonRegistry(context);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void dispatchesButtonInteractionToMatchingHandler() throws NoSuchMethodException {
        Method method = ButtonController.class.getDeclaredMethod("pausePlay", ButtonInteraction.class);
        registry.register("buttonController", method.getAnnotation(Interaction.class), method);

        MockButtonInteraction interaction = MockButtonInteraction.forButton("player:pause-play").build();
        registry.handle(interaction);

        assertThat(context.getBean(ButtonController.class).invoked).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).isEqualTo("ok");
    }
}
