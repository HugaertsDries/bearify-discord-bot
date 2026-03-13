package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.CommandInteraction;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.function.Consumer;

/**
 * JDA event listener that forwards slash command interactions to the registry dispatcher.
 */
class JdaEventListener extends ListenerAdapter {

    private final Consumer<CommandInteraction> handler;

    JdaEventListener(Consumer<CommandInteraction> handler) {
        this.handler = handler;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        handler.accept(new JdaCommandInteraction(event));
    }
}
