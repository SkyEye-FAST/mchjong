package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Locale;

/** Only the index of a server-issued action is accepted from the client. */
public record Action(Type type, List<Integer> tiles) {
    public enum Type {
        DISCARD, RIICHI, CHI, PON, OPEN_KAN, CLOSED_KAN, ADDED_KAN, NUKI,
        RON, TSUMO, PASS, ABORT_NINE, READY, NEXT, PRACTICE, CHANGE_RULE, LEAVE
    }

    public Action { tiles = List.copyOf(tiles); }
    public Action(Type type) { this(type, List.of()); }
    public Action(Type type, int tile) { this(type, List.of(tile)); }
    public String translationKey() { return "action.mchjong." + type.name().toLowerCase(Locale.ROOT); }
}
