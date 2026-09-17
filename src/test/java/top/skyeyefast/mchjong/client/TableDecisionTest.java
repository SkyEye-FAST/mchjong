package top.skyeyefast.mchjong.client;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import static org.junit.jupiter.api.Assertions.*;

class TableDecisionTest {
    private static final UUID TABLE = new UUID(1, 7);
    private static final List<Action> DISCARD = List.of(new Action(Action.Type.DISCARD, 13));

    private static TableView view(UUID table, long revision, long decision, int viewer, List<Action> actions) {
        return new TableView(table, revision, decision, 1, RuleSet.MAHJONG_SOUL_4, Game.Phase.TURN,
            viewer, 0, 0, 0, 0, 0, 70, 0, List.of(), null, List.of(), actions, List.of(), "playing", List.of(), List.of(),
            top.skyeyefast.mchjong.engine.TimeControl.DEFAULT, List.of(), List.of(), false, null, null, null);
    }

    @Test void aRequestCanOnlyBeSentOnceAndHeartbeatsDoNotUnlockIt() {
        var gate = new TableDecision();
        var base = view(TABLE, 2, 2, 0, DISCARD);
        assertTrue(gate.receive(base));
        assertTrue(gate.submit(base, 0));
        assertFalse(gate.submit(base, 0));
        assertFalse(gate.receive(view(TABLE, 2, 2, 0, DISCARD)));
        assertFalse(gate.receive(view(TABLE, 3, 2, 0, DISCARD)));
        assertTrue(gate.pending());
        assertFalse(gate.submit(base, 0));
    }

    @Test void anAcknowledgedReactionUnlocksWithoutChangingTheSharedDecision() {
        var gate = new TableDecision();
        var base = view(TABLE, 2, 2, 0, List.of(new Action(Action.Type.PASS)));
        gate.receive(base);
        assertTrue(gate.submit(base, 0));
        assertTrue(gate.receive(view(TABLE, 3, 2, 0, List.of())));
        assertFalse(gate.pending());
        assertFalse(gate.submit(base, 0));
    }

    @Test void oldButtonIndicesCannotBeSubmittedForANewDecision() {
        var gate = new TableDecision();
        var before = view(TABLE, 2, 2, 0, DISCARD);
        var after = view(TABLE, 3, 3, 0, DISCARD);
        gate.receive(before);
        assertTrue(gate.submit(before, 0));
        assertTrue(gate.receive(after));
        assertFalse(gate.pending());
        assertFalse(gate.submit(before, 0));
        assertTrue(gate.submit(after, 0));
        assertFalse(gate.receive(before));
        assertTrue(gate.pending());
    }

    @Test void tableIdentityViewingPermissionAndActionBoundsAreValidated() {
        var gate = new TableDecision();
        var base = view(TABLE, 2, 2, 0, DISCARD);
        gate.receive(base);
        assertFalse(gate.submit(base, -1));
        assertFalse(gate.submit(base, 1));
        assertFalse(gate.submit(view(UUID.randomUUID(), 2, 2, 0, DISCARD), 0));
        assertFalse(gate.submit(view(TABLE, 2, 2, 1, DISCARD), 0));
        assertTrue(gate.submit(base, 0));
        assertTrue(gate.receive(view(TABLE, 2, 2, -1, List.of())));
        assertFalse(gate.pending());
        assertFalse(gate.submit(base, 0));
        assertTrue(gate.receive(null));
        assertFalse(gate.submit(base, 0));
    }
}
