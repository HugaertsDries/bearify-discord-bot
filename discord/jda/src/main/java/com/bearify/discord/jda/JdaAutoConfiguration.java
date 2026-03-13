package com.bearify.discord.jda;

import com.bearify.discord.api.gateway.DiscordClientFactory;
import net.dv8tion.jda.api.JDA;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that registers a JDA-backed {@link DiscordClientFactory} bean.
 * Runs before the discord-starter configuration so the factory is ready to be injected.
 */
@AutoConfiguration
@AutoConfigureBefore(name = "com.bearify.discord.spring.DiscordAutoConfiguration")
@ConditionalOnClass(JDA.class)
public class JdaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DiscordClientFactory.class)
    public DiscordClientFactory jdaDiscordClientFactory() {
        return new JdaDiscordClientFactory();
    }
}
