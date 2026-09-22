package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.SplittableRandom;

/** Pregame wind lottery. Unturned wind identities belong only to the persistent server state. */
public final class RoomSeating {
    public enum Stage { GATHERING, DRAWING, POSITIONING }
    Stage stage = Stage.GATHERING;
    int[] winds = {-1, -1, -1, -1};
    int[] concealed = {};
    int available;

    void begin(int players, boolean manual, long seed) {
        var order = new ArrayList<Integer>();
        for (int wind = 0; wind < players; wind++) order.add(wind);
        // Nearby decision seeds must not pin a wind to the same concealed slot.
        var random = new SplittableRandom(seed);
        for (int remaining = order.size(); remaining > 1; remaining--)
            Collections.swap(order, remaining - 1, random.nextInt(remaining));
        concealed = order.stream().mapToInt(Integer::intValue).toArray();
        if (manual) {
            stage = Stage.DRAWING;
            available = (1 << players) - 1;
        } else {
            for (int seat = 0; seat < players; seat++) winds[seat] = concealed[seat];
            stage = Stage.POSITIONING;
        }
    }

    void draw(int seat, int tile) {
        winds[seat] = concealed[tile];
        available &= ~(1 << tile);
    }

    boolean complete(int players) {
        for (int seat = 0; seat < players; seat++) if (winds[seat] < 0) return false;
        return true;
    }

    void positioned(int players) {
        for (int seat = 0; seat < players; seat++) winds[seat] = seat;
        concealed = new int[0];
        available = 0;
        stage = Stage.POSITIONING;
    }

    void validate(int players) {
        if (stage == null || winds == null || winds.length != 4 || concealed == null
            || (available & ~((1 << players) - 1)) != 0) throw new IllegalStateException("Invalid seating state");
        var seen = new HashSet<Integer>();
        for (int seat = 0; seat < 4; seat++) {
            int wind = winds[seat];
            if (wind < -1 || wind >= players || seat >= players && wind != -1 || wind >= 0 && !seen.add(wind))
                throw new IllegalStateException("Invalid drawn wind");
        }
        if (stage == Stage.DRAWING) {
            if (concealed.length != players || Arrays.stream(concealed).anyMatch(wind -> wind < 0 || wind >= players)
                || Arrays.stream(concealed).distinct().count() != players || Integer.bitCount(available) + seen.size() != players)
                throw new IllegalStateException("Invalid wind lottery");
            for (int tile = 0; tile < players; tile++) if (((available >> tile) & 1) == 1 && seen.contains(concealed[tile]))
                throw new IllegalStateException("Drawn wind remains available");
        } else if (available != 0 || concealed.length != 0 || stage == Stage.GATHERING && !seen.isEmpty()
            || stage == Stage.POSITIONING && !complete(players)) throw new IllegalStateException("Invalid seating stage");
        if (stage == Stage.POSITIONING) for (int seat = 0; seat < players; seat++)
            if (winds[seat] != seat) throw new IllegalStateException("Positioned roster does not match the winds");
    }
}
