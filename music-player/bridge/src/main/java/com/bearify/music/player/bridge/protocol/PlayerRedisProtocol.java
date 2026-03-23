package com.bearify.music.player.bridge.protocol;

public final class PlayerRedisProtocol {

    private PlayerRedisProtocol() {}

    public static final class Keys {
        private Keys() {}

        public static String connectRequest(String requestId) {
            return "players:request:" + requestId;
        }

        public static String assignment(String guildId) {
            return "players:assignment:" + guildId + ":*";
        }

        public static String assignment(String guildId, String voiceChannelId) {
            return "players:assignment:" + guildId + ":" + voiceChannelId;
        }
    }

    public static final class Channels {
        private Channels() {}

        public static final String EVENTS = "players:events";
        public static final String REQUESTS = "players:requests";

        public static String interactions(String playerId) {
            return "players:interactions:" + playerId;
        }
    }
}
