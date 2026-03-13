package com.bearify.controller.commands;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.spring.annotation.Command;
import com.bearify.discord.spring.annotation.Interaction;

import java.util.List;
import java.util.Random;

@Command
public class PingCommand {

    private static final List<String> RESPONSES = List.of(
            "Pong! 🏓",
            "Still hibernating... wait, no. 🐻",
            "ROAR! 🐾",
            "Grr! 😤🐻",
            "The bear is awake. 👀",
            "Did someone say honey? 🍯",
            "Bearify is very much alive. 🎵🐻",
            "Pong! Now leave me alone. 🐻💤"
    );

    private final Random random = new Random();

    @Interaction(value = "ping", description = "Check if the bot is alive")
    public void ping(CommandInteraction interaction) {
        interaction.reply(RESPONSES.get(random.nextInt(RESPONSES.size()))).ephemeral().send();
    }
}
