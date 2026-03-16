package com.bearify.music.player.agent.domain;

public record ConnectionRequest(String requestId, String voiceChannelId, String guildId) {}
