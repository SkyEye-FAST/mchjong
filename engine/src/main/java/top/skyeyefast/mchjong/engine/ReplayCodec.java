package top.skyeyefast.mchjong.engine;

public final class ReplayCodec {
    private static final com.google.gson.Gson JSON = new com.google.gson.GsonBuilder().disableJdkUnsafe().serializeNulls().create();
    private ReplayCodec() {}
    public static String encode(Object value) { return JSON.toJson(value); }
    public static <T> T decode(String json, Class<T> type) {
        if (type != ReplayMatch.class && type != ReplayMatch.Header.class && type != ReplayMatch.Index.class)
            throw new IllegalArgumentException("Not a replay document");
        return SichuanCodec.decode(json, type, 8 * 1024 * 1024);
    }
    public static void validate(ReplayMatch match) {
        for (int index = 0; index < match.handCount(); index++) {
            switch (match.variant()) {
                case RIICHI -> ReplayPlayback.timeline(match, index);
                case MCR -> McrReplayPlayback.timeline(match, index);
                case SICHUAN -> SichuanReplayPlayback.timeline(match, index);
                case TAIWAN -> throw new IllegalArgumentException("Taiwan replay is unavailable");
            }
        }
    }
}
