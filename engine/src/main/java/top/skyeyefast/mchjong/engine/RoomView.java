package top.skyeyefast.mchjong.engine;

/** Public room metadata, separate from private tiles and room rule configuration. */
public record RoomView(int host, boolean convenienceHints, RoomSeating.Stage seating, int availableWinds,
                       java.util.List<Seat> seats, int settlementTicks, int settlementSkippedSeats) {
    public RoomView { seats = java.util.List.copyOf(seats); }
    /** Presence is null only for an empty seat. Virtual bots are always seated; companions use their mounts. */
    public record Seat(PlayerPresence presence, int wind, BotDifficulty difficulty) {}
}
