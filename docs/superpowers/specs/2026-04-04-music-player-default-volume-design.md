# Music Player Default Volume Design

## Goal

Make the music-player agent's startup default volume configurable through the repo `.env` file, while keeping runtime volume changes available after startup.

## Scope

This change only affects the initial volume applied to newly created audio engine instances during application startup. It does not introduce a new runtime command, persistence layer, or any behavior that forces the configured volume back after users change it.

## Configuration Design

Add a new Spring configuration property under `music-player`:

- `music-player.default-volume`

Bind it from an environment variable in `music-player/agent/src/main/resources/application.yml`:

- `music-player.default-volume: ${PLAYER_DEFAULT_VOLUME:100}`

This keeps the env-based override mechanism consistent with the existing application configuration. The default fallback remains `100` when the env var is absent.

## Property Binding And Validation

Extend `PlayerProperties` with a new `defaultVolume` field. The field should:

- default to `100`
- be validated as a positive or zero integer
- enforce an upper bound compatible with LavaPlayer's volume API

The preferred validation range is `0..1000`, which covers mute and louder-than-normal amplification while preventing obviously invalid values.

## Application Flow

The configured startup default volume should be applied exactly once when a new `LavaAudioEngine` creates its underlying LavaPlayer `AudioPlayer`.

Flow:

1. Spring binds `music-player.default-volume` into `PlayerProperties`.
2. The application wiring passes `defaultVolume` into `LavaAudioEngine`.
3. `LavaAudioEngine` calls LavaPlayer's volume setter immediately after constructing the internal player.
4. Any later runtime volume adjustments continue to operate normally and are not overridden by configuration.

## Component Changes

### `PlayerProperties`

Add the typed configuration field and validation annotations.

### `application.yml`

Expose the property through `${PLAYER_DEFAULT_VOLUME:100}` so local `.env` and deployment env vars can override it without code changes.

### `LavaAudioEngine`

Update the constructor to accept the configured default volume and set the underlying LavaPlayer player volume during initialization.

### Wiring

Update whichever factory or bean path constructs `LavaAudioEngine` so it passes the configured value from `PlayerProperties`.

## Error Handling

Invalid configured values should fail fast during application startup through Spring Boot configuration validation rather than being silently clamped. This keeps misconfiguration obvious and avoids surprising behavior.

## Testing

Add or update tests to cover:

- `PlayerProperties` validation for the accepted range
- startup property binding from `music-player.default-volume`
- engine initialization applying the configured default volume
- unchanged runtime behavior after initialization

The minimum acceptable automated coverage is property validation plus a focused unit test proving the engine sets its initial volume from configuration.

## Non-Goals

- Persisting volume per guild or per session
- Adding a new env var for live reconfiguration
- Overriding user-issued volume changes after startup
