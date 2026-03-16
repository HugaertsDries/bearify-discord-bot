package com.bearify.shared.player;

import com.bearify.shared.events.MusicPlayerEvent;
import com.bearify.shared.events.MusicPlayerInteraction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class PlayerMessageCodec {

    private final ObjectMapper objectMapper;

    public PlayerMessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(MusicPlayerEvent event) {
        return write(event, "MusicPlayerEvent");
    }

    public String serialize(MusicPlayerInteraction interaction) {
        return write(interaction, "MusicPlayerInteraction");
    }

    public MusicPlayerEvent parseEvent(byte[] body) {
        return read(body, MusicPlayerEvent.class, "MusicPlayerEvent");
    }

    public MusicPlayerInteraction parseInteraction(byte[] body) {
        return read(body, MusicPlayerInteraction.class, "MusicPlayerInteraction");
    }

    private String write(Object value, String label) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize " + label, e);
        }
    }

    private <T> T read(byte[] body, Class<T> type, String label) {
        try {
            return objectMapper.readValue(body, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize " + label, e);
        }
    }
}
