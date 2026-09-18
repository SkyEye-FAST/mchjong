package top.skyeyefast.mchjong.engine;

import java.util.List;

/** Composition of the full physical set, before removing 2–8 characters for sanma. */
public enum RedFives {
    NONE(0, 0, 0), THREE(1, 1, 1), FOUR(1, 2, 1);

    private final int man, pin, sou;
    RedFives(int man, int pin, int sou) { this.man = man; this.pin = pin; this.sou = sou; }
    public int count(int suit) {
        return switch (suit) { case 0 -> man; case 1 -> pin; case 2 -> sou; default -> 0; };
    }
    public int total() { return man + pin + sou; }
    public String translationKey() { return "red_fives.mchjong." + name().toLowerCase(java.util.Locale.ROOT); }
    public static RedFives of(int man, int pin, int sou) {
        for (var composition : values())
            if (composition.man == man && composition.pin == pin && composition.sou == sou) return composition;
        return null;
    }
    public static RedFives of(List<Integer> tiles) {
        int[] counts = new int[3];
        for (int tile : tiles) if (Tile.red(tile)) counts[Tile.kind(tile) / 9]++;
        return of(counts[0], counts[1], counts[2]);
    }
}
