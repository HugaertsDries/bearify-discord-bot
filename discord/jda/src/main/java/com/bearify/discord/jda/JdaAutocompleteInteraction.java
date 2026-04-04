package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.Option;
import com.bearify.discord.api.interaction.AutocompleteInteraction;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.util.List;
import java.util.Optional;

/**
 * Wraps a JDA autocomplete interaction as the compact application model.
 */
class JdaAutocompleteInteraction implements AutocompleteInteraction {

    private final CommandAutoCompleteInteractionEvent event;

    JdaAutocompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        this.event = event;
    }

    @Override
    public String getId() {
        String command = event.getName();
        String option = event.getFocusedOption().getName();
        return Optional.ofNullable(event.getSubcommandName())
                .filter(name -> !name.isBlank())
                .map(subcommand -> command + ":" + subcommand + ":" + option)
                .orElse(command + ":" + option);
    }

    @Override
    public String getValue() {
        return event.getFocusedOption().getValue();
    }

    @Override
    public void reply(List<Option> choices) {
        List<Command.Choice> mapped = choices.stream()
                .map(choice -> new Command.Choice(choice.name(), choice.value()))
                .toList();
        event.replyChoices(mapped).queue();
    }

    @Override
    public Optional<String> getGuildId() {
        return Optional.ofNullable(event.getGuild()).map(net.dv8tion.jda.api.entities.Guild::getId);
    }

    @Override
    public Optional<String> getVoiceChannelId() {
        return Optional.ofNullable(event.getMember())
                .map(Member::getVoiceState)
                .filter(GuildVoiceState::inAudioChannel)
                .flatMap(vs -> Optional.ofNullable(vs.getChannel()).map(ISnowflake::getId));
    }

    @Override
    public Optional<String> getTextChannelId() {
        return Optional.ofNullable(event.getChannel()).map(ISnowflake::getId);
    }
}
