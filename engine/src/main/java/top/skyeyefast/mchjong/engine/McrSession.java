package top.skyeyefast.mchjong.engine;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** MCR match state and hand acknowledgements; room authority belongs to TableSession. */
public final class McrSession extends TableSession {
    private McrGame game;
    private List<Integer> stock = List.of();
    private int confirmed;

    public McrSession(UUID tableId, long seed) {
        super(tableId, MahjongVariant.MCR, 4, seed);
    }

    /** Copy the prepared room roster in seat order; never populate it from a client action. */
    public static McrSession start(UUID tableId, List<TableParticipant> participants, long seed, List<Integer> stock) {
        var roster = roster(participants);
        var session = new McrSession(tableId, seed);
        for (int seat = 0; seat < 4; seat++) session.participants[seat] = Participant.restore(roster.get(seat));
        session.hostId = roster.get(0).id();
        session.seating.positioned(4);
        session.configureEquipment(false, stock);
        session.startMatch();
        return session;
    }

    /** Presence is deliberately not restored. Old requests cannot target the new incarnation. */
    public static McrSession restore(State state) {
        Objects.requireNonNull(state);
        var session = new McrSession(state.room().tableId(), state.room().seed());
        session.restoreRoom(state.room());
        session.stock = state.stock();
        session.game = state.game() == null ? null : McrGame.restore(state.game());
        session.confirmed = state.confirmed();
        if (session.game != null) roster(session.participants());
        return session;
    }

    /** Authenticated sender only; no seat number or tile identity is accepted from the request. */
    public boolean act(UUID actor, UUID expectedTable, UUID expectedIncarnation, long decision, int actionIndex) {
        if (game == null || exitVote != null) return false;
        int seat = authorize(actor, expectedTable, expectedIncarnation, decision, game.decision(), true);
        if (seat < 0 || !game.act(seat, decision, actionIndex)) return false;
        if (game.phase() == McrGame.Phase.MATCH_END) lifecycle = Lifecycle.FINISHED;
        changed(false);
        return true;
    }

    /** All four participants acknowledge the completed hand before the next one is dealt. */
    public boolean confirmNextHand(UUID actor, UUID expectedTable, UUID expectedIncarnation, long decision) {
        if (game == null || exitVote != null) return false;
        int seat = authorize(actor, expectedTable, expectedIncarnation, decision, game.decision(), true);
        if (seat < 0 || game.phase() != McrGame.Phase.HAND_END || (confirmed & (1 << seat)) != 0) return false;
        confirmed |= 1 << seat;
        if (confirmed == 15) {
            game.nextHand();
            confirmed = 0;
        }
        changed(false);
        return true;
    }

    public boolean equipped() { return Tile.validMcrSet(stock); }

    public boolean configureEquipment(boolean manual, List<Integer> tiles) {
        Objects.requireNonNull(tiles);
        if (manual || !lobby()) return false;
        if (!tiles.isEmpty() && !Tile.validMcrSet(tiles))
            throw new IllegalArgumentException("An MCR table requires all 144 physical tiles");
        if (stock.equals(tiles)) return false;
        stock = List.copyOf(tiles);
        resetReadiness();
        changed(true);
        return true;
    }

    protected void startMatch() {
        if (!equipped()) throw new IllegalStateException("Cannot start MCR without its complete stock");
        roster(participants());
        game = McrGame.fromStock(seed, stock);
        confirmed = 0;
        lifecycle = Lifecycle.PLAYING;
        renewIncarnation();
    }

    protected void clearMatch() {
        game = null;
        confirmed = 0;
    }

    public void tick() { tickRoom(); }

    @Override protected boolean pauseForAbsence() { return seated() != 15; }
    @Override protected boolean canReturnToLobby(int seat) { return seat == host(); }

    /** An absent participant gets the same concealed-data protection as an unprivileged spectator. */
    public View view(UUID recipient) {
        if (game == null) return null;
        return new View(tableId, incarnation, revision, participants(), seated(), confirmed,
            McrView.project(game, viewerSeat(recipient), seated() == 15 && exitVote == null));
    }

    public State save() {
        return new State(State.FORMAT, saveRoom(), stock, confirmed, game == null ? null : game.save());
    }

    /** A private envelope, not a wire view. No mount state or reusable request incarnation is persisted. */
    public record State(int format, TableSession.State room, List<Integer> stock, int confirmed, McrGameState game) {
        public static final int FORMAT = 5;

        public State {
            if (format != FORMAT) throw new IllegalArgumentException("Unsupported MCR session format");
            Objects.requireNonNull(room);
            stock = List.copyOf(stock);
            if (room.variant() != MahjongVariant.MCR || room.capacity() != 4 || room.manual()
                || !stock.isEmpty() && !Tile.validMcrSet(stock)
                || confirmed < 0 || confirmed >= 15 || confirmed != 0 && (game == null || game.phase() != McrGame.Phase.HAND_END)
                || (room.lifecycle() == Lifecycle.LOBBY) != (game == null)
                || game != null && (!Tile.validMcrSet(stock)
                    || (room.lifecycle() == Lifecycle.FINISHED) != (game.phase() == McrGame.Phase.MATCH_END)))
                throw new IllegalArgumentException("Invalid MCR session state");
            if (game != null) roster(room.participants());
        }
    }

    /** Identity-bound recipient projection. Access to private game state is never part of this contract. */
    public record View(UUID tableId, UUID incarnation, long revision, List<TableParticipant> participants,
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

    private static List<TableParticipant> roster(List<TableParticipant> participants) {
        var copy = List.copyOf(participants);
        var identities = new HashSet<UUID>();
        if (copy.size() != 4 || copy.stream().anyMatch(player -> player.id() == null || player.bot() || !identities.add(player.id())))
            throw new IllegalArgumentException("An MCR session requires four distinct participants in seat order");
        return copy;
    }
}
