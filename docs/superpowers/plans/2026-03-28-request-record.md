# Request Record Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the paired `(String requestId, String requesterTag)` parameters with a single `Request(id, requesterTag)` record throughout `MusicPlayerInteraction`, `MusicPlayerEvent`, and `AudioPlayer`.

**Architecture:** Three sequential tasks — create the record, migrate the interaction layer (bridge + agent method signatures + controller construction), then migrate the event layer (bridge + agent dispatch + controller consumer). Both sides of the Redis wire format change together in a single deployment unit, so no backwards-compatibility is needed.

**Tech Stack:** Java 25, Spring Boot 3.4.3, Jackson 2.x (auto-handles Java records), JUnit 5, AssertJ

---

## File Map

| File | Task | Change |
|---|---|---|
| `music-player/bridge/src/main/java/com/bearify/music/player/bridge/model/Request.java` | 1 | **Create** |
| `music-player/bridge/src/main/java/com/bearify/music/player/bridge/events/MusicPlayerInteraction.java` | 2 | Replace two String fields with `Request` on 6 records |
| `music-player/agent/src/main/java/com/bearify/music/player/agent/domain/AudioPlayer.java` | 2, 3 | Change 6 method signatures (Task 2); update event dispatch (Task 3) |
| `music-player/agent/src/main/java/com/bearify/music/player/agent/port/MusicPlayerInteractionDispatcher.java` | 2 | Update switch arms |
| `controller/src/main/java/com/bearify/controller/music/redis/RedisMusicPlayer.java` | 2 | Update interaction construction |
| `music-player/agent/src/test/java/com/bearify/music/player/agent/domain/AudioPlayerTest.java` | 2, 3 | Update call sites (Task 2); update event assertions (Task 3) |
| `music-player/agent/src/test/java/com/bearify/music/player/agent/port/MusicPlayerInteractionDispatcherTest.java` | 2 | Update interaction constructions + StubPlayer overrides |
| `music-player/bridge/src/main/java/com/bearify/music/player/bridge/events/MusicPlayerEvent.java` | 3 | Replace two String fields with `Request` on 8 records |
| `controller/src/main/java/com/bearify/controller/music/discord/TextChannelMusicPlayerTrackAnnouncer.java` | 3 | `.requesterTag()` → `.request().requesterTag()` |
| `controller/src/test/java/com/bearify/controller/music/discord/TextChannelMusicPlayerTrackAnnouncerTest.java` | 3 | Update event constructions |
| `controller/src/test/java/com/bearify/controller/music/discord/MusicPlayerInteractionIntegrationTest.java` | 3 | Update event constructions |

---

### Task 1: Add `Request` record to bridge module

**Files:**
- Create: `music-player/bridge/src/main/java/com/bearify/music/player/bridge/model/Request.java`

- [ ] **Step 1: Create the record**

```java
package com.bearify.music.player.bridge.model;

public record Request(String id, String requesterTag) {}
```

- [ ] **Step 2: Build the bridge module to confirm it compiles**

```bash
JAVA_HOME="/c/Users/dries/.jdks/openjdk-25.0.2" ./gradlew :music-player:bridge:build -q
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add music-player/bridge/src/main/java/com/bearify/music/player/bridge/model/Request.java
git commit -m "add Request record to bridge"
```

---

### Task 2: Migrate `MusicPlayerInteraction` records and all consumers

Six records swap `(requestId, requesterTag)` → `Request request`. The six `AudioPlayer` method signatures change. Tests updated first.

**Files:**
- Modify: `music-player/agent/src/test/java/com/bearify/music/player/agent/domain/AudioPlayerTest.java`
- Modify: `music-player/agent/src/test/java/com/bearify/music/player/agent/port/MusicPlayerInteractionDispatcherTest.java`
- Modify: `music-player/bridge/src/main/java/com/bearify/music/player/bridge/events/MusicPlayerInteraction.java`
- Modify: `music-player/agent/src/main/java/com/bearify/music/player/agent/domain/AudioPlayer.java`
- Modify: `music-player/agent/src/main/java/com/bearify/music/player/agent/port/MusicPlayerInteractionDispatcher.java`
- Modify: `controller/src/main/java/com/bearify/controller/music/redis/RedisMusicPlayer.java`

- [ ] **Step 1: Update `AudioPlayerTest` call sites** (keep event assertions unchanged — those change in Task 3)

