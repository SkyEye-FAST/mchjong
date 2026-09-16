package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;

/** A tile ID identifies one physical tile, not just its face. */
public final class Tile {
    public static final int HIDDEN = -1;
    public static final int ABSENT = -2;
    public static final int EAST = 27, SOUTH = 28, WEST = 29, NORTH = 30;
    public static final int WHITE = 31, GREEN = 32, RED = 33;

    private Tile() {}

    public static int kind(int id) {
        if (id < 0 || id >= 136) throw new IllegalArgumentException("Invalid physical tile: " + id);
        return id / 4;
    }

    public static boolean red(int id) {
        return id >= 0 && id % 4 == 0 && (kind(id) == 4 || kind(id) == 13 || kind(id) == 22);
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
        var tiles = new ArrayList<Integer>(sanma ? 108 : 136);
        for (int id = 0; id < 136; id++) {
            if (!sanma || kind(id) == 0 || kind(id) >= 8) tiles.add(id);
        }
        return tiles;
    }

    public static List<String> notations(List<Integer> ids) {
        return ids.stream().map(id -> notation(kind(id))).toList();
    }
}
