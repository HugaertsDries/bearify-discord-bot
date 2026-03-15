# controller

The Discord-facing application. It receives user interactions, resolves feature-specific domain objects, and communicates with `player` instances over Redis.

## Structure

- `music`: music-specific domain, Discord entry points, and Redis-backed player implementations.
- `misc`: lightweight commands that do not justify a larger feature boundary.

## Running

```bash
DISCORD_CONTROLLER_TOKEN=your-token \
DISCORD_GUILD_ID=your-guild-id \
REDIS_HOST=localhost \
REDIS_PORT=6379 \
./gradlew :controller:bootRun
```

## Configuration

| Property | Required | Description |
|---|---|---|
| `discord.token` | Yes | Bot token from the Discord Developer Portal |
| `discord.guild-id` | No | Guild ID for instant command registration in development |
| `spring.data.redis.host` | Yes | Redis host |
| `spring.data.redis.port` | Yes | Redis port |
