package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.Discard;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.HandScore;
import top.skyeyefast.mchjong.engine.ScoreAnnouncements;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.TimeControl;
import static org.junit.jupiter.api.Assertions.*;

class TableAudioEventsTest {
    private static final UUID TABLE = new UUID(6, 12);
    private static TableView.Seat seat(List<Discard> river, List<Meld> melds, List<Integer> norths) {
        return new TableView.Seat(false, "Player", true, false, false, 25000,
            List.of(), -2, melds, river, norths, false, false, false);
    }
    private static TableView view(long revision, int hand, Game.Phase phase, List<TableView.Seat> seats, String result) {
        return new TableView(TABLE, revision, 1, hand, RuleSet.TENHOU_4.config(), phase, 0,
            0, 0, 0, 0, 0, 70, 0, List.of(), null, seats, List.of(), List.of(), result, List.of(), List.of(),
            TimeControl.DEFAULT, List.of(), List.of(), top.skyeyefast.mchjong.engine.HandVisibility.SELF, null, null, null, false, 1);
    }
    private static List<TableView.Seat> seats() {
        return new ArrayList<>(Collections.nCopies(4, seat(List.of(), List.of(), List.of())));
    }
    private static List<String> sounds(TableView before, TableView after) {
        return TableAudioEvents.between(before, after).stream().map(TableAudioEvents.Cue::sound).toList();
    }

    private static TableView receipt(long revision, int hand, List<TableView.Win> wins) {
        return new TableView(TABLE, revision, revision, hand, RuleSet.TENHOU_4.config(), Game.Phase.HAND_END, 0,
            0, 0, 0, 0, 0, 0, 0, List.of(), null, seats(), List.of(), wins, "ron", List.of(), List.of(),
            TimeControl.DEFAULT, List.of(), List.of(), top.skyeyefast.mchjong.engine.HandVisibility.SELF, null, null, null, false, 1);
    }

    @Test void receiptWaitsForEachRecordingThenShowsPointsBeforeTheGrade() {
        var score = new HandScore(3, 60, 0, 8000, 0, 0, List.of("Richi"), 2);
        var wins = List.of(new TableView.Win(1, 0, 4, score));
        var readout = new ResultReadout(receipt(1, 1, wins), 0);
        assertNull(readout.tick(400, true));
        assertEquals(0, readout.visibleRows(0));
        assertEquals("yaku.riichi", readout.tick(500, false));
        assertNull(readout.tick(5000, true));
        assertEquals(1, readout.visibleRows(0));
        assertEquals("yaku.dora_2", readout.tick(5001, false));
        assertEquals(-1, readout.scoredAt(0));
        assertNull(readout.tick(6000, true));
        assertNull(readout.tick(6001, false));
        assertEquals(6001, readout.scoredAt(0));
        assertNull(readout.tick(6350, false));
        assertEquals("score.mangan", readout.tick(6351, false));
        assertNull(readout.tick(9000, true));
        assertFalse(readout.complete());
        assertNull(readout.tick(9001, false));
        assertTrue(readout.complete());
        assertEquals(9001, readout.pointsAt());
        assertTrue(readout.matches(receipt(50, 1, wins)), "Snapshot/decision refresh retains progress");
        assertFalse(readout.matches(receipt(51, 2, wins)));
        assertNull(readout.tick(10000, false));
        readout.finish(11000);
        assertEquals(9001, readout.pointsAt(), "Skipping a completed readout does not restart scores");
    }

    @Test void silentMultiWinnerReceiptsAdvanceOnceAndCanBeSkippedWithoutReplaying() {
        var score = new HandScore(1, 30, 0, 1000, 0, 0, List.of("Richi"), 0);
        var view = receipt(1, 1, List.of(new TableView.Win(1, 0, 4, score), new TableView.Win(2, 0, 4, score)));
        var readout = new ResultReadout(view, 0);
        var events = new ArrayList<String>();
        for (long now = 0; now <= 7000; now += 50) {
            String event = readout.tick(now, false);
            if (event != null) events.add(event);
        }
        assertEquals(List.of("yaku.riichi", "yaku.riichi"), events);
        assertTrue(readout.complete());
        assertEquals(1, readout.winner());
        var skipped = new ResultReadout(view, 0);
        skipped.tick(400, false);
        skipped.finish(450);
        assertEquals(1, skipped.visibleRows(1));
        assertNull(skipped.tick(10000, false));
        assertEquals(450, skipped.pointsAt());
    }

    @Test void gradesUseActualPaymentsAndCoverAllSupportedYakumanMultipliers() {
        String[] grades = {null, "mangan", "haneman", "baiman", "sanbaiman", "kazoe_yakuman"};
        int[] bases = {1920, 2000, 3000, 4000, 6000, 8000};
        for (int i = 0; i < bases.length; i++) {
            String expected = grades[i] == null ? null : "score." + grades[i];
            assertEquals(expected, ScoreAnnouncements.limit(new HandScore(13, 30, 0, bases[i] * 4, 0, 0, List.of(), 0), false));
            assertEquals(expected, ScoreAnnouncements.limit(new HandScore(13, 30, 0, bases[i] * 6, 0, 0, List.of(), 0), true));
            assertEquals(expected, ScoreAnnouncements.limit(new HandScore(13, 30, 0, 0, bases[i] * 2, bases[i], List.of(), 0), false));
        }
        for (int count = 1; count <= 6; count++) {
            String event = ScoreAnnouncements.limit(new HandScore(13 * count, 0, count, 32000 * count, 0, 0, List.of(), 0), false);
            assertEquals("score.yakuman" + (count == 1 ? "" : "_" + count), event);
            assertTrue(ScoreAnnouncements.SUBTITLES.containsKey(event));
        }
    }

