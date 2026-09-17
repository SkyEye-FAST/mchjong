package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.Discard;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.TimeControl;
import static org.junit.jupiter.api.Assertions.*;

class TableAudioEventsTest {
    private static final UUID TABLE = new UUID(6, 12);
    private static TableView.Seat seat(List<Discard> river, List<Meld> melds, List<Integer> norths) {
        return new TableView.Seat("Player", true, false, false, 25000,
            List.of(), -2, melds, river, norths, false, false);
    }
    private static TableView view(long revision, int hand, Game.Phase phase, List<TableView.Seat> seats, String result) {
        return new TableView(TABLE, revision, 1, hand, RuleSet.TENHOU_4, phase, 0,
            0, 0, 0, 0, 0, 70, 0, List.of(), null, seats, List.of(), List.of(), result, List.of(), List.of(),
            TimeControl.DEFAULT, List.of(), List.of(), false, null, null, null);
    }
    private static List<TableView.Seat> seats() {
        return new ArrayList<>(Collections.nCopies(4, seat(List.of(), List.of(), List.of())));
    }
    private static List<String> sounds(TableView before, TableView after) {
        return TableAudioEvents.between(before, after).stream().map(TableAudioEvents.Cue::sound).toList();
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
        seats.set(0, new TableView.Seat("Player", true, false, false, 24000, List.of(), -2,
            List.of(), List.of(called), List.of(), true, false));
        var before = view(1, 1, Game.Phase.TURN, seats, "playing");
        seats.set(0, new TableView.Seat("Player", true, false, false, 24000, List.of(), -2,
            List.of(), List.of(called, new Discard(20, true, false, true)), List.of(), true, false));
        assertEquals(List.of("tsumogiri"), sounds(before, view(2, 1, Game.Phase.REACTION, seats, "playing")));
    }

    @Test void winDrawAndFinalMatchCuesAreDistinctAndDoNotRepeatForReadyVotes() {
        var before = view(1, 1, Game.Phase.TURN, seats(), "playing");
        for (String reason : List.of("exhaustive", "four_kans", "nagashi"))
            assertEquals(List.of("draw_end"), sounds(before, view(2, 1, Game.Phase.HAND_END, seats(), reason)));
        for (String reason : List.of("ron", "tsumo")) {
            var after = view(2, 1, Game.Phase.MATCH_END, seats(), reason);
            assertEquals(List.of(reason, "match_end"), sounds(before, after));
            assertTrue(sounds(after, view(3, 1, Game.Phase.MATCH_END, seats(), reason)).isEmpty());
        }
    }
}
