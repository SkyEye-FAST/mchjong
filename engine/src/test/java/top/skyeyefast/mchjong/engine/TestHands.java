package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;

final class TestHands {
    private TestHands() {}
    static List<Integer> tiles(String text) {
        boolean[] used = new boolean[136];
        var result = new ArrayList<Integer>();
        StringBuilder digits = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) { digits.append(c); continue; }
            int suit = "mpsz".indexOf(c);
            if (suit < 0 || digits.isEmpty()) throw new IllegalArgumentException(text);
            for (int i = 0; i < digits.length(); i++) {
                int n = digits.charAt(i) - '0';
                int kind = suit * 9 + (n == 0 ? 4 : n - 1);
                int copy = n == 0 ? 0 : (kind == 4 || kind == 13 || kind == 22) ? 1 : 0;
                while (copy < 4 && used[kind * 4 + copy]) copy++;
                if (copy == 4 && n != 0 && !used[kind * 4]) copy = 0;
                if (copy >= 4 || used[kind * 4 + copy]) throw new IllegalArgumentException("Too many copies: " + text);
                int tile = kind * 4 + copy;
                used[tile] = true;
                result.add(tile);
            }
            digits.setLength(0);
        }
        if (!digits.isEmpty()) throw new IllegalArgumentException(text);
        return result;
    }
    static Meld meld(Meld.Type type, String text) {
        var tiles = tiles(text);
        return new Meld(type, tiles, 3, type == Meld.Type.CLOSED_KAN ? Tile.ABSENT : tiles.getFirst());
    }
}