    @Test void doubleRiichiUsesThePublicDeclarationAndKeepsItsSettlementRecordingSeparate() {
        var before = view(1, 1, Game.Phase.TURN, seats(), "playing");
        var seats = seats();
        seats.set(1, new TableView.Seat(false, "Player", true, false, false, 24000,
            List.of(), -2, List.of(), List.of(new Discard(12, true, false, true)), List.of(), true, false, true));
        var after = view(2, 1, Game.Phase.REACTION, seats, "playing");
        var cue = TableAudioEvents.between(before, after).getLast();
        assertEquals("riichi", cue.sound());
        assertEquals("double_riichi", cue.voice());
        assertEquals("yaku.double_riichi", ScoreAnnouncements.yaku("WRichi"));
        assertTrue(TableAudioEvents.between(after, view(3, 1, Game.Phase.REACTION, seats, "playing")).isEmpty());
    }

    @Test void firstObservationRepeatedAndCosmeticSnapshotsAreSilent() {
        var view = view(1, 1, Game.Phase.TURN, seats(), "playing");
        assertTrue(sounds(null, view).isEmpty());
        assertTrue(sounds(view, view).isEmpty());
        assertTrue(sounds(view, view(2, 1, Game.Phase.TURN, seats(), "playing")).isEmpty());
    }

    @Test void openingPlaysOneWallAndOneDealRatherThanDozensOfTileSounds() {
        var cues = TableAudioEvents.between(view(1, 0, Game.Phase.LOBBY, seats(), "lobby"),
            view(2, 1, Game.Phase.TURN, seats(), "playing"));
        assertEquals(List.of("wall", "deal"), cues.stream().map(TableAudioEvents.Cue::sound).toList());
        assertEquals(10, cues.getLast().delay());
    }

    @Test void riichiAndBothDiscardTypesAreEmittedOnlyForNewRiverEntries() {
        var before = view(1, 1, Game.Phase.TURN, seats(), "playing");
        var seats = seats();
        seats.set(0, seat(List.of(new Discard(12, true, false, true)), List.of(), List.of()));
        var after = view(2, 1, Game.Phase.REACTION, seats, "playing");
        assertEquals(List.of("tsumogiri", "riichi"), sounds(before, after));
        seats.set(0, seat(List.of(new Discard(12, true, true, true)), List.of(), List.of()));
        assertTrue(sounds(after, view(3, 1, Game.Phase.REACTION, seats, "playing")).isEmpty());
        seats.set(0, seat(List.of(new Discard(12, false, false, false)), List.of(), List.of()));
        assertEquals(List.of("tedashi"), sounds(before, view(2, 1, Game.Phase.REACTION, seats, "playing")));
    }

    @Test void addedKanDoesNotRepeatPonAndNukiHasItsOwnVoice() {
        var seats = seats();
        seats.set(1, seat(List.of(), List.of(new Meld(Meld.Type.PON, List.of(40, 41, 42), 0, 40)), List.of()));
        var before = view(1, 1, Game.Phase.TURN, seats, "playing");
        seats.set(1, seat(List.of(), List.of(new Meld(Meld.Type.ADDED_KAN, List.of(40, 41, 42, 43), 0, 40)), List.of()));
        seats.set(2, seat(List.of(), List.of(), List.of(120)));
        assertEquals(List.of("kan", "nuki"), sounds(before, view(2, 1, Game.Phase.TURN, seats, "playing")));
    }

    @Test void replacingACalledRiichiDiscardDoesNotRepeatTheDeclarationVoice() {
        var seats = seats();
        var called = new Discard(12, true, true, true);
        seats.set(0, new TableView.Seat(false, "Player", true, false, false, 24000, List.of(), -2,
            List.of(), List.of(called), List.of(), true, false, false));
        var before = view(1, 1, Game.Phase.TURN, seats, "playing");
        seats.set(0, new TableView.Seat(false, "Player", true, false, false, 24000, List.of(), -2,
            List.of(), List.of(called, new Discard(20, true, false, true)), List.of(), true, false, false));
        assertEquals(List.of("tsumogiri"), sounds(before, view(2, 1, Game.Phase.REACTION, seats, "playing")));
    }

    @Test void winDrawAndFinalMatchCuesAreDistinctAndDoNotRepeatForReadyVotes() {
        var before = view(1, 1, Game.Phase.TURN, seats(), "playing");
        for (String reason : List.of("exhaustive", "four_kans", "nagashi"))
            assertEquals(List.of("draw_end"), sounds(before, view(2, 1, Game.Phase.HAND_END, seats(), reason)));
        for (String reason : List.of("ron", "tsumo")) {
            var after = view(2, 1, Game.Phase.MATCH_END, seats(), reason);
            assertEquals(List.of(reason), sounds(before, after));
            assertTrue(sounds(after, view(3, 1, Game.Phase.MATCH_END, seats(), reason)).isEmpty());
        }
    }
}
