package top.skyeyefast.mchjong.engine;

import java.util.List;

public record Meld(Type type, List<Integer> tiles, int fromSeat, int calledTile) {
    public enum Type { SEQUENCE, TRIPLET, OPEN_QUAD, CONCEALED_QUAD, ADDED_QUAD }

    public Meld { java.util.Objects.requireNonNull(type); tiles = List.copyOf(tiles); }
    public boolean closed() { return type == Type.CONCEALED_QUAD; }
    public boolean quad() { return tiles.size() == 4; }
    public int kind() { return Tile.kind(tiles.get(0)); }

}
