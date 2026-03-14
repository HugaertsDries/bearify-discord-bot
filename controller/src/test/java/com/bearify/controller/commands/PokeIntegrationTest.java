package com.bearify.controller.commands;

import com.bearify.controller.AbstractControllerIntegrationTest;
import com.bearify.discord.spring.CommandRegistry;
import com.bearify.discord.testing.MockCommandInteraction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class PokeIntegrationTest extends AbstractControllerIntegrationTest {

    @Autowired
    CommandRegistry registry;

    // --- HAPPY PATH ---

    @Test
    void handlesPokeInteraction() {
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("poke").build();

        registry.dispatch(interaction);

        assertThat(interaction.isDeferredEphemeral()).isTrue();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("pokes survived");
    }
}
