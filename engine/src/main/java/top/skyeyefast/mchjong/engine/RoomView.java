package top.skyeyefast.mchjong.engine;

/** Public room metadata, separate from private tiles and room rule configuration. */
public record RoomView(int host, boolean invitationTeleport, RoomSeating.Stage seating, int availableWinds,
                       java.util.List<Seat> seats) {
    public RoomView { seats = java.util.List.copyOf(seats); }
    public record Seat(boolean present, int wind, BotDifficulty difficulty) {}
}
