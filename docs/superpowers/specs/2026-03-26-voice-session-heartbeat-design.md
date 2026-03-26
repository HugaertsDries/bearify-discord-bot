# Voice Session Heartbeat Design

## Goal

Make a music-player agent leave a voice session automatically after a configurable amount of time with no non-bot users present in the channel.

The design should fit the existing agent architecture without introducing a second periodic maintenance concept next to `AssignmentHeartbeat`.

## Summary

Replace `AssignmentHeartbeat` with a broader `VoiceSessionHeartbeat` component.

`VoiceSessionHeartbeat` remains a scheduled maintenance loop over active guilds, but its responsibility expands from Redis assignment renewal to voice-session maintenance:

- renew the Redis assignment TTL for connected sessions
- track when a session becomes lonely
- disconnect through `VoiceConnectionManager` once loneliness exceeds the configured timeout

This keeps one periodic maintenance concept for active voice sessions instead of splitting the behavior across a heartbeat and a separate watchdog.

## Requirements

- If the bot is connected to a voice channel and there are no non-bot users in that channel, start a loneliness timer for that guild.
- If a non-bot user rejoins before the timeout expires, cancel the pending lonely leave.
- If the bot remains lonely for at least the configured timeout, disconnect it from that guild.
- Disconnecting due to loneliness must reuse the existing `VoiceConnectionManager.disconnect(guildId)` flow.
- Redis assignment renewal must continue to work exactly as it does today while the bot is connected.
- The timeout must be configurable through `music-player` properties.
- The check cadence must be configurable through `music-player` properties.
- Leaving due to loneliness does not need to announce anything in text channels in this iteration.
- The timeout applies even when music is currently playing.

## Non-Goals

- No text-channel announcement when the lonely timeout fires.
- No new Discord gateway event abstraction for voice-presence updates.
- No refactor of `AudioPlayer.previous()` or the `justRestarted` behavior as part of this change.
- No generic rule engine or pluggable maintenance framework.

## Existing Context

Current behavior is split across these pieces:

- `VoiceConnectionManager` owns connect, claim, disconnect, and shutdown behavior.
- `AssignmentHeartbeat` periodically renews Redis assignment TTLs for active guilds.
- `VoiceSession.isLonely()` already exists and is implemented in the JDA adapter.
- `AudioPlayerPool.activeGuildIds()` provides the set of guilds currently served by the agent.

Today there is no background process that reacts to prolonged loneliness. The agent only leaves on explicit disconnect or shutdown.

## Proposed Design

### Component

Replace `AssignmentHeartbeat` with `VoiceSessionHeartbeat` in the agent domain.

`VoiceSessionHeartbeat` is responsible for periodic maintenance of active voice sessions. It should be the only scheduled component concerned with current guild voice-session state.

Dependencies:

- `AudioPlayerPool`
- `DiscordClient`
- `StringRedisTemplate`
- `PlayerProperties`
- `VoiceConnectionManager`

### Configuration

Extend `PlayerProperties` with a new nested configuration block:

- `music-player.voice-session.heartbeat-interval`
- `music-player.voice-session.lonely-timeout`

Recommended defaults:

- `heartbeat-interval = 10s`
- `lonely-timeout = 5m`

Validation rules:

- `lonely-timeout` must be greater than `Duration.ZERO`
- `heartbeat-interval` must be greater than `Duration.ZERO`

No requirement is added that `lonely-timeout > heartbeat-interval`. A larger interval than timeout is allowed, with the understanding that effective leave time is rounded up to the next heartbeat tick.

The existing assignment configuration remains unchanged:

- `music-player.assignment.ttl`

This keeps Redis lease semantics independent from session-maintenance cadence.

### State

`VoiceSessionHeartbeat` keeps in-memory lonely tracking per guild:

- `Map<String, LonelyState>` or `Map<String, Instant>`

The minimum required state is:

- guild id
- first observed lonely timestamp
- optionally the channel id the timestamp belongs to

Tracking should be reset when:

- the guild has no active voice session
- the session is no longer lonely
- the connected channel changes
- the guild is disconnected

Including channel id in the tracked state is preferred because it makes channel migration explicit and prevents loneliness observed in one channel from carrying over to another.

### Tick Flow

On each scheduled tick:

1. Iterate `pool.activeGuildIds()`.
2. Read `client.guild(guildId).voice()`.
3. If no session is present:
   - clear lonely tracking for that guild
   - continue
4. If a session is present:
   - renew the Redis assignment TTL for `assignment(guildId, channelId)`
5. If the session is not lonely:
   - clear lonely tracking for that guild
   - continue
