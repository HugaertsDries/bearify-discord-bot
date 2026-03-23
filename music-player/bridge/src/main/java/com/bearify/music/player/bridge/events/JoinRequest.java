package com.bearify.music.player.bridge.events;

public record JoinRequest(String requestId, String guildId, String voiceChannelId) {}
