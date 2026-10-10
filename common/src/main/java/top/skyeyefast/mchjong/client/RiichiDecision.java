package top.skyeyefast.mchjong.client;

import top.skyeyefast.mchjong.engine.RiichiView;

/** One outstanding action per authoritative choice set; heartbeats are not acknowledgements. */
public final class RiichiDecision {
    private RiichiView view;
    private boolean pending;

    public boolean pending() { return pending; }

    /**
     * Accepts the snapshot unless its revision is older for the same table.
     * Returns whether local selection and pending state must reset, not whether the snapshot was accepted.
     * A table, viewer, token or action-list change resets that state; a null snapshot clears it too.
     * An unchanged choice set keeps the outstanding request pending, even at a newer revision.
     */
    public boolean receive(RiichiView next) {
        if (view != null && next != null && view.tableId().equals(next.tableId()) && next.revision() < view.revision()) return false;
        boolean changed = !sameDecision(view, next);
        if (changed) pending = false;
        view = next;
        return changed;
    }

    /** Marks one request pending if the offered choice set is current and the index is valid; does not send it. */
    public boolean submit(RiichiView offered, int index) {
        if (pending || !sameDecision(view, offered) || index < 0 || index >= view.actions().size()) return false;
        pending = true;
        return true;
    }

    private static boolean sameDecision(RiichiView first, RiichiView second) {
        return first != null && second != null && first.tableId().equals(second.tableId())
            && first.viewerSeat() == second.viewerSeat() && first.decision() == second.decision()
            && first.actions().equals(second.actions());
    }
}
