# shared

DTOs and domain models shared between the `controller` and `player` modules. No business logic — just data carriers passed over Redis pub/sub.

## Contents

### Model
- `Track` — a playable track (title, author, uri, durationMs)

### Events
- `PlayerCommand` — sent from controller to player. Sealed interface with subtypes:
  - `Play(playerId, track)`
  - `Queue(playerId, track)`
  - `Pause(playerId)`
  - `Resume(playerId)`
  - `Skip(playerId)`
  - `Stop(playerId)`

- `PlayerEvent` — sent from player to controller. Sealed interface with subtypes:
  - `TrackStart(playerId, track)`
  - `TrackEnd(playerId, track)`
  - `TrackError(playerId, track)`
  - `QueueEmpty(playerId)`
  - `PlayerReady(playerId)`
  - `PlayerStopped(playerId)`
