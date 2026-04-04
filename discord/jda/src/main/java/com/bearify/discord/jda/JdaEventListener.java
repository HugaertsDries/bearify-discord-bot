package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.ButtonInteraction;
import com.bearify.discord.api.interaction.AutocompleteInteraction;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.Interaction;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * JDA event listener that forwards slash command interactions to the registry dispatcher.
 * Each interaction is dispatched to the given executor so that long-running handlers
 * do not block JDA's event thread.
 */
class JdaEventListener extends ListenerAdapter {

    private final Executor executor;
    private final Consumer<Interaction> interactionHandler;
    private final Function<SlashCommandInteractionEvent, CommandInteraction> commandInteractionFactory;
    private final Function<ButtonInteractionEvent, ButtonInteraction> buttonInteractionFactory;
    private final Function<CommandAutoCompleteInteractionEvent, AutocompleteInteraction> autocompleteInteractionFactory;

    JdaEventListener(Executor executor, Consumer<Interaction> interactionHandler) {
        this(executor, interactionHandler, JdaCommandInteraction::new, JdaButtonInteraction::new, JdaAutocompleteInteraction::new);
    }

    JdaEventListener(Executor executor,
                     Consumer<Interaction> interactionHandler,
                     Function<SlashCommandInteractionEvent, CommandInteraction> commandInteractionFactory,
                     Function<ButtonInteractionEvent, ButtonInteraction> buttonInteractionFactory) {
        this(executor, interactionHandler, commandInteractionFactory, buttonInteractionFactory, JdaAutocompleteInteraction::new);
    }

    JdaEventListener(Executor executor,
                     Consumer<Interaction> interactionHandler,
                     Function<SlashCommandInteractionEvent, CommandInteraction> commandInteractionFactory,
                     Function<ButtonInteractionEvent, ButtonInteraction> buttonInteractionFactory,
                     Function<CommandAutoCompleteInteractionEvent, AutocompleteInteraction> autocompleteInteractionFactory) {
        this.executor = executor;
        this.interactionHandler = interactionHandler;
        this.commandInteractionFactory = commandInteractionFactory;
        this.buttonInteractionFactory = buttonInteractionFactory;
        this.autocompleteInteractionFactory = autocompleteInteractionFactory;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        CommandInteraction interaction = commandInteractionFactory.apply(event);
        executor.execute(() -> interactionHandler.accept(interaction));
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        ButtonInteraction interaction = buttonInteractionFactory.apply(event);
        executor.execute(() -> interactionHandler.accept(interaction));
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        AutocompleteInteraction interaction = autocompleteInteractionFactory.apply(event);
        executor.execute(() -> interactionHandler.accept(interaction));
    }
}
