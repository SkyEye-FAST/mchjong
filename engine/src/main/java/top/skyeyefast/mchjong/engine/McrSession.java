package top.skyeyefast.mchjong.engine;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One room's fixed four-player match. The host supplies authenticated identities and live mounts. */
public final class McrSession {
    private final UUID tableId;
    private final UUID incarnation = UUID.randomUUID();
    private final List<Participant> participants;
    private final McrGame game;
    private long revision;
    private int seated;
    private int confirmed;

    /** Copy the prepared room roster in seat order; never populate it from a client action. */
    public static McrSession start(UUID tableId, List<Participant> participants, long seed, List<Integer> stock) {
        Objects.requireNonNull(tableId);
        var roster = roster(participants);
        return new McrSession(tableId, roster, McrGame.fromStock(seed, stock), 1, 0);
    }

    private McrSession(UUID tableId, List<Participant> participants, McrGame game, long revision, int confirmed) {
        this.tableId = tableId;
        this.participants = participants;
        this.game = game;
        this.revision = revision;
        this.confirmed = confirmed;
    }

    /** Presence is deliberately not restored. Old requests cannot target the new incarnation. */
    public static McrSession restore(State state) {
        Objects.requireNonNull(state);
        return new McrSession(state.tableId(), state.participants(), McrGame.restore(state.game()),
            Math.addExact(state.revision(), 1), state.confirmed());
    }

    public UUID tableId() { return tableId; }
    public UUID incarnation() { return incarnation; }

    public int seatOf(UUID player) {
        if (player != null) for (int seat = 0; seat < 4; seat++)
            if (participants.get(seat).id().equals(player)) return seat;
        return -1;
    }

    /** Server-observed UUID -> physical seat at this table. Foreign or displaced mounts grant no access. */
    public boolean synchronizeSeats(Map<UUID, Integer> mounted) {
        var observed = Map.copyOf(mounted);
        int next = 0;
        for (int seat = 0; seat < 4; seat++) {
            int expected = seat;
            long occupants = observed.values().stream().filter(value -> value == expected).count();
            if (occupants == 1 && Objects.equals(observed.get(participants.get(seat).id()), seat)) next |= 1 << seat;
        }
        if (seated == next) return false;
        seated = next;
        revision = Math.addExact(revision, 1);
        return true;
    }

    /** Authenticated sender only; no seat number or tile identity is accepted from the request. */
    public boolean act(UUID actor, UUID expectedTable, UUID expectedIncarnation, long decision, int actionIndex) {
        int seat = authorized(actor, expectedTable, expectedIncarnation, decision);
        if (seat < 0 || !game.act(seat, decision, actionIndex)) return false;
        revision = Math.addExact(revision, 1);
        return true;
    }

    /** All four participants acknowledge the completed hand before the next one is dealt. */
    public boolean confirmNextHand(UUID actor, UUID expectedTable, UUID expectedIncarnation, long decision) {
        int seat = authorized(actor, expectedTable, expectedIncarnation, decision);
        if (seat < 0 || game.phase() != McrGame.Phase.HAND_END || (confirmed & (1 << seat)) != 0) return false;
        confirmed |= 1 << seat;
        if (confirmed == 15) {
            game.nextHand();
            confirmed = 0;
        }
        revision = Math.addExact(revision, 1);
        return true;
    }

    private int authorized(UUID actor, UUID expectedTable, UUID expectedIncarnation, long decision) {
        if (!tableId.equals(expectedTable) || !incarnation.equals(expectedIncarnation)
            || decision != game.decision() || seated != 15) return -1;
        return seatOf(actor);
    }

    /** An absent participant gets the same concealed-data protection as an unprivileged spectator. */
    public View view(UUID recipient) {
        int seat = seatOf(recipient);
        if (seat >= 0 && (seated & (1 << seat)) == 0) seat = -1;
        return new View(tableId, incarnation, revision, participants, seated, confirmed,
            McrView.project(game, seat, seated == 15));
    }

    public State save() { return new State(State.FORMAT, tableId, revision, participants, confirmed, game.save()); }

    public record Participant(UUID id, String name) {
        public Participant {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
            if (name.isBlank() || name.length() > 64 || name.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Invalid MCR participant name");
        }
    }

    /** A private envelope, not a wire view. No mount state or reusable request incarnation is persisted. */
    public record State(int format, UUID tableId, long revision, List<Participant> participants,
                        int confirmed, McrGameState game) {
        public static final int FORMAT = 2;

        public State {
            if (format != FORMAT) throw new IllegalArgumentException("Unsupported MCR session format");
            Objects.requireNonNull(tableId);
            participants = roster(participants);
            Objects.requireNonNull(game);
            if (revision < 1 || revision >= Long.MAX_VALUE - 1 || confirmed < 0 || confirmed >= 15
                || confirmed != 0 && game.phase() != McrGame.Phase.HAND_END)
                throw new IllegalArgumentException("Invalid MCR session state");
        }
    }

    /** Identity-bound recipient projection. Access to private game state is never part of this contract. */
    public record View(UUID tableId, UUID incarnation, long revision, List<Participant> participants,
                       int seated, int confirmed, McrView game) {
        public View {
            Objects.requireNonNull(tableId);
            Objects.requireNonNull(incarnation);
            participants = roster(participants);
            Objects.requireNonNull(game);
            if (revision < 1 || seated < 0 || seated > 15 || confirmed < 0 || confirmed >= 15
                || confirmed != 0 && game.phase() != McrGame.Phase.HAND_END
                || game.viewerSeat() >= 0 && (seated & (1 << game.viewerSeat())) == 0
                || seated != 15 && !game.actions().isEmpty())
                throw new IllegalArgumentException("Invalid MCR session view");
        }

        public boolean paused() { return seated != 15 && game.phase() != McrGame.Phase.MATCH_END; }
        public boolean canConfirmNextHand() {
            return seated == 15 && game.viewerSeat() >= 0 && game.phase() == McrGame.Phase.HAND_END
                && (confirmed & (1 << game.viewerSeat())) == 0;
        }
    }

    private static List<Participant> roster(List<Participant> participants) {
        var copy = List.copyOf(participants);
        var identities = new HashSet<UUID>();
        if (copy.size() != 4 || copy.stream().anyMatch(player -> !identities.add(player.id())))
            throw new IllegalArgumentException("An MCR session requires four distinct participants in seat order");
        return copy;
    }
}