6. If the session is lonely:
   - initialize lonely tracking if absent
   - if the tracked channel differs from the current channel, reset tracking to the current channel and current time
   - if the elapsed lonely duration is still below timeout, continue
   - re-read current session state before disconnecting
   - if the session is still present, still on the same channel, and still lonely, call `voiceConnectionManager.disconnect(guildId)`
   - clear lonely tracking for that guild

The disconnect path must not perform partial cleanup in the heartbeat itself. All cleanup and event dispatch should continue to flow through `VoiceConnectionManager.disconnect`.

## Error Handling

The heartbeat should be defensive and fail per guild rather than aborting the entire sweep.

If inspecting one guild throws unexpectedly:

- log the guild id and continue with the next guild
- do not lose loneliness tracking for unrelated guilds

If Redis TTL renewal fails:

- log the failure
- continue the sweep

If `disconnect(guildId)` fails unexpectedly:

- log the failure
- clear or preserve lonely state based on the failure mode

Preferred behavior after disconnect failure:

- preserve lonely tracking so the next heartbeat can retry

This avoids a stuck lonely session remaining connected forever because one disconnect attempt threw.

## Concurrency and Race Conditions

### Rejoin race

A user may rejoin between ticks. The recorded lonely timestamp is only evidence of elapsed time, not enough to justify a disconnect by itself.

For that reason, the tick that crosses the timeout must re-check that:

- a voice session still exists
- the channel is still the tracked channel
- `isLonely()` is still `true`

Only then may it disconnect.

### Channel migration

The bot can move channels through an explicit connect or claim flow while remaining in the same guild.

Lonely tracking must reset when channel id changes. Otherwise loneliness observed in the old channel could incorrectly force a leave in the new channel.

### Disconnect idempotency

`VoiceConnectionManager.disconnect(guildId)` is already written to tolerate repeated calls in practice. The heartbeat should still clear lonely tracking after a successful disconnect to avoid redundant attempts.

### Multi-thread visibility

The heartbeat is the only component that should mutate lonely-tracking state. Other components should continue to use existing connection and disconnect APIs rather than reaching into that state.

This keeps synchronization needs small and localized.

## Testing Strategy

Add focused tests around the replacement heartbeat component.

Required tests:

- renews assignment TTL when a voice session is active
- does not renew assignment TTL when no voice session is active
- starts lonely tracking when a session becomes lonely
- does not disconnect before the lonely timeout expires
- disconnects once loneliness exceeds the timeout
- clears lonely tracking when a non-bot user returns
- resets lonely tracking when the connected channel changes
- clears lonely tracking when the session disappears
- retries disconnect on a later tick if a prior disconnect attempt failed

Existing `VoiceConnectionManagerTest` patterns can be reused for fake guild/session behavior, especially the existing fake support for `isLonely()`.

The previous `AssignmentHeartbeatTest` should be replaced or renamed to cover the broader session-maintenance behavior.

## Open TODO Review

Three open TODO comments exist in the music-player agent.

### `LavaAudioEngine`: nullable `getPlayingTrack()`

This should be handled separately from the lonely-leave work.

It is a good cleanup and already has a concrete design in `docs/superpowers/specs/2026-03-25-quick-wins-design.md`, but it does not affect session-maintenance behavior. It can be done in a separate commit on the same branch if desired.

### `AudioPlayerPool`: inner-builder pattern

This should not be part of the lonely-leave work.

The current constructor call is slightly long, but an inner builder would likely add more ceremony than value. If this area becomes harder to read later, prefer a small private factory method rather than a builder abstraction.

### `AudioPlayer`: `justRestarted`

This should also stay out of scope.

That TODO points to playback semantics in `previous()`, not session maintenance. It needs its own targeted review because changing it can alter user-visible playback behavior.

## Migration Plan

Implementation should proceed in this order:

1. Extend `PlayerProperties` with voice-session heartbeat settings.
2. Replace `AssignmentHeartbeat` with `VoiceSessionHeartbeat`.
3. Preserve assignment TTL renewal behavior inside the new component.
4. Add lonely tracking and timeout-based disconnect behavior.
5. Update or replace tests for the new component.

## Decision

Adopt a single `VoiceSessionHeartbeat` abstraction rather than adding a second periodic maintenance concept.

This keeps the architecture coherent:

- one scheduled loop for active voice-session maintenance
- one connection manager for connect and disconnect side effects
- one Redis assignment lease model

The result is a configurable, low-risk lonely-leave behavior that follows existing boundaries and avoids unnecessary new abstractions.