In `AudioPlayerTest.java`, add import at the top:

```java
import com.bearify.music.player.bridge.model.Request;
```

Then replace every direct call to a player action method. All occurrences of `"req-1"` and `"@user"` as two separate args become a single `new Request(...)`:

```java
// pausesPlaybackWhenPlaying and resumesPlaybackWhenPaused
player.togglePause(new Request("req-1", "@user"));

// advancesToNextTrackInQueue, nextUnpausesWhenAdvancingToDifferentTrack
player.next(new Request("req-1", "@user"));

// goesToPreviousTrackOnSecondPreviousAfterRestart — two previous calls
player.next(new Request("req-1", "@alice"));     // setup
player.previous(new Request("req-1", "@user"));  // first
player.previous(new Request("req-2", "@user"));  // second

// goesToPreviousTrackWhenNearStart
player.next(new Request("req-1", "@alice"));
player.previous(new Request("req-1", "@user"));

// previousUnpausesWhenSwitchingToEarlierTrack
player.next(new Request("req-1", "@alice"));
player.previous(new Request("req-2", "@user"));

// restartsCurrentTrackWhenPastThreshold
player.previous(new Request("req-1", "@user"));

// seeksForwardByShortDefaultOnShortTrack, seeksForwardByLongDefaultOnLongTrack, forwardPreservesPausedStateWhenSeekingWithinTrack
player.forward(Duration.ZERO, new Request("req-1", "@user"));

// seeksForwardBySpecifiedAmount
player.forward(Duration.ofMillis(30_000), new Request("req-1", "@user"));

// seeksBackwardByDefaultAmount
player.rewind(Duration.ZERO, new Request("req-1", "@user"));

// seeksBackwardBySpecifiedAmount
player.rewind(Duration.ofMillis(15_000), new Request("req-1", "@user"));

// rewindPreservesPausedState
player.rewind(Duration.ofMillis(15_000), new Request("req-1", "@user"));

// clearsQueueAndEmitsClearedEvent
player.clear(new Request("req-1", "@user"));

// doesNotEmitQueueEmptyWhenNextFailsButCurrentTrackKeepsPlaying
player.next(new Request("req-1", "@user"));

// rejectsPreviousWhenHistoryIsEmptyAndNearStart
player.previous(new Request("req-1", "@user"));
```

Leave ALL event assertions (`.isEqualTo(new MusicPlayerEvent.Paused(...))` etc.) unchanged — they still use the old 4-arg form for now.

- [ ] **Step 2: Update `MusicPlayerInteractionDispatcherTest`**

Add import:

```java
import com.bearify.music.player.bridge.model.Request;
```

Update the six interaction constructions from flat-string form to `Request`:

```java
// routesTogglePauseToPlayer
dispatcher.handle(new MusicPlayerInteraction.TogglePause(PLAYER_ID, new Request(REQUEST_ID, "@user"), GUILD_ID));

// routesNextToPlayer
dispatcher.handle(new MusicPlayerInteraction.Next(PLAYER_ID, new Request(REQUEST_ID, "@user"), GUILD_ID));

// routesPreviousToPlayer
dispatcher.handle(new MusicPlayerInteraction.Previous(PLAYER_ID, new Request(REQUEST_ID, "@user"), GUILD_ID));

// routesRewindToPlayer
dispatcher.handle(new MusicPlayerInteraction.Rewind(PLAYER_ID, new Request(REQUEST_ID, "@user"), GUILD_ID, 15000));

// routesForwardToPlayer
dispatcher.handle(new MusicPlayerInteraction.Forward(PLAYER_ID, new Request(REQUEST_ID, "@user"), GUILD_ID, 30000));
```

Update `StubPlayer` overrides to match the new signatures:

```java
@Override
public void togglePause(Request request) {
    togglePauseCalls.add(guildId + "|" + request.requesterTag());
}

@Override
public void next(Request request) {
    nextCalls.add(guildId + "|" + request.requesterTag());
}

@Override
public void previous(Request request) {
    previousCalls.add(guildId + "|" + request.requesterTag());
}

@Override
public void rewind(Duration seek, Request request) {
    rewindCalls.add(seek.toMillis() + "|" + guildId + "|" + request.requesterTag());
}

@Override
public void forward(Duration seek, Request request) {
    forwardCalls.add(seek.toMillis() + "|" + guildId + "|" + request.requesterTag());
}

@Override
public void clear(Request request) {
}
```

