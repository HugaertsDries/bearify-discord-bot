package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.CommandInteraction;
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
    private final Consumer<CommandInteraction> handler;
    private final Function<SlashCommandInteractionEvent, CommandInteraction> interactionFactory;

    JdaEventListener(Executor executor, Consumer<CommandInteraction> handler) {
        this(executor, handler, JdaCommandInteraction::new);
    }

    JdaEventListener(Executor executor, Consumer<CommandInteraction> handler,
                     Function<SlashCommandInteractionEvent, CommandInteraction> interactionFactory) {
        this.executor = executor;
        this.handler = handler;
        this.interactionFactory = interactionFactory;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        CommandInteraction interaction = interactionFactory.apply(event);
        executor.execute(() -> handler.accept(interaction));
    }
}
