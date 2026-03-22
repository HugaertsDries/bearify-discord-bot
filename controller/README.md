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
REDIS_USERNAME= \
REDIS_PASSWORD= \
./gradlew :controller:bootRun
```

## Commands

### `/player join`
Lures a bear into your voice channel. Acquires a free player agent from the pool.

### `/player leave`
Sends the bear home. Disconnects the player from your voice channel.

### `/player play <search>`
Plays a track or searches YouTube. Accepts a track name, URL, or search term. Auto-joins your channel if no player is already there.

### `/player pause`
Toggles pause/resume on the currently playing track.

### `/player next`
Skips to the next track in the queue.

### `/player previous`
Goes back to the previous track. If the current track is more than 3 seconds in, it restarts it instead.

### `/player rewind [seconds]`
Rewinds by the given number of seconds (default: 10s for tracks under 5 minutes, 30s for longer ones).

### `/player forward [seconds]`
Fast-forwards by the given number of seconds (default: 10s for tracks under 5 minutes, 30s for longer ones).

## Configuration

| Property | Required | Description |
|---|---|---|
| `discord.token` | Yes | Bot token from the Discord Developer Portal |
| `discord.guild-id` | No | Guild ID for instant command registration in development |
| `spring.data.redis.host` | Yes | Redis host |
| `spring.data.redis.port` | Yes | Redis port |
| `spring.data.redis.username` | No | Redis username when the server requires auth |
| `spring.data.redis.password` | No | Redis password when the server requires auth |
