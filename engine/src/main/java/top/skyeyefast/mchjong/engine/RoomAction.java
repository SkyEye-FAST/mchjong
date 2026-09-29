package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Objects;

/** Preparation controls only. Tile decisions stay in Action and McrAction. */
public record RoomAction(Type type, List<Integer> arguments) {
    public enum Type { READY, LEAVE_ROOM, BEGIN_SEATING, DRAW_WIND, FILL_BOTS, SET_BOT, REMOVE_BOT, TRANSFER_HOST, RETURN_TO_LOBBY }

    public RoomAction {
        Objects.requireNonNull(type);
        arguments = List.copyOf(arguments);
    }
    public RoomAction(Type type) { this(type, List.of()); }
    public RoomAction(Type type, int argument) { this(type, List.of(argument)); }
}
