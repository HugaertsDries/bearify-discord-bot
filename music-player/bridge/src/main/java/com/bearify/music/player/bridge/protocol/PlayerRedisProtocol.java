package com.bearify.music.player.bridge.protocol;

public final class PlayerRedisProtocol {

    private PlayerRedisProtocol() {}

    public static final class Keys {
        private Keys() {}

        public static final String AVAILABLE_PLAYERS = "players:available";

        public static String assignment(String guildId, String voiceChannelId) {
            return "players:assignment:" + guildId + ":" + voiceChannelId;
        }
    }

    public static final class Channels {
        private Channels() {}

        public static final String EVENTS = "players:events";

        public static String commands(String playerId) {
            return "players:commands:" + playerId;
        }
    }
}
