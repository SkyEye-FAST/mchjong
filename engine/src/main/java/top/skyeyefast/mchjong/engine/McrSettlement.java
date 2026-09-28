package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Four-player MCR payments. Fan calculation stays in McrHandAnalyzer. */
public final class McrSettlement {
    private McrSettlement() {}

    public sealed interface Result permits Win, Draw {
        List<Integer> deltas();
    }

    /** fromSeat is -1 for self-draw. The winning tile is a reference, not another owned tile. */
    public record Win(int winner, int fromSeat, int tile, McrWinContext context,
                      McrHandScore score, List<Integer> deltas) implements Result {
        public Win {
            Objects.requireNonNull(context);
            Objects.requireNonNull(score);
            deltas = List.copyOf(deltas);
        }
    }

    public record Draw() implements Result {
        @Override public List<Integer> deltas() { return List.of(0, 0, 0, 0); }
    }

    /** A below-minimum declaration: not a hand result and never a negative winning score. */
    public record Penalty(int handNumber, int offender, int tile, List<Integer> deltas) {
        public Penalty { deltas = List.copyOf(deltas); }
    }

    static Win win(int winner, int fromSeat, int tile, McrWinContext context, McrHandScore score) {
        seat(winner);
        Objects.requireNonNull(context);
        Objects.requireNonNull(score);
        if (!score.meetsMinimum() || score.nonFlowerFan() < 8 || score.totalFan() < score.nonFlowerFan())
            throw new IllegalArgumentException("A normal MCR win requires eight non-flower points");
        if (context.method() == McrWinContext.Method.SELF_DRAW) {
            if (fromSeat != -1) throw new IllegalArgumentException("Self-draw has no discarder");
        } else {
            seat(fromSeat);
            if (fromSeat == winner) throw new IllegalArgumentException("A discard win needs another player");
        }
        var deltas = new ArrayList<>(Collections.nCopies(4, 0));
        for (int payer = 0; payer < 4; payer++) if (payer != winner) {
            int payment = fromSeat == -1 || payer == fromSeat ? Math.addExact(8, score.totalFan()) : 8;
            deltas.set(payer, -payment);
            deltas.set(winner, Math.addExact(deltas.get(winner), payment));
        }
        return new Win(winner, fromSeat, tile, context, score, deltas);
    }

    static Penalty wrongWin(int handNumber, int offender, int tile) {
        seat(offender);
        if (handNumber < 1 || handNumber > 16) throw new IllegalArgumentException("Invalid MCR hand number");
        var deltas = new ArrayList<>(Collections.nCopies(4, 10));
        deltas.set(offender, -30);
        return new Penalty(handNumber, offender, tile, deltas);
    }

    private static void seat(int seat) {
        if (seat < 0 || seat >= 4) throw new IllegalArgumentException("MCR seats must be in 0..3");
    }
}
