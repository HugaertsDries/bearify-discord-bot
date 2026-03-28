package com.bearify.discord.jda;

import com.bearify.discord.api.gateway.Activity;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.DiscordClientFactory;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.model.CommandDefinition;

import java.util.List;
import java.util.function.Consumer;

/**
 * Creates {@link JdaDiscordClient} instances with all configuration baked in at construction time.
 */
class JdaDiscordClientFactory implements DiscordClientFactory {

    @Override
    public DiscordClient create(List<CommandDefinition> commands,
                                Consumer<CommandInteraction> handler) {
        return new JdaDiscordClient(commands, handler, null);
    }

    @Override
    public DiscordClient create(List<CommandDefinition> commands,
                                Consumer<CommandInteraction> handler,
                                Activity activity) {
        return new JdaDiscordClient(commands, handler, activity);
    }
}
