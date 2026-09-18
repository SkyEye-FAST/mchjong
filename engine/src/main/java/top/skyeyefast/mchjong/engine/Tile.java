package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;

/** A tile ID identifies one physical tile, not just its face. */
public final class Tile {
    private static final int RED_FLAG = 256;
    public static final java.util.Comparator<Integer> ORDER = java.util.Comparator.comparingInt(Tile::kind)
        .thenComparingInt(id -> id);
    public static final int HIDDEN = -1;
    public static final int ABSENT = -2;
    public static final int EAST = 27, SOUTH = 28, WEST = 29, NORTH = 30;
    public static final int WHITE = 31, GREEN = 32, RED = 33;

    private Tile() {}

    public static int kind(int id) {
        int physical = id & ~RED_FLAG;
        if (physical < 0 || physical >= 136 || (id & RED_FLAG) != 0 && physical / 4 != 4
            && physical / 4 != 13 && physical / 4 != 22) throw new IllegalArgumentException("Invalid physical tile: " + id);
        return physical / 4;
    }

    public static boolean red(int id) {
        return id >= 0 && (id & RED_FLAG) != 0;
    }

    public static int id(int kind, int copy, boolean red) {
        if (kind < 0 || kind >= 34 || copy < 0 || copy >= 4) throw new IllegalArgumentException("Invalid tile identity");
        int id = kind * 4 + copy | (red ? RED_FLAG : 0);
        kind(id);
        return id;
    }

    public static boolean validSet(List<Integer> tiles) {
        boolean sanma = tiles.size() == 108;
        if (!sanma && tiles.size() != 136) return false;
        boolean[] seen = new boolean[136];
        try {
            for (int tile : tiles) {
                int face = kind(tile);
                if (sanma && face > 0 && face < 8) return false;
                int physical = tile & ~RED_FLAG;
                if (seen[physical]) return false;
                seen[physical] = true;
            }
            return RedFives.of(tiles) != null;
        } catch (IllegalArgumentException invalid) { return false; }
    }

    public static boolean terminalOrHonor(int kind) {
        return kind >= 27 || kind % 9 == 0 || kind % 9 == 8;
    }

    public static String notation(int kind) {
        if (kind < 0 || kind >= 34) throw new IllegalArgumentException("Invalid tile kind");
        return "" + (kind < 27 ? kind % 9 + 1 : kind - 26) + "mpsz".charAt(kind / 9);
    }

    public static int parseKind(String notation) {
        if (notation.length() != 2) throw new IllegalArgumentException("Invalid tile notation");
        int suit = "mpsz".indexOf(notation.charAt(1));
        int number = Character.digit(notation.charAt(0), 10);
        if (suit < 0 || number < 1 || number > (suit == 3 ? 7 : 9)) {
            throw new IllegalArgumentException("Invalid tile notation: " + notation);
        }
        return suit * 9 + number - 1;
    }

    public static int doraAfter(int indicator, boolean sanma) {
        if (sanma && indicator == 0) return 8;
        if (sanma && indicator == 8) return 0;
        if (indicator < 27) return indicator / 9 * 9 + (indicator + 1) % 9;
        if (indicator < 31) return 27 + (indicator - 26) % 4;
        return 31 + (indicator - 30) % 3;
    }

    public static List<Integer> set(boolean sanma) {
        return set(sanma, RedFives.THREE);
    }

    public static List<Integer> set(boolean sanma, RedFives redFives) {
        var tiles = new ArrayList<Integer>(sanma ? 108 : 136);
        for (int face = 0; face < 34; face++) {
            if (sanma && face > 0 && face < 8) continue;
            for (int copy = 0; copy < 4; copy++)
                tiles.add(id(face, copy, face < 27 && face % 9 == 4 && copy < redFives.count(face / 9)));
        }
        return tiles;
    }

    public static List<String> notations(List<Integer> ids) {
        return ids.stream().map(id -> notation(kind(id))).toList();
    }
}
