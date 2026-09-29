package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Locale;

/** Only the index of a server-issued action is accepted from the client. */
public record Action(Type type, List<Integer> tiles) {
    public enum Type {
        DISCARD, RIICHI, CHI, PON, OPEN_KAN, CLOSED_KAN, ADDED_KAN, NUKI,
        RON, TSUMO, PASS, ABORT_NINE, NEXT, SKIP_SETTLEMENT, CHANGE_RULE,
        SHUFFLE, BUILD_WALL, PICK_UP_DICE, ROLL_DICE, TAKE_PACKET, DRAW, SETTLEMENT_DONE;

        /** Riichi declaration to neutral physical structure; never match enum names or ordinals. */
        public Meld.Type meldType() {
            return switch (this) {
                case CHI -> Meld.Type.SEQUENCE;
                case PON -> Meld.Type.TRIPLET;
                case OPEN_KAN -> Meld.Type.OPEN_QUAD;
                case CLOSED_KAN -> Meld.Type.CONCEALED_QUAD;
                case ADDED_KAN -> Meld.Type.ADDED_QUAD;
                default -> throw new IllegalStateException("Not a meld declaration: " + this);
            };
        }

        public static Type fromMeld(Meld.Type type) {
            return switch (type) {
                case SEQUENCE -> CHI;
                case TRIPLET -> PON;
                case OPEN_QUAD -> OPEN_KAN;
                case CONCEALED_QUAD -> CLOSED_KAN;
                case ADDED_QUAD -> ADDED_KAN;
            };
        }
    }

    public Action { tiles = List.copyOf(tiles); }
    public Action(Type type) { this(type, List.of()); }
    public Action(Type type, int tile) { this(type, List.of(tile)); }
    public String translationKey() { return "action.mchjong." + type.name().toLowerCase(Locale.ROOT); }
}
