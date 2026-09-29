package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Public room information and recipient-specific room controls; never contains rule-state tiles. */
public record TableRoomView(UUID tableId, UUID incarnation, long revision, long decision,
                            MahjongVariant variant, TableSession.Lifecycle lifecycle,
                            int host, int viewerSeat, boolean manual, boolean equipped, boolean paused,
                            RoomSeating.Stage seating, List<Integer> availableWinds, List<Seat> seats,
                            List<RoomAction> actions, ExitVote exitVote, boolean leaveDecision) {
    public TableRoomView {
        Objects.requireNonNull(tableId);
        Objects.requireNonNull(incarnation);
        Objects.requireNonNull(variant);
        Objects.requireNonNull(lifecycle);
        Objects.requireNonNull(seating);
        availableWinds = List.copyOf(availableWinds);
        seats = List.copyOf(seats);
        actions = List.copyOf(actions);
        if (revision < 1 || decision < 1 || seats.size() < 3 || seats.size() > 4
            || host < -1 || host >= seats.size() || viewerSeat < -1 || viewerSeat >= seats.size()
            || viewerSeat < 0 && (!actions.isEmpty() || leaveDecision)
            || variant == MahjongVariant.MCR && (manual || seats.size() != 4))
            throw new IllegalArgumentException("Invalid room view");
    }

    public boolean lobby() { return lifecycle == TableSession.Lifecycle.LOBBY; }

    public record Seat(TableParticipant participant, PlayerPresence presence, int wind) {
        public Seat {
            Objects.requireNonNull(participant);
            Objects.requireNonNull(presence);
            if (wind < -1 || wind > 3) throw new IllegalArgumentException("Invalid room wind");
        }
    }
}
