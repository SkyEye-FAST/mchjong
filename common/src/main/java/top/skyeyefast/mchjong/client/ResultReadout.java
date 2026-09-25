package top.skyeyefast.mchjong.client;

import java.util.List;
import java.util.UUID;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.ScoreAnnouncements;
import top.skyeyefast.mchjong.engine.TableView;

/** Client presentation only: one row/recording at a time, then points, then the limit. */
public final class ResultReadout {
    private final UUID table;
    private final int hand, viewer;
    private final List<TableView.Win> wins;
    private final List<List<String>> rows;
    private final List<String> limits;
    private final int[] visible;
    private final long[] scored;
    private int winner;
    private boolean limitSpoken, complete;
    private long next, pointsAt = -1;

    public ResultReadout(TableView view, long now) {
        table = view.tableId(); hand = view.handNumber(); viewer = view.viewerSeat(); wins = view.wins();
        rows = wins.stream().map(win -> ScoreAnnouncements.rows(win.score())).toList();
        limits = wins.stream().map(win -> ScoreAnnouncements.limit(win.score(), win.seat() == view.dealer())).toList();
        visible = new int[wins.size()];
        scored = new long[wins.size()];
        java.util.Arrays.fill(scored, -1);
        next = now + 400;
        if (wins.isEmpty()) finish(now);
    }

    public boolean matches(TableView view) {
        return view != null && table.equals(view.tableId()) && hand == view.handNumber()
            && viewer == view.viewerSeat() && wins.equals(view.wins())
            && (view.phase() == Game.Phase.HAND_END || view.phase() == Game.Phase.MATCH_END);
    }

    public int winner() { return winner; }
    public boolean complete() { return complete; }
    public int visibleRows(int index) { return visible[index]; }
    public long scoredAt(int index) { return scored[index]; }
    public long pointsAt() { return pointsAt; }

    /** At most one event per tick, even after a long frame. Scores are always server-authored. */
    public String tick(long now, boolean speaking) {
        if (complete || now < next || speaking) return null;
        if (visible[winner] < rows.get(winner).size()) {
            next = now + 750;
            return rows.get(winner).get(visible[winner]++);
        }
        if (scored[winner] < 0) {
            scored[winner] = now;
            next = now + 350;
            return null;
        }
        if (!limitSpoken) {
            limitSpoken = true;
            next = now + 900;
            return limits.get(winner);
        }
        if (winner + 1 < wins.size()) {
            winner++;
            limitSpoken = false;
            next = now + 350;
        } else finish(now);
        return null;
    }

    public void finish(long now) {
        if (complete) return;
        for (int i = 0; i < visible.length; i++) {
            visible[i] = rows.get(i).size();
            if (scored[i] < 0) scored[i] = now;
        }
        complete = true;
        pointsAt = now;
    }
}
