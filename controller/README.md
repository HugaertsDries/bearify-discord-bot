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

Every `/player` command answers only once the bear has confirmed it, or says the bear didn't answer in time. A player button that fails answers privately to whoever pressed it.

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

### `/player clear`
Clears the current playlist.

### `/player remove <track>`
Drops one upcoming track. While you type, it suggests the queued tracks matching your text; a bare number removes the track at that position. If the queue moved since you picked, the bear still removes the track you picked, or says it's gone.

### `/player shuffle`
Mixes up the upcoming tracks once. The track playing now stays.

### `/player queue`
Shows you, privately, the whole queue as a card in the player's Up Next style: the track playing now, up to 10 rows, then "…and N more", and the track count and total time. When more than 3 tracks are queued, the player's Up Next ends with "+N more on the trail" and a **Show** button that privately lists the tracks the player doesn't show.

### `/player volume <level>`
Sets the bear's volume from 0 to 100 for everyone in the channel. The player shows it next to the requester. The bear starts every visit at `music-player.default-volume` (100).

### `/poke [pokes]`
Pokes the bear a number of times (default: 4) and reports the response latency of each poke.

### `/dev playback-preview [preset]`
Posts a playback component preview in the current text channel. Presets: `broadcast` (default), `just-skipped`, `heavy-queue`. Only registered when the `dev` profile is active.

## Configuration

| Property | Required | Description |
|---|---|---|
| `discord.token` | Yes | Bot token from the Discord Developer Portal |
| `discord.guild-id` | No | Guild ID for instant command registration in development |
| `spring.data.redis.host` | Yes | Redis host |
| `spring.data.redis.port` | Yes | Redis port |
| `spring.data.redis.username` | No | Redis username when the server requires auth |
| `spring.data.redis.password` | No | Redis password when the server requires auth |
| `bearify.health.channel-id` | No | Private text channel (env `BEARIFY_HEALTH_CHANNEL_ID`) where the controller posts once when YouTube playback breaks or no bear answers the health check, and once when that clears. The bot needs View Channel and Send Messages there. Blank turns the check off. |
