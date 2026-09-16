package top.skyeyefast.mchjong.engine;

import java.util.List;

public record Meld(Type type, List<Integer> tiles, int fromSeat, int calledTile) {
    public enum Type { CHI, PON, OPEN_KAN, CLOSED_KAN, ADDED_KAN }

    public Meld { tiles = List.copyOf(tiles); }
    public boolean closed() { return type == Type.CLOSED_KAN; }
    public boolean kan() { return tiles.size() == 4; }
    public int kind() { return Tile.kind(tiles.getFirst()); }

    public String libraryNotation() {
        StringBuilder text = new StringBuilder();
        tiles.stream().map(Tile::kind).sorted().forEach(k -> text.append(k < 27 ? k % 9 + 1 : k - 26));
        text.append("mpsz".charAt(kind() / 9));
        if (closed()) return "0" + text.substring(1, 3) + "0" + text.charAt(4);
        return text.toString();
    }
}
