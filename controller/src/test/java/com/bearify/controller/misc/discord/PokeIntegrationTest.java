package com.bearify.controller.misc.discord;

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

        registry.handle(interaction);

        assertThat(interaction.isDeferredEphemeral()).isFalse();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("pokes survived");
    }
}
