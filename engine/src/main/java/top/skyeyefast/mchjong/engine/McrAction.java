package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Objects;

/** Engine-issued MCR declarations. The position determines win method and melded-kong origin. */
public record McrAction(Type type, List<Integer> tiles) {
    public enum Type { DRAW, REPLACE_FLOWER, DISCARD, CHOW, PUNG, MELDED_KONG, CONCEALED_KONG, WIN, PASS }

    public McrAction {
        Objects.requireNonNull(type);
        tiles = List.copyOf(tiles);
        int expected = switch (type) {
            case DRAW, REPLACE_FLOWER, PASS -> 0;
            case DISCARD, WIN -> 1;
            case CHOW, PUNG -> 2;
            case CONCEALED_KONG -> 4;
            case MELDED_KONG -> tiles.size() == 1 ? 1 : 3;
        };
        if (tiles.size() != expected || tiles.stream().anyMatch(tile -> tile < 0 || tile >= 136)
            || tiles.stream().distinct().count() != tiles.size())
            throw new IllegalArgumentException("Invalid MCR action tiles");
    }

    public McrAction(Type type) { this(type, List.of()); }
    public McrAction(Type type, int tile) { this(type, List.of(tile)); }
}
