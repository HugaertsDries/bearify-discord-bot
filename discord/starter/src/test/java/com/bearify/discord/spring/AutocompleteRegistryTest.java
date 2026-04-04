package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.Option;
import com.bearify.discord.api.interaction.AutocompleteInteraction;
import com.bearify.discord.spring.annotation.DiscordController;
import com.bearify.discord.spring.annotation.Interaction;
import com.bearify.discord.testing.MockAutocompleteInteraction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutocompleteRegistryTest {

    @DiscordController
    static class SearchController {
        boolean invoked;

        @Interaction(type = com.bearify.discord.api.interaction.InteractionType.AUTOCOMPLETE, value = "player:play:search")
        void search(AutocompleteInteraction interaction) {
            invoked = true;
            interaction.reply(List.of(new Option("Song A", "song-a")));
        }
    }

    @DiscordController
    static class IgnoredController {
        boolean invoked;

        @Interaction(value = "player:play:search")
        void search(AutocompleteInteraction interaction) {
            invoked = true;
            interaction.reply(List.of(new Option("Ignored", "ignored")));
        }
    }

    private AnnotationConfigApplicationContext context;
    private AutocompleteRegistry registry;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean("searchController", SearchController.class, SearchController::new);
        context.refresh();
        registry = new AutocompleteRegistry(context);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void dispatchesAutocompleteInteractionToMatchingHandler() throws NoSuchMethodException {
        Method method = SearchController.class.getDeclaredMethod("search", AutocompleteInteraction.class);
        registry.register("searchController", method.getAnnotation(Interaction.class), method);

        MockAutocompleteInteraction interaction = MockAutocompleteInteraction.forAutocomplete("player:play:search", "lofi").build();
        registry.handle(interaction);

        assertThat(context.getBean(SearchController.class).invoked).isTrue();
        assertThat(interaction.getReplies()).singleElement().satisfies(choices ->
                assertThat(choices).containsExactly(new Option("Song A", "song-a")));
    }

    @Test
    void repliesWithEmptyChoicesWhenAutocompleteInteractionIsUnknown() {
        MockAutocompleteInteraction interaction = MockAutocompleteInteraction.forAutocomplete("player:play:missing", "lofi").build();
        registry.handle(interaction);

        assertThat(interaction.getReplies()).singleElement().satisfies(List::isEmpty);
    }

    @Test
    void ignoresNonAutocompleteHandlers() throws NoSuchMethodException {
        Method method = IgnoredController.class.getDeclaredMethod("search", AutocompleteInteraction.class);
        registry.register("searchController", method.getAnnotation(Interaction.class), method);

        MockAutocompleteInteraction interaction = MockAutocompleteInteraction.forAutocomplete("player:play:search", "lofi").build();
        registry.handle(interaction);

        assertThat(context.getBean(SearchController.class).invoked).isFalse();
        assertThat(interaction.getReplies()).singleElement().satisfies(List::isEmpty);
    }
}
