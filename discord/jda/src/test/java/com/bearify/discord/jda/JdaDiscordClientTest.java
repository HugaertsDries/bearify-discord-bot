package com.bearify.discord.jda;

import com.bearify.discord.api.gateway.Activity;
import com.bearify.discord.api.model.OptionDefinition;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class JdaDiscordClientTest {

    @Test
    void mapsPlayingActivity() {
        net.dv8tion.jda.api.entities.Activity activity = JdaDiscordClient.toActivity(new Activity(Activity.Type.PLAYING, "with slash commands"));

        assertThat(activity).isEqualTo(net.dv8tion.jda.api.entities.Activity.playing("with slash commands"));
    }

    @Test
    void mapsListeningActivity() {
        net.dv8tion.jda.api.entities.Activity activity = JdaDiscordClient.toActivity(new Activity(Activity.Type.LISTENING, "your queue"));

        assertThat(activity).isEqualTo(net.dv8tion.jda.api.entities.Activity.listening("your queue"));
    }

    @Test
    void returnsNullWhenActivityIsAbsent() {
        assertThat(JdaDiscordClient.toActivity(null)).isNull();
    }

    @Test
    void mapsAutocompleteFlagToJdaOptionData() throws Exception {
        JdaDiscordClient client = new JdaDiscordClient(java.util.List.of(), interaction -> {}, null);
        OptionDefinition option = new OptionDefinition(
                "search",
                "Search",
                OptionDefinition.OptionType.STRING,
                true,
                true
        );

        Method method = JdaDiscordClient.class.getDeclaredMethod("toOptionData", OptionDefinition.class);
        method.setAccessible(true);

        net.dv8tion.jda.api.interactions.commands.build.OptionData optionData =
                (net.dv8tion.jda.api.interactions.commands.build.OptionData) method.invoke(client, option);

        assertThat(optionData.isAutoComplete()).isTrue();
    }
}