- [ ] **Step 3: Run tests — confirm compilation fails**

```bash
JAVA_HOME="/c/Users/dries/.jdks/openjdk-25.0.2" ./gradlew :music-player:agent:test -q 2>&1 | head -30
```

Expected: compilation error — `togglePause(String, String)` not found (tests now call `togglePause(Request)` but production still has two-String signature).

- [ ] **Step 4: Update `MusicPlayerInteraction.java` — replace two String fields with `Request` on 6 records**

Add import after the existing imports:

```java
import com.bearify.music.player.bridge.model.Request;
```

Replace the six records (keep Javadoc comments on Rewind/Forward):

```java
record TogglePause(String playerId, Request request, String guildId) implements MusicPlayerInteraction {
    @Override public String requestId() { return request.id(); }
}
record Next(String playerId, Request request, String guildId) implements MusicPlayerInteraction {
    @Override public String requestId() { return request.id(); }
}
record Previous(String playerId, Request request, String guildId) implements MusicPlayerInteraction {
    @Override public String requestId() { return request.id(); }
}
/** seekMs of 0 means use the player's configured default. */
record Rewind(String playerId, Request request, String guildId, long seekMs) implements MusicPlayerInteraction {
    @Override public String requestId() { return request.id(); }
}
/** seekMs of 0 means use the player's configured default. */
record Forward(String playerId, Request request, String guildId, long seekMs) implements MusicPlayerInteraction {
    @Override public String requestId() { return request.id(); }
}
record Clear(String playerId, Request request, String guildId) implements MusicPlayerInteraction {
    @Override public String requestId() { return request.id(); }
}
```

`Play`, `Stop`, and `Connect` remain unchanged.

- [ ] **Step 5: Update `AudioPlayer.java` — change 6 method signatures**

Add import:

```java
import com.bearify.music.player.bridge.model.Request;
```

Remove the TODO comment on line 83. Change each method signature and update internal usages:

```java
public void togglePause(Request request) {
    boolean nowPaused = !engine.isPaused();
    engine.setPaused(nowPaused);
    if (nowPaused) {
        eventDispatcher.dispatch(new MusicPlayerEvent.Paused(playerId, request.id(), guildId, request.requesterTag()));
    } else {
        eventDispatcher.dispatch(new MusicPlayerEvent.Resumed(playerId, request.id(), guildId, request.requesterTag()));
    }
}

public synchronized void next(Request request) {
    if (queue.isEmpty()) {
        eventDispatcher.dispatch(new MusicPlayerEvent.NothingToAdvance(playerId, request.id(), guildId));
        return;
    }
    Track current = engine.getPlayingTrack();
    if (current != null) {
        history.addFirst(current.clone());
    }
    engine.setPaused(false);
    engine.play(queue.pollFirst());
    eventDispatcher.dispatch(new MusicPlayerEvent.Skipped(playerId, request.id(), guildId, request.requesterTag()));
}

public synchronized void previous(Request request) {
    Track current = engine.getPlayingTrack();
    // TODO why is this justRestarted needed? Why is the second validation not enough?
    if (!justRestarted && current != null && current.position() > properties.previousRestartThreshold().toMillis()) {
        current.setPosition(0);
        justRestarted = true;
        return;
    }
    justRestarted = false;
    if (history.isEmpty()) {
        eventDispatcher.dispatch(new MusicPlayerEvent.NothingToGoBack(playerId, request.id(), guildId));
        return;
    }
    if (current != null) {
        queue.addFirst(current.clone());
    }
    engine.setPaused(false);
    engine.play(history.pollFirst());
    eventDispatcher.dispatch(new MusicPlayerEvent.WentBack(playerId, request.id(), guildId, request.requesterTag()));
}

public void rewind(Duration seek, Request request) {
    Track track = engine.getPlayingTrack();
    if (track == null) return;
    long effectiveSeekMs = seek.isZero() ? defaultSeekMs(track) : seek.toMillis();
    long newPosition = Math.max(0, track.position() - effectiveSeekMs);
    track.setPosition(newPosition);
    eventDispatcher.dispatch(new MusicPlayerEvent.Rewound(playerId, request.id(), guildId, request.requesterTag(), effectiveSeekMs));
}

public synchronized void forward(Duration seek, Request request) {
    Track track = engine.getPlayingTrack();
    if (track == null) return;
    long effectiveSeekMs = seek.isZero() ? defaultSeekMs(track) : seek.toMillis();
    long newPosition = track.position() + effectiveSeekMs;
    if (newPosition >= track.duration()) {
        if (queue.isEmpty()) {
            eventDispatcher.dispatch(new MusicPlayerEvent.NothingToAdvance(playerId, request.id(), guildId));
            return;
        } else {
            boolean wasPaused = engine.isPaused();
            history.addFirst(track.clone());
            engine.play(queue.pollFirst());
            if (wasPaused) {
                eventDispatcher.dispatch(new MusicPlayerEvent.Paused(playerId, request.id(), guildId, request.requesterTag()));
            }
        }
    } else {
        track.setPosition(newPosition);
    }
    eventDispatcher.dispatch(new MusicPlayerEvent.Forwarded(playerId, request.id(), guildId, request.requesterTag(), effectiveSeekMs));
}

public synchronized void clear(Request request) {
    queue.clear();
    eventDispatcher.dispatch(new MusicPlayerEvent.Cleared(playerId, request.id(), guildId, request.requesterTag()));
}
```

