package top.skyeyefast.mchjong.engine;

/** Public room metadata, separate from private tiles and room rule configuration. */
public record RoomView(int host, boolean invitationTeleport, RoomSeating.Stage seating, int availableWinds,
                       java.util.List<Seat> seats) {
    public RoomView { seats = java.util.List.copyOf(seats); }
    /** Presence is null only for an empty seat. Bots are reported as seated. */
    public record Seat(PlayerPresence presence, int wind, BotDifficulty difficulty) {}
}
