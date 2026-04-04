package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.model.TrackMetadata;

import java.util.List;

public interface TrackCatalogue {
    List<TrackMetadata> find(String guildId, String query, int limit);
}
