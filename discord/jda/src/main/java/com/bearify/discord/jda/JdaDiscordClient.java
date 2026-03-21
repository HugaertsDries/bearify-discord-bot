package com.bearify.discord.jda;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.Guild;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.api.model.OptionDefinition;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;
import java.util.function.Consumer;

/**
 * JDA-backed implementation of {@link DiscordClient}.
 */
public class JdaDiscordClient implements DiscordClient {

    private static final String NO_DESCRIPTION = "No description provided";

    private final List<CommandDefinition> commands;
    private final Consumer<CommandInteraction> interactionHandler;
    public JDA jda;

    JdaDiscordClient(List<CommandDefinition> commands, Consumer<CommandInteraction> interactionHandler) {
        this.commands = commands;
        this.interactionHandler = interactionHandler;
    }

    @Override
    public void start(String token) {
        connect(token);
        jda.updateCommands().addCommands(toCommandData()).queue();
    }

    @Override
    public void start(String token, String guildId) {
        connect(token);
        var guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalStateException("Guild not found: " + guildId);
        }
        guild.updateCommands().addCommands(toCommandData()).queue();
    }

    @Override
    public Guild guild(String guildId) {
        if (jda == null) {
            throw new IllegalStateException("Discord client has not been started");
        }
        return new JdaGuild(jda, guildId);
    }

    @Override
    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
        }
    }

    private void connect(String token) {
        try {
            var audio = new AudioModuleConfig();
            audio = audio.withDaveSessionFactory(new JDaveSessionFactory());
            jda = JDABuilder.createDefault(token)
                    .addEventListeners(new JdaEventListener(interactionHandler))
                    .setAudioModuleConfig(audio)
                    .build();
            jda.awaitReady();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for JDA to be ready", e);
        }
    }

    private List<CommandData> toCommandData() {
        return commands.stream()
                .map(def -> {
                    String description = def.description().isBlank() ? NO_DESCRIPTION : def.description();
                    if (!def.subcommands().isEmpty()) {
                        SlashCommandData group = Commands.slash(def.name(), description);
                        def.subcommands().forEach(sub -> {
                            SubcommandData subData = new SubcommandData(sub.name(),
                                    sub.description().isBlank() ? NO_DESCRIPTION : sub.description());
                            sub.options().stream().map(this::toOptionData).forEach(subData::addOptions);
                            group.addSubcommands(subData);
                        });
                        return group;
                    }
                    List<OptionData> options = def.options().stream().map(this::toOptionData).toList();
                    return (CommandData) Commands.slash(def.name(), description).addOptions(options);
                })
                .toList();
    }

    private OptionData toOptionData(OptionDefinition option) {
        OptionType jdaType = OptionType.valueOf(option.type().name());
        String description = option.description().isBlank() ? NO_DESCRIPTION : option.description();
        return new OptionData(jdaType, option.name(), description, option.required());
    }
}