- [ ] **Step 6: Update `MusicPlayerInteractionDispatcher.java` — pass `interaction.request()` to AudioPlayer**

No new import needed — `Request` is never written explicitly here, only passed through via `p.request()`.

Update the six switch arms (the `PlayerNotFound` dispatch paths use `p.requestId()` which still works via the `@Override` delegation):

```java
case MusicPlayerInteraction.TogglePause p ->
        pool.get(p.guildId()).ifPresentOrElse(
                player -> player.togglePause(p.request()),
                () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, p.requestId(), p.guildId())));
case MusicPlayerInteraction.Next n ->
        pool.get(n.guildId()).ifPresentOrElse(
                player -> player.next(n.request()),
                () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, n.requestId(), n.guildId())));
case MusicPlayerInteraction.Previous p ->
        pool.get(p.guildId()).ifPresentOrElse(
                player -> player.previous(p.request()),
                () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, p.requestId(), p.guildId())));
case MusicPlayerInteraction.Rewind r ->
        pool.get(r.guildId()).ifPresentOrElse(
                player -> player.rewind(Duration.ofMillis(r.seekMs()), r.request()),
                () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, r.requestId(), r.guildId())));
case MusicPlayerInteraction.Forward f ->
        pool.get(f.guildId()).ifPresentOrElse(
                player -> player.forward(Duration.ofMillis(f.seekMs()), f.request()),
                () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, f.requestId(), f.guildId())));
case MusicPlayerInteraction.Clear c ->
        pool.get(c.guildId()).ifPresent(player -> player.clear(c.request()));
```

- [ ] **Step 7: Update `RedisMusicPlayer.java` — wrap requestId + requesterTag into `Request` at construction**

Add import:

```java
import com.bearify.music.player.bridge.model.Request;
```

Update the six `new MusicPlayerInteraction.*` constructions in the `Connected` inner class:

```java
// togglePause (line ~253)
serialize(new MusicPlayerInteraction.TogglePause(playerId, new Request(p.requestId(), requesterTag), guildId))

// next (line ~270)
serialize(new MusicPlayerInteraction.Next(playerId, new Request(p.requestId(), requesterTag), guildId))

// previous (line ~286)
serialize(new MusicPlayerInteraction.Previous(playerId, new Request(p.requestId(), requesterTag), guildId))

// rewind (line ~301) — fire-and-forget, uses inline UUID
serialize(new MusicPlayerInteraction.Rewind(playerId, new Request(UUID.randomUUID().toString(), requesterTag), guildId, seek.toMillis()))

// forward (line ~309)
serialize(new MusicPlayerInteraction.Forward(playerId, new Request(p.requestId(), requesterTag), guildId, seek.toMillis()))

// clear (line ~324) — fire-and-forget, uses inline UUID
serialize(new MusicPlayerInteraction.Clear(playerId, new Request(UUID.randomUUID().toString(), requesterTag), guildId))
```

- [ ] **Step 8: Run all tests and confirm they pass**

```bash
JAVA_HOME="/c/Users/dries/.jdks/openjdk-25.0.2" ./gradlew build -q
```

