package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Public room information and recipient-specific room controls; never contains rule-state tiles. */
public record TableRoomView(UUID tableId, UUID incarnation, long revision, long decision,
                            MahjongVariant variant, TableSession.Lifecycle lifecycle,
                            int host, int viewerSeat, boolean manual, boolean equipped, boolean paused,
                            RoomSeating.Stage seating, List<Integer> availableWinds, List<Seat> seats,
                            List<RoomAction> actions, ExitVote exitVote, boolean leaveDecision,
                            boolean convenienceHints, boolean allowConvenienceHints, MatchAutomation automation,
                            List<PreparationProblem> equipmentProblems) {
    public TableRoomView {
        Objects.requireNonNull(tableId);
        Objects.requireNonNull(incarnation);
        Objects.requireNonNull(variant);
        Objects.requireNonNull(lifecycle);
        Objects.requireNonNull(seating);
        availableWinds = List.copyOf(availableWinds);
        seats = List.copyOf(seats);
        actions = List.copyOf(actions);
        equipmentProblems = List.copyOf(equipmentProblems);
        if (revision < 1 || decision < 1 || seats.size() < 3 || seats.size() > 4
            || host < -1 || host >= seats.size() || viewerSeat < -1 || viewerSeat >= seats.size()
            || viewerSeat < 0 && (!actions.isEmpty() || leaveDecision || automation != null)
            || automation != null && (manual || lifecycle == TableSession.Lifecycle.LOBBY || seats.get(viewerSeat).participant().bot())
            || convenienceHints && !allowConvenienceHints
            || variant != MahjongVariant.RIICHI && (manual || seats.size() != 4))
            throw new IllegalArgumentException("Invalid room view");
    }

    public boolean lobby() { return lifecycle == TableSession.Lifecycle.LOBBY; }

    public TableRoomView withEquipmentProblems(List<PreparationProblem> problems) {
        return new TableRoomView(tableId, incarnation, revision, decision, variant, lifecycle, host, viewerSeat,
            manual, equipped, paused, seating, availableWinds, seats, actions, exitVote, leaveDecision,
            convenienceHints, allowConvenienceHints, automation, problems);
    }

    public record Seat(TableParticipant participant, PlayerPresence presence, int wind) {
        public Seat {
            Objects.requireNonNull(participant);
            if (wind < -1 || wind > 3) throw new IllegalArgumentException("Invalid room wind");
        }
    }
}
