package com.bearify.controller.music.redis;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.model.Request;
import com.bearify.music.player.bridge.model.StopReason;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.bearify.music.player.bridge.model.TrackRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerProtocolRoundTripTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static Stream<Class<?>> eventTypes() {
        return Arrays.stream(MusicPlayerEvent.class.getPermittedSubclasses());
    }

    static Stream<Class<?>> interactionTypes() {
        return Arrays.stream(MusicPlayerInteraction.class.getPermittedSubclasses());
    }

    @ParameterizedTest
    @MethodSource("eventTypes")
    void roundTripsEveryEventType(Class<?> type) {
        MusicPlayerEvent event = (MusicPlayerEvent) sample(type);

        String json = OBJECT_MAPPER.writerFor(MusicPlayerEvent.class).writeValueAsString(event);

        assertThat(OBJECT_MAPPER.readValue(json, MusicPlayerEvent.class)).isEqualTo(event);
    }

    @ParameterizedTest
    @MethodSource("interactionTypes")
    void roundTripsEveryInteractionType(Class<?> type) {
        MusicPlayerInteraction interaction = (MusicPlayerInteraction) sample(type);

        String json = OBJECT_MAPPER.writerFor(MusicPlayerInteraction.class).writeValueAsString(interaction);

        assertThat(OBJECT_MAPPER.readValue(json, MusicPlayerInteraction.class)).isEqualTo(interaction);
    }

    @Test
    void roundTripsHealthCheckResultWithoutReason() {
        MusicPlayerEvent event = new MusicPlayerEvent.HealthCheckResult("player-id", "request-id", true, Optional.empty());

        String json = OBJECT_MAPPER.writerFor(MusicPlayerEvent.class).writeValueAsString(event);

        assertThat(OBJECT_MAPPER.readValue(json, MusicPlayerEvent.class)).isEqualTo(event);
    }

    @Test
    void roundTripsQueueSnapshotWithoutCurrentTrack() {
        MusicPlayerEvent event = new MusicPlayerEvent.QueueSnapshot("player-id", "request-id", "guild-id", Optional.empty(), List.of(track()));

        String json = OBJECT_MAPPER.writerFor(MusicPlayerEvent.class).writeValueAsString(event);

        assertThat(OBJECT_MAPPER.readValue(json, MusicPlayerEvent.class)).isEqualTo(event);
    }

    @Test
    void readsQueueUpdatedFromOlderBearWithoutQueueSize() {
        String json = "{\"type\":\"queue_updated\",\"playerId\":\"player-id\",\"requestId\":\"request-id\",\"guildId\":\"guild-id\",\"upNext\":[]}";

        assertThat(OBJECT_MAPPER.readValue(json, MusicPlayerEvent.class))
                .isEqualTo(new MusicPlayerEvent.QueueUpdated("player-id", "request-id", "guild-id", List.of(), 0, 0L));
    }

    @Test
    void readsStoppedWithoutReasonAsRequested() {
        String json = "{\"type\":\"player_stopped\",\"playerId\":\"player-id\",\"requestId\":\"request-id\",\"guildId\":\"guild-id\"}";

        assertThat(OBJECT_MAPPER.readValue(json, MusicPlayerEvent.class))
                .isEqualTo(new MusicPlayerEvent.Stopped("player-id", "request-id", "guild-id", StopReason.REQUESTED));
    }

    private static Object sample(Class<?> recordType) {
        RecordComponent[] components = recordType.getRecordComponents();
        Class<?>[] parameterTypes = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] arguments = Arrays.stream(components).map(MusicPlayerProtocolRoundTripTest::sampleValue).toArray();
        try {
            return recordType.getDeclaredConstructor(parameterTypes).newInstance(arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot construct " + recordType.getSimpleName(), e);
        }
    }

    private static Object sampleValue(RecordComponent component) {
        Class<?> type = component.getType();
        String name = component.getName();
        if (type == String.class) return name + "-value";
        if (type == long.class || type == Long.class) return 42L;
        if (type == int.class || type == Integer.class) return 7;
        if (type == boolean.class) return true;
        if (type == Optional.class && component.getGenericType().getTypeName().contains(TrackMetadata.class.getName())) return Optional.of(track());
        // the last constant, so a reason lost in transit cannot hide behind a default of REQUESTED
        if (type.isEnum()) return type.getEnumConstants()[type.getEnumConstants().length - 1];
        if (type == Optional.class) return Optional.of(name + "-value");
        if (type == Request.class) return new Request("request-id", "requester#0001");
        if (type == TrackRequest.class) return new TrackRequest("query", "text-channel-id", "requester#0001");
        if (type == TrackMetadata.class) return track();
        if (type == List.class) return List.of(track());
        throw new IllegalArgumentException("No sample value for " + type.getName() + " " + name);
    }

    private static TrackMetadata track() {
        return new TrackMetadata("title", "author", "https://example.com/track", 180_000L, "https://example.com/art.jpg");
    }
}