Expected: BUILD SUCCESSFUL. The event assertions in `AudioPlayerTest` still use the old 4-String form — this is intentional and correct until Task 3.

- [ ] **Step 9: Commit**

```bash
git add \
  music-player/bridge/src/main/java/com/bearify/music/player/bridge/events/MusicPlayerInteraction.java \
  music-player/agent/src/main/java/com/bearify/music/player/agent/domain/AudioPlayer.java \
  music-player/agent/src/main/java/com/bearify/music/player/agent/port/MusicPlayerInteractionDispatcher.java \
  controller/src/main/java/com/bearify/controller/music/redis/RedisMusicPlayer.java \
  music-player/agent/src/test/java/com/bearify/music/player/agent/domain/AudioPlayerTest.java \
  music-player/agent/src/test/java/com/bearify/music/player/agent/port/MusicPlayerInteractionDispatcherTest.java
git commit -m "migrate MusicPlayerInteraction records to Request"
```

---

### Task 3: Migrate `MusicPlayerEvent` records and all consumers

Eight event records swap `(requestId, requesterTag)` → `Request request`. `AudioPlayer` event dispatch updated. Announcer and tests updated.

**Files:**
- Modify: `music-player/agent/src/test/java/com/bearify/music/player/agent/domain/AudioPlayerTest.java`
- Modify: `controller/src/test/java/com/bearify/controller/music/discord/TextChannelMusicPlayerTrackAnnouncerTest.java`
- Modify: `controller/src/test/java/com/bearify/controller/music/discord/MusicPlayerInteractionIntegrationTest.java`
- Modify: `music-player/bridge/src/main/java/com/bearify/music/player/bridge/events/MusicPlayerEvent.java`
- Modify: `music-player/agent/src/main/java/com/bearify/music/player/agent/domain/AudioPlayer.java`
- Modify: `controller/src/main/java/com/bearify/controller/music/discord/TextChannelMusicPlayerTrackAnnouncer.java`

- [ ] **Step 1: Update event assertions in `AudioPlayerTest.java`**

All event assertions use the old `(playerId, "req-1", guildId, "@user")` form. Replace with `(playerId, new Request("req-1", "@user"), guildId)`:

```java
// pausesPlaybackWhenPlaying
.isEqualTo(new MusicPlayerEvent.Paused(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID));

// resumesPlaybackWhenPaused
.isEqualTo(new MusicPlayerEvent.Resumed(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID));

// advancesToNextTrackInQueue
.contains(new MusicPlayerEvent.Skipped(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID));

// goesToPreviousTrackOnSecondPreviousAfterRestart
.contains(new MusicPlayerEvent.WentBack(PLAYER_ID, new Request("req-2", "@user"), GUILD_ID));

// goesToPreviousTrackWhenNearStart
.contains(new MusicPlayerEvent.WentBack(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID));

// seeksForwardByShortDefaultOnShortTrack
.isEqualTo(new MusicPlayerEvent.Forwarded(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 10_000));

// seeksForwardByLongDefaultOnLongTrack
.isEqualTo(new MusicPlayerEvent.Forwarded(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 30_000));

// seeksForwardBySpecifiedAmount
.isEqualTo(new MusicPlayerEvent.Forwarded(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 30_000));

// seeksBackwardByDefaultAmount
.isEqualTo(new MusicPlayerEvent.Rewound(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 10_000));

// seeksBackwardBySpecifiedAmount
.isEqualTo(new MusicPlayerEvent.Rewound(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID, 15_000));

// clearsQueueAndEmitsClearedEvent
.isEqualTo(new MusicPlayerEvent.Cleared(PLAYER_ID, new Request("req-1", "@user"), GUILD_ID));
```

- [ ] **Step 2: Update `TextChannelMusicPlayerTrackAnnouncerTest.java`**

Add import:

```java
import com.bearify.music.player.bridge.model.Request;
```

Update every `new MusicPlayerEvent.*` construction in the test:

