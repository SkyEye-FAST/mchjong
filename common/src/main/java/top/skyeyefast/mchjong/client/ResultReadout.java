package top.skyeyefast.mchjong.client;

import java.util.List;
import java.util.UUID;
import top.skyeyefast.mchjong.engine.ScoreAnnouncements;
import top.skyeyefast.mchjong.engine.RiichiView;

/** Client presentation only: one row/recording at a time, then points, then the limit. */
public final class ResultReadout {
    public enum Stage { YAKU, POINTS, LIMIT, NEXT_WINNER, COMPLETE }
    public record Event(Stage stage, int winner, String voice) {}
    public static final long POINTS_HOLD_MILLIS = 750;
    private static final long YAKU_PAUSE_MILLIS = 300;
    private final UUID table;
    private final int hand, viewer;
    private final List<RiichiView.Win> wins;
    private final List<List<String>> rows;
    private final List<String> limits;
    private final int[] visible;
    private final long[] scored;
    private int winner;
    private Stage stage = Stage.YAKU;
    private boolean complete;
    private final boolean[] graded;
    private long next, quietSince = -1, pointsAt = -1;

    public ResultReadout(RiichiView view, long now) {
        table = view.tableId(); hand = view.handNumber(); viewer = view.viewerSeat(); wins = view.wins();
        rows = wins.stream().map(win -> ScoreAnnouncements.rows(view, win).stream().map(ScoreAnnouncements.Row::voice).toList()).toList();
        limits = wins.stream().map(win -> ScoreAnnouncements.limit(win.score(), win.seat() == view.dealer())).toList();
        visible = new int[wins.size()];
        scored = new long[wins.size()];
        graded = new boolean[wins.size()];
        java.util.Arrays.fill(scored, -1);
        next = now + 400;
        if (wins.isEmpty()) finish(now);
    }

    ResultReadout(TableResultState view, long now) {
        table = null; hand = -1; viewer = view.viewerSeat(); wins = List.of();
        rows = view.wins().stream().map(w -> w.rows().stream().map(TableResultState.Row::voice).toList()).toList();
        limits = view.wins().stream().map(TableResultState.Win::limitVoice).toList();
        visible = new int[rows.size()]; scored = new long[rows.size()]; graded = new boolean[rows.size()];
        java.util.Arrays.fill(scored, -1); next = now + 400;
        if (rows.isEmpty()) finish(now);
    }

    public boolean matches(RiichiView view) {
        return view != null && table != null && table.equals(view.tableId()) && hand == view.handNumber()
            && viewer == view.viewerSeat() && wins.equals(view.wins())
            && (view.phase() == RiichiView.Phase.HAND_END || view.phase() == RiichiView.Phase.MATCH_END);
    }

    public int winner() { return winner; }
    public boolean complete() { return complete; }
    public int visibleRows(int index) { return visible[index]; }
    public long scoredAt(int index) { return scored[index]; }
    public long pointsAt() { return pointsAt; }

    public Stage stage() { return stage; }
    public boolean limitVisible(int index) { return graded[index]; }

    /** One event per tick. Silence is measured after playback, including long recordings. */
    public Event tick(long now, boolean speaking) {
        if (complete) return null;
        if (speaking) { quietSince = -1; return null; }
        if (quietSince < 0) quietSince = now;
        if (now < next) return null;
        if (stage == Stage.YAKU || stage == Stage.NEXT_WINNER) {
            if (visible[winner] < rows.get(winner).size()) {
                stage = Stage.YAKU;
                next = now + 750;
                quietSince = -1;
                return new Event(stage, winner, rows.get(winner).get(visible[winner]++));
            }
            if (now - quietSince < YAKU_PAUSE_MILLIS) return null;
            scored[winner] = now;
            stage = Stage.POINTS;
            next = now + POINTS_HOLD_MILLIS;
            return new Event(stage, winner, null);
        }
        if (stage == Stage.POINTS && limits.get(winner) != null) {
            stage = Stage.LIMIT;
            graded[winner] = true;
            next = now + 900;
            quietSince = -1;
            return new Event(stage, winner, limits.get(winner));
        }
        if (stage == Stage.LIMIT && now - quietSince < 250) return null;
        if (winner + 1 < visible.length) {
            winner++;
            stage = Stage.NEXT_WINNER;
            next = now + 350;
            return new Event(stage, winner, null);
        }
        finish(now);
        return new Event(Stage.COMPLETE, winner, null);
    }

    public void finish(long now) {
        if (complete) return;
        for (int i = 0; i < visible.length; i++) {
            visible[i] = rows.get(i).size();
            if (scored[i] < 0) scored[i] = now;
            graded[i] = limits.get(i) != null;
        }
        stage = Stage.COMPLETE;
        complete = true;
        pointsAt = now;
    }
}
