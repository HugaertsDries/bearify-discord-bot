package com.bearify.discord.jda;

import com.bearify.discord.api.gateway.Activity;
import org.junit.jupiter.api.Test;

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
}
