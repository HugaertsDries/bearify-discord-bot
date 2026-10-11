package com.bearify.controller.music.domain;

import java.util.List;

/**
 * Remembers which player message each bear has posted where, so the controller can still delete it after a restart
 * or once the bear is gone without saying goodbye.
 */
public interface PlayerMessageStore {

    void remember(String playerId, String guildId, String textChannelId, String messageId);

    /** Forgets the channel's message only if it is still {@code messageId}, so a newer message posted meanwhile stays remembered. */
    void forget(String playerId, String guildId, String textChannelId, String messageId);

    List<PlayerMessage> all();

    record PlayerMessage(String playerId, String guildId, String textChannelId, String messageId) {}
}
