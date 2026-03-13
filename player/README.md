# player

The audio player agent. Runs as a separate Spring Boot application, listens for `PlayerCommand`s over Redis, and plays audio in a Discord voice channel using LavaPlayer.

## Purpose
Handles all audio playback. Multiple player instances can run simultaneously, each handling a separate voice channel. Players self-register in Redis on startup so the controller can discover them.

## Running

```bash
DISCORD_TOKEN=your-player-token \
PLAYER_ID=player-1 \
REDIS_HOST=localhost \
REDIS_PORT=6379 \
./gradlew :player:bootRun
```

## Configuration

| Property | Required | Description |
|---|---|---|
| `discord.token` | Yes | Bot token for this player instance |
| `player.id` | Yes | Unique identifier for this player |
| `spring.data.redis.host` | Yes | Redis host |
| `spring.data.redis.port` | Yes | Redis port |

## Notes
- Each player requires its own Discord bot token
- Players communicate with the controller exclusively via Redis pub/sub
- See `shared` module for the `PlayerCommand` and `PlayerEvent` types
