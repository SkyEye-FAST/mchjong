package top.skyeyefast.mchjong.client;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.RiichiAction;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.engine.RiichiView;
import static org.junit.jupiter.api.Assertions.*;

class RiichiDecisionTest {
    private static final UUID TABLE = new UUID(1, 7);
    private static final List<RiichiAction> DISCARD = List.of(new RiichiAction(RiichiAction.Type.DISCARD, 13));

    private static RiichiView view(UUID table, long revision, long decision, int viewer, List<RiichiAction> actions) {
        return new RiichiView(table, revision, decision, 1, RiichiPreset.MAHJONG_SOUL_4.config(), RiichiView.Phase.TURN,
            viewer, 0, 0, 0, 0, 0, 70, 0, List.of(), null, List.of(), actions, List.of(), "playing", List.of(), List.of(), List.of(),
            top.skyeyefast.mchjong.engine.TimeControl.DEFAULT, List.of(), List.of(), top.skyeyefast.mchjong.engine.PlayerHandVisibility.SELF, false, null, null, null, false, 1, java.util.Map.of(), List.of(), 0, 0);
    }

    @Test void aRequestCanOnlyBeSentOnceAndHeartbeatsDoNotUnlockIt() {
        var gate = new RiichiDecision();
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
        var gate = new RiichiDecision();
        var base = view(TABLE, 2, 2, 0, List.of(new RiichiAction(RiichiAction.Type.PASS)));
        gate.receive(base);
        assertTrue(gate.submit(base, 0));
        assertTrue(gate.receive(view(TABLE, 3, 2, 0, List.of())));
        assertFalse(gate.pending());
        assertFalse(gate.submit(base, 0));
    }

    @Test void oldButtonIndicesCannotBeSubmittedForANewDecision() {
        var gate = new RiichiDecision();
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
        var gate = new RiichiDecision();
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
