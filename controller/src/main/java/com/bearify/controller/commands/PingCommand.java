package com.bearify.controller.commands;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.spring.annotation.Command;
import com.bearify.discord.spring.annotation.Interaction;

@Command
public class PingCommand {

    @Interaction(value = "ping", description = "Check if the bot is alive")
    public void ping(CommandInteraction interaction) {
        interaction.reply("Pong!").send();
    }
}
