# controller

The main Discord bot application. Receives slash commands from users and sends `PlayerCommand`s to player agents over Redis.

## Purpose
Acts as the user-facing side of the bot. Handles all Discord interactions and delegates audio playback to one or more `player` instances.

## Running

```bash
DISCORD_TOKEN=your-token \
DISCORD_GUILD_ID=your-guild-id \
REDIS_HOST=localhost \
REDIS_PORT=6379 \
./gradlew :controller:bootRun
```

## Configuration

| Property | Required | Description |
|---|---|---|
| `discord.token` | Yes | Bot token from the Discord Developer Portal |
| `discord.guild-id` | No | Guild ID for instant command registration (dev only) |
| `spring.data.redis.host` | Yes | Redis host |
| `spring.data.redis.port` | Yes | Redis port |

## Adding commands

```java
@Command
public class MyCommand {

    @Interaction(value = "hello", description = "Say hello")
    public void hello(CommandInteraction interaction) {
        interaction.reply("Hello!").send();
    }
}
```
