package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotReportTest {
    @Test void pairedIntervalsGroupEveryRotationBySeedAndRejectIncompletePairs() {
        var before = matches(0);
        var after = matches(120);
        var result = BotReport.compare(before, after);
        assertEquals(2, result.seeds());
        assertEquals(8, result.matches());
        assertEquals(120, result.points().delta(), 0.001);
        assertEquals(120, result.points().low(), 0.001);
        assertEquals(120, result.points().high(), 0.001);
        assertEquals(0, result.rank().delta(), 0.001);
        var opposed = after.stream().map(m -> new BotReport.Match(m.seed(), m.rotation(), m.challenger(), m.field(),
            m.rules(), m.points().stream().map(p -> m.seed() == 0 ? p : -p).toList(), m.ranks(), m.hands())).toList();
        var clustered = BotReport.compare(before, opposed).points();
        assertEquals(-120, clustered.low(), 0.001, "Correlated rotations must be resampled together");
        assertEquals(120, clustered.high(), 0.001);
        var incomplete = new ArrayList<>(after);
        incomplete.removeLast();
        assertThrows(IllegalArgumentException.class, () -> BotReport.compare(before, incomplete));
        var duplicate = new ArrayList<>(after);
        duplicate.add(after.getFirst());
        assertThrows(IllegalArgumentException.class, () -> BotReport.compare(before, duplicate));
        var different = after.stream().map(m -> new BotReport.Match(m.seed() + 2, m.rotation(), m.challenger(), m.field(),
            m.rules(), m.points(), m.ranks(), m.hands())).toList();
        assertThrows(IllegalArgumentException.class, () -> BotReport.compare(before, different));
    }

    private static List<BotReport.Match> matches(int gain) {
        var matches = new ArrayList<BotReport.Match>();
        for (long seed = 0; seed < 2; seed++) for (int rotation = 0; rotation < 4; rotation++) {
            var points = new ArrayList<Integer>();
            for (int seat = 0; seat < 4; seat++) points.add(seat == rotation ? -3 * gain : gain);
            matches.add(new BotReport.Match(seed, rotation, "model", "HARD", "TENHOU_4", points, List.of(1, 2, 3, 4), 8));
        }
        return matches;
    }
}