```java
// trackStart helper (line ~179):
return new MusicPlayerEvent.TrackStart(playerId, new Request("req-1", "@user"), "guild-1",
        new TrackMetadata("Song", "Artist", "https://example.com", 60_000));

// acceptUpdatesEmbedWhenPaused (line ~48):
announcer.accept(new MusicPlayerEvent.Paused("player-1", new Request("req-2", "@user"), "guild-1"));

// acceptUpdatesEmbedWhenResumed (line ~60-61):
announcer.accept(new MusicPlayerEvent.Paused("player-1", new Request("req-2", "@user"), "guild-1"));
announcer.accept(new MusicPlayerEvent.Resumed("player-1", new Request("req-3", "@user"), "guild-1"));

// acceptShowsTemporarySkippedAction (line ~74):
announcer.accept(new MusicPlayerEvent.Skipped("player-1", new Request("req-2", "@user"), "guild-1"));

// acceptShowsTemporaryWentBackAction (line ~86):
announcer.accept(new MusicPlayerEvent.WentBack("player-1", new Request("req-2", "@user"), "guild-1"));

// acceptReplacesOlderTemporaryActionWithNewerOne (line ~98-99):
announcer.accept(new MusicPlayerEvent.Skipped("player-1", new Request("req-2", "@user"), "guild-1"));
announcer.accept(new MusicPlayerEvent.Cleared("player-1", new Request("req-3", "@other"), "guild-1"));

// acceptShowsForwardedAction (line ~111):
announcer.accept(new MusicPlayerEvent.Forwarded("player-1", new Request("req-2", "@user"), "guild-1", 30_000));
```

- [ ] **Step 3: Update `MusicPlayerInteractionIntegrationTest.java`**

Add import:

```java
import com.bearify.music.player.bridge.model.Request;
```

Update all `MusicPlayerEvent` constructions:

```java
// First TrackStart (line ~237):
new MusicPlayerEvent.TrackStart(
        PLAYER_ID,
        new Request("event-1", "@user"),
        GUILD_ID,
        new TrackMetadata("Song", "Artist", "https://example.com", 60_000))

// Second TrackStart (line ~266):
new MusicPlayerEvent.TrackStart(
        PLAYER_ID,
        new Request("event-1", "@user"),
        GUILD_ID,
        new TrackMetadata("Song", "Artist", "https://example.com", 60_000))

// Paused (line ~277):
new MusicPlayerEvent.Paused(PLAYER_ID, new Request("event-2", "@user"), GUILD_ID)

// Third TrackStart (line ~302):
new MusicPlayerEvent.TrackStart(
        PLAYER_ID,
        new Request("event-1", "@user"),
        GUILD_ID,
        new TrackMetadata("Song", "Artist", "https://example.com", 60_000))

// Skipped (line ~313):
new MusicPlayerEvent.Skipped(PLAYER_ID, new Request("event-2", "@user"), GUILD_ID)
```

- [ ] **Step 4: Run tests to confirm compilation fails**

```bash
JAVA_HOME="/c/Users/dries/.jdks/openjdk-25.0.2" ./gradlew :music-player:agent:test :controller:test -q 2>&1 | head -30
```

Expected: compilation errors — event records still have 4-String constructors but tests now call 3-arg `(playerId, Request, guildId)` form.

- [ ] **Step 5: Update `MusicPlayerEvent.java` — replace two String fields with `Request` on 8 records**

Add import:

```java
import com.bearify.music.player.bridge.model.Request;
```

Replace the eight records:

```java
record TrackStart(String playerId, Request request, String guildId, TrackMetadata track) implements MusicPlayerEvent {
    @Override public String requestId() { return request.id(); }
}
record Paused(String playerId, Request request, String guildId) implements MusicPlayerEvent {
    @Override public String requestId() { return request.id(); }
}
record Resumed(String playerId, Request request, String guildId) implements MusicPlayerEvent {
    @Override public String requestId() { return request.id(); }
}
record Skipped(String playerId, Request request, String guildId) implements MusicPlayerEvent {
    @Override public String requestId() { return request.id(); }
}
record WentBack(String playerId, Request request, String guildId) implements MusicPlayerEvent {
    @Override public String requestId() { return request.id(); }
}
record Rewound(String playerId, Request request, String guildId, long seekMs) implements MusicPlayerEvent {
    @Override public String requestId() { return request.id(); }
}
record Forwarded(String playerId, Request request, String guildId, long seekMs) implements MusicPlayerEvent {
    @Override public String requestId() { return request.id(); }
}
record Cleared(String playerId, Request request, String guildId) implements MusicPlayerEvent {
    @Override public String requestId() { return request.id(); }
}
```

