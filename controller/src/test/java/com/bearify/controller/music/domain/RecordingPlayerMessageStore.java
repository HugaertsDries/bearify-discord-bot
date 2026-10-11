package com.bearify.controller.music.domain;

import java.util.ArrayList;
import java.util.List;

/** An in-memory {@link PlayerMessageStore} for tests that don't need Redis. */
public class RecordingPlayerMessageStore implements PlayerMessageStore {

    private final List<PlayerMessage> messages = new ArrayList<>();

    @Override
    public synchronized void remember(String playerId, String guildId, String textChannelId, String messageId) {
        messages.removeIf(message -> message.playerId().equals(playerId)
                && message.guildId().equals(guildId)
                && message.textChannelId().equals(textChannelId));
        messages.add(new PlayerMessage(playerId, guildId, textChannelId, messageId));
    }

    @Override
    public synchronized void forget(String playerId, String guildId, String textChannelId, String messageId) {
        messages.remove(new PlayerMessage(playerId, guildId, textChannelId, messageId));
    }

    @Override
    public synchronized List<PlayerMessage> all() {
        return List.copyOf(messages);
    }
}
