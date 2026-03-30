package com.bearify.discord.jda;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import com.bearify.discord.api.gateway.Activity;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.Guild;
import com.bearify.discord.api.gateway.TextChannel;
import com.bearify.discord.api.interaction.Interaction;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.api.model.OptionDefinition;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * JDA-backed implementation of {@link DiscordClient}.
 */
public class JdaDiscordClient implements DiscordClient {

    private static final String NO_DESCRIPTION = "No description provided";

    private final List<CommandDefinition> commands;
    private final ExecutorService interactionExecutor;
    private final Consumer<Interaction> interactionHandler;
    private final Activity activity;

    public JDA jda;

    JdaDiscordClient(List<CommandDefinition> commands,
                     Consumer<Interaction> interactionHandler,
                     Activity activity) {
        this.commands = commands;
        this.interactionHandler = interactionHandler;
        this.activity = activity;
        this.interactionExecutor = Executors.newVirtualThreadPerTaskExecutor();
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
    public TextChannel textChannel(String channelId) {
        if (jda == null) {
            throw new IllegalStateException("Discord client has not been started");
        }
        MessageChannel channel = jda.getChannelById(MessageChannel.class, channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Text channel not found: " + channelId);
        }
        return new JdaTextChannel(channel);
    }

    @Override
    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
        }
        interactionExecutor.shutdown();
        try {
            if (!interactionExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                interactionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            interactionExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void connect(String token) {
        try {
            var audio = new AudioModuleConfig();
            audio = audio.withDaveSessionFactory(new JDaveSessionFactory());
            JDABuilder builder = JDABuilder.createDefault(token)
                    .addEventListeners(new JdaEventListener(interactionExecutor, interactionHandler))
                    .setAudioModuleConfig(audio);
            net.dv8tion.jda.api.entities.Activity startupActivity = toActivity(activity);
            if (startupActivity != null) {
                builder = builder.setActivity(startupActivity);
            }
            jda = builder.build();
            jda.awaitReady();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for JDA to be ready", e);
        }
    }

    static net.dv8tion.jda.api.entities.Activity toActivity(Activity activity) {
        if (activity == null) {
            return null;
        }
        return switch (activity.type()) {
            case PLAYING -> net.dv8tion.jda.api.entities.Activity.playing(activity.text());
            case LISTENING -> net.dv8tion.jda.api.entities.Activity.listening(activity.text());
            case WATCHING -> net.dv8tion.jda.api.entities.Activity.watching(activity.text());
            case COMPETING -> net.dv8tion.jda.api.entities.Activity.competing(activity.text());
        };
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