`TrackEnd`, `TrackError`, `QueueEmpty`, `NothingToAdvance`, `NothingToGoBack`, `TrackNotFound`, `TrackLoadFailed`, `Ready`, `Stopped`, `ConnectFailed`, `PlayerNotFound` remain unchanged.

- [ ] **Step 6: Update `AudioPlayer.java` — update event dispatch inside methods + `TrackEventHandler`**

In each method, remove the `request.id()` / `request.requesterTag()` unpacking — pass `request` directly:

```java
// togglePause:
eventDispatcher.dispatch(new MusicPlayerEvent.Paused(playerId, request, guildId));
// else:
eventDispatcher.dispatch(new MusicPlayerEvent.Resumed(playerId, request, guildId));

// next — NothingToAdvance still uses plain requestId (record unchanged):
eventDispatcher.dispatch(new MusicPlayerEvent.NothingToAdvance(playerId, request.id(), guildId));
// skip:
eventDispatcher.dispatch(new MusicPlayerEvent.Skipped(playerId, request, guildId));

// previous — NothingToGoBack unchanged:
eventDispatcher.dispatch(new MusicPlayerEvent.NothingToGoBack(playerId, request.id(), guildId));
// went back:
eventDispatcher.dispatch(new MusicPlayerEvent.WentBack(playerId, request, guildId));

// rewind:
eventDispatcher.dispatch(new MusicPlayerEvent.Rewound(playerId, request, guildId, effectiveSeekMs));

// forward — NothingToAdvance unchanged:
eventDispatcher.dispatch(new MusicPlayerEvent.NothingToAdvance(playerId, request.id(), guildId));
// wasPaused branch:
eventDispatcher.dispatch(new MusicPlayerEvent.Paused(playerId, request, guildId));
// forwarded:
eventDispatcher.dispatch(new MusicPlayerEvent.Forwarded(playerId, request, guildId, effectiveSeekMs));

// clear:
eventDispatcher.dispatch(new MusicPlayerEvent.Cleared(playerId, request, guildId));
```

In `TrackEventHandler.onTrackStart`, build a `Request` from the random ID + track's requester tag:

```java
eventDispatcher.dispatch(new MusicPlayerEvent.TrackStart(
        playerId,
        new Request(randomId(), track.requesterTag()),
        guildId,
        toTrackMetadata(track)));
```

- [ ] **Step 7: Update `TextChannelMusicPlayerTrackAnnouncer.java`**

No new import needed — `Request` is never written explicitly here, only accessed via method chaining.

Update the five action message lines in the `accept()` switch:

```java
case MusicPlayerEvent.Skipped skipped -> notify("Last track skipped by " + skipped.request().requesterTag());
case MusicPlayerEvent.WentBack wentBack -> notify("Jumped back by " + wentBack.request().requesterTag());
case MusicPlayerEvent.Rewound rewound -> notify("Rewound by " + rewound.request().requesterTag());
case MusicPlayerEvent.Forwarded forwarded -> notify("Forwarded by " + forwarded.request().requesterTag());
case MusicPlayerEvent.Cleared cleared -> notify("Cleared by " + cleared.request().requesterTag());
```

Update `onTrackStart` where it reads `requesterTag` from the event:

```java
currentRequesterTag = event.request().requesterTag();
```

- [ ] **Step 8: Run full build and confirm all tests pass**

```bash
JAVA_HOME="/c/Users/dries/.jdks/openjdk-25.0.2" ./gradlew build -q
```

Expected: BUILD SUCCESSFUL across all modules.

- [ ] **Step 9: Commit**

```bash
git add \
  music-player/bridge/src/main/java/com/bearify/music/player/bridge/events/MusicPlayerEvent.java \
  music-player/agent/src/main/java/com/bearify/music/player/agent/domain/AudioPlayer.java \
  controller/src/main/java/com/bearify/controller/music/discord/TextChannelMusicPlayerTrackAnnouncer.java \
  music-player/agent/src/test/java/com/bearify/music/player/agent/domain/AudioPlayerTest.java \
  controller/src/test/java/com/bearify/controller/music/discord/TextChannelMusicPlayerTrackAnnouncerTest.java \
  controller/src/test/java/com/bearify/controller/music/discord/MusicPlayerInteractionIntegrationTest.java
git commit -m "migrate MusicPlayerEvent records to Request"
```
