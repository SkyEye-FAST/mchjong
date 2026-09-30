package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SichuanSession extends TableSession {
    private static final int SETTLEMENT_TICKS = 200;
    private SichuanRules rules = SichuanPreset.SBR_2025.config();
    private SichuanGame game;
    private List<Integer> stock = List.of();
    private TimeControl timeControl = TimeControl.DEFAULT;
    private final List<TimeControl.Clock> clocks = new ArrayList<>();
    private int age;
    private int confirmed;

    public SichuanSession(UUID tableId, long seed) { super(tableId, MahjongVariant.SICHUAN, 4, seed); }
    public SichuanGame game() { return game; }
    public SichuanRules rules() { return rules; }
    public TimeControl timeControl() { return timeControl; }
    protected boolean pauseForAbsence() { return !hasSeatedHuman(); }
    protected boolean canReturnToLobby(int seat) { return seat == host(); }
    public boolean equipped() { return Tile.validSichuanSet(stock); }
    public boolean configureEquipment(boolean manual, List<Integer> tiles) {
        Objects.requireNonNull(tiles);
        if (manual || !lobby() || exitVote != null) return false;
        if (!tiles.isEmpty() && !Tile.validSichuanSet(tiles)) throw new IllegalArgumentException("Sichuan requires 108 suited tiles");
        if (stock.equals(tiles)) return false;
        stock = List.copyOf(tiles); resetReadiness(); changed(true); return true;
    }
    public boolean configureClock(UUID actor, TimeControl control) {
        if (!lobby() || exitVote != null || !isHost(actor)) return false;
        timeControl = Objects.requireNonNull(control); resetReadiness(); changed(true); return true;
    }
    public boolean configureRules(UUID actor, long expectedDecision, SichuanRules rules) {
        if (!lobby() || exitVote != null || !isHost(actor) || expectedDecision != decision || !worldPolicy.allowCustomRules()) return false;
        this.rules = Objects.requireNonNull(rules); resetReadiness(); changed(true); return true;
    }
    protected void startMatch() {
        if (!equipped()) throw new IllegalStateException("Cannot start Sichuan without stock");
        game = new SichuanGame(seed, rules, stock); lifecycle = Lifecycle.PLAYING;
        for (int seat = 0; seat < 4; seat++) clocks.add(new TimeControl.Clock(timeControl.moveSeconds() * 20, timeControl.reserveSeconds() * 20, false));
        age = 0; confirmed = 0; renewIncarnation();
    }
    protected void clearMatch() { game = null; clocks.clear(); age = 0; confirmed = 0; }
    public boolean confirmNextHand(UUID actor, UUID expectedTable, UUID expectedIncarnation, long expectedDecision) {
        if (game == null || paused() || exitVote != null || game.phase() != SichuanGame.Phase.HAND_END) return false;
        int seat = authorize(actor, expectedTable, expectedIncarnation, expectedDecision, game.decision());
        if (seat < 0 || (confirmed & (1 << seat)) != 0) return false;
        confirmed |= 1 << seat;
        if (confirmed == 15) nextHand();
        changed(false); return true;
    }
    private void nextHand() {
        if (!game.nextHand()) throw new IllegalStateException("Sichuan hand cannot advance");
        age = 0; confirmed = 0;
        for (int seat = 0; seat < 4; seat++) clocks.set(seat,
            new TimeControl.Clock(timeControl.moveSeconds() * 20, timeControl.reserveSeconds() * 20, false));
        changed(false);
    }
    public boolean act(UUID actor, UUID expectedTable, UUID expectedIncarnation, long expectedDecision, int index) {
        if (game == null || paused() || exitVote != null) return false;
        int seat = authorize(actor, expectedTable, expectedIncarnation, expectedDecision, game.decision());
        return seat >= 0 && apply(seat, expectedDecision, index);
    }
    private boolean apply(int seat, long expectedDecision, int index) {
        if (!game.act(seat, expectedDecision, index)) return false;
        if (game.decision() != expectedDecision) {
            age = 0;
            for (int target = 0; target < 4; target++) clocks.set(target,
                new TimeControl.Clock(timeControl.moveSeconds() * 20, clocks.get(target).reserveTicks(), false));
        }
        if (game.phase() == SichuanGame.Phase.MATCH_END) lifecycle = Lifecycle.FINISHED;
        changed(false); return true;
    }
    public void tick() {
        if (!tickRoom() || game == null || lifecycle == Lifecycle.FINISHED) return;
        age = Math.min(14_400, age + 1);
        if (game.phase() == SichuanGame.Phase.HAND_END) {
            if (age >= SETTLEMENT_TICKS) nextHand();
            else if (age % 10 == 0) changed(false);
            return;
        }
        long expectedDecision = game.decision();
        for (int seat = 0; seat < 4; seat++) if (clockActive(seat)) {
            var clock = clocks.get(seat);
            var remaining = new TimeControl.Clock(clock.moveTicks(), clock.reserveTicks(), true).after(50);
            clocks.set(seat, new TimeControl.Clock(remaining.moveTicks(), remaining.reserveTicks(), false));
        }
        for (int seat = 0; seat < 4 && game.decision() == expectedDecision; seat++) {
            var actions = game.actions(seat);
            if (actions.isEmpty()) continue;
            if (actions.size() == 1 && actions.get(0).type() == SichuanAction.Type.DRAW) {
                if (age >= 12) apply(seat, expectedDecision, 0);
            } else if (clocks.get(seat).moveTicks() + clocks.get(seat).reserveTicks() == 0) {
                int fallback = -1;
                for (int index = 0; index < actions.size(); index++) {
                    var action = actions.get(index);
                    if (action.type() == SichuanAction.Type.PASS) { fallback = index; break; }
                    if (fallback < 0 && (action.type() == SichuanAction.Type.VOID_SUIT || action.type() == SichuanAction.Type.DISCARD)) fallback = index;
                }
                if (fallback >= 0) apply(seat, expectedDecision, fallback);
            }
        }
        if (age % 10 == 0) changed(false);
    }
    private boolean clockActive(int seat) {
        var actions = game.actions(seat);
        return !actions.isEmpty() && !(actions.size() == 1 && actions.get(0).type() == SichuanAction.Type.DRAW);
    }
    public View view(UUID recipient) {
        if (game == null) return null;
        boolean blocked = paused() || exitVote != null;
        var visibleClocks = new ArrayList<TimeControl.Clock>();
        for (int seat = 0; seat < 4; seat++) {
            var clock = clocks.get(seat);
            visibleClocks.add(new TimeControl.Clock(clock.moveTicks(), clock.reserveTicks(), !blocked && clockActive(seat)));
        }
        return new View(tableId, incarnation, revision, blocked, visibleClocks, confirmed,
            game.phase() == SichuanGame.Phase.HAND_END ? Math.max(0, SETTLEMENT_TICKS - age) : 0,
            SichuanView.project(game, viewerSeat(recipient), !blocked));
    }
    public State save() { return new State(State.FORMAT, saveRoom(), rules, stock, timeControl, clocks, age, confirmed, game == null ? null : game.save()); }
    public static SichuanSession restore(State state) {
        var session = new SichuanSession(state.room().tableId(), state.room().seed());
        session.restoreRoom(state.room()); session.rules = state.rules(); session.stock = state.stock(); session.timeControl = state.timeControl();
        session.clocks.addAll(state.clocks()); session.age = state.age(); session.confirmed = state.confirmed();
        session.game = state.game() == null ? null : SichuanGame.restore(state.game());
        return session;
    }
    public record State(int format, TableSession.State room, SichuanRules rules, List<Integer> stock,
                        TimeControl timeControl, List<TimeControl.Clock> clocks, int age, int confirmed, SichuanGame.State game) {
        public static final int FORMAT = 2;
        public State {
            Objects.requireNonNull(room); Objects.requireNonNull(rules); Objects.requireNonNull(timeControl);
            stock = List.copyOf(stock); clocks = List.copyOf(clocks);
            if (format != FORMAT || room.variant() != MahjongVariant.SICHUAN || room.capacity() != 4 || room.manual()
                || !stock.isEmpty() && !Tile.validSichuanSet(stock) || (room.lifecycle() == Lifecycle.LOBBY) != (game == null)
                || game != null && (!Tile.validSichuanSet(stock) || !game.rules().equals(rules)
                    || (room.lifecycle() == Lifecycle.FINISHED) != (game.phase() == SichuanGame.Phase.MATCH_END))
                || clocks.size() != (game == null ? 0 : 4) || age < 0 || age > 14_400 || game == null && age != 0
                || confirmed < 0 || confirmed >= 15 || (game == null || game.phase() != SichuanGame.Phase.HAND_END) && confirmed != 0
                || game != null && game.phase() == SichuanGame.Phase.HAND_END && age >= SETTLEMENT_TICKS
                || room.participants().stream().anyMatch(player -> player.bot())
                || game != null && room.participants().stream().anyMatch(player -> player.id() == null)
                || clocks.stream().anyMatch(clock -> clock.active() || clock.moveTicks() < 0 || clock.moveTicks() > timeControl.moveSeconds() * 20
                    || clock.reserveTicks() < 0 || clock.reserveTicks() > timeControl.reserveSeconds() * 20))
                throw new IllegalArgumentException("Invalid Sichuan session state");
        }
    }
    public record View(UUID tableId, UUID incarnation, long revision, boolean paused, List<TimeControl.Clock> clocks,
                       int confirmed, int settlementTicks, SichuanView game) {
        public View {
            Objects.requireNonNull(tableId); Objects.requireNonNull(incarnation); Objects.requireNonNull(game);
            clocks = List.copyOf(clocks);
            if (revision < 1 || clocks.size() != 4 || paused && (!game.actions().isEmpty() || clocks.stream().anyMatch(TimeControl.Clock::active)))
                throw new IllegalArgumentException("Invalid Sichuan session view");
            if (confirmed < 0 || confirmed >= 15 || settlementTicks < 0 || settlementTicks > SETTLEMENT_TICKS
                || game.phase() != SichuanGame.Phase.HAND_END && (confirmed != 0 || settlementTicks != 0)
                || game.phase() == SichuanGame.Phase.HAND_END && settlementTicks == 0)
                throw new IllegalArgumentException("Invalid Sichuan settlement view");
        }
    }
}
