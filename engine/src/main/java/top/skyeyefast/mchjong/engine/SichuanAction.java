package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Objects;

public record SichuanAction(Type type, List<Integer> tiles, int suit) {
    public enum Type { VOID_SUIT, DRAW, DISCARD, PUNG, DISCARD_KONG, CONCEALED_KONG, ADDED_KONG, WIN, PASS }
    public SichuanAction {
        Objects.requireNonNull(type);
        tiles = List.copyOf(tiles);
        int size = switch (type) {
            case DISCARD, ADDED_KONG -> 1;
            case PUNG -> 2;
            case DISCARD_KONG -> 3;
            case CONCEALED_KONG -> 4;
            default -> 0;
        };
        if ((type == Type.VOID_SUIT ? tiles.size() > 1 : tiles.size() != size)
            || (type == Type.VOID_SUIT ? suit < 0 || suit > 2 : suit != -1))
            throw new IllegalArgumentException("Invalid Sichuan action");
        for (int tile : tiles) if (tile < 0 || tile >= 108) throw new IllegalArgumentException("Invalid Sichuan tile");
        if (type == Type.VOID_SUIT && !tiles.isEmpty() && Tile.kind(tiles.get(0)) / 9 != suit)
            throw new IllegalArgumentException("First discard is not in the void suit");
    }
    public SichuanAction(Type type) { this(type, List.of(), -1); }
    public SichuanAction(Type type, List<Integer> tiles) { this(type, tiles, -1); }
}
