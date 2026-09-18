package top.skyeyefast.mchjong.engine;

/** Public room metadata, separate from private tiles and room rule configuration. */
public record RoomView(int host, boolean invitationTeleport) {}
