package top.skyeyefast.mchjong.client;

import top.skyeyefast.mchjong.engine.TableView;

/** One outstanding action per authoritative choice set; heartbeats are not acknowledgements. */
public final class TableDecision {
    private TableView view;
    private boolean pending;

    public boolean pending() { return pending; }

    public boolean receive(TableView next) {
        if (view != null && next != null && view.tableId().equals(next.tableId()) && next.revision() < view.revision()) return false;
        boolean changed = !sameDecision(view, next);
        if (changed) pending = false;
        view = next;
        return changed;
    }

    public boolean submit(TableView offered, int index) {
        if (pending || !sameDecision(view, offered) || index < 0 || index >= view.actions().size()) return false;
        pending = true;
        return true;
    }

    private static boolean sameDecision(TableView first, TableView second) {
        return first != null && second != null && first.tableId().equals(second.tableId())
            && first.viewerSeat() == second.viewerSeat() && first.decision() == second.decision()
            && first.actions().equals(second.actions());
    }
}
