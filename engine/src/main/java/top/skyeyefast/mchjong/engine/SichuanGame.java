package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import static top.skyeyefast.mchjong.engine.SichuanAction.Type.*;

public final class SichuanGame {
    public enum Phase { VOIDING, TURN, REACTION, HAND_END }
    private final SichuanRules rules;
    private final SichuanWall wall;
    private final Player[] players = new Player[4];
    private Phase phase = Phase.VOIDING;
    private long revision = 1;
    private long decision = 1;
    private int turn;
    private int focus = Tile.ABSENT;
    private int supplier = -1;
    private int pendingKong = -1;
    private boolean replacementDraw;
    private boolean afterKong;
    private int kongLedgerStart = -1;
    private final SichuanAction[] responses = new SichuanAction[4];
    private final List<SichuanSettlement.Win> wins = new ArrayList<>();
    private final List<SichuanSettlement.Entry> ledger = new ArrayList<>();
    private SichuanSettlement.Result result;

    public SichuanGame(long seed) { this(seed, SichuanPreset.SBR_2025.config(), Tile.sichuanSet()); }
    public SichuanGame(long seed, SichuanRules rules, List<Integer> stock) {
        this.rules = Objects.requireNonNull(rules);
        wall = new SichuanWall(seed, 0, stock);
        for (int seat = 0; seat < 4; seat++) players[seat] = new Player();
        for (int packet = 0; packet < 3; packet++)
            for (int seat = 0; seat < 4; seat++)
                for (int tile = 0; tile < 4; tile++) players[seat].hand.add(wall.draw());
        players[0].hand.add(wall.take(48));
        players[0].drawn = wall.take(52);
        players[0].hand.add(players[0].drawn);
        for (int seat = 1; seat < 4; seat++) players[seat].hand.add(wall.draw());
        for (var player : players) player.hand.sort(Tile.ORDER);
        validate();
    }

    private SichuanGame(State state) {
        rules = state.rules(); wall = SichuanWall.restore(state.wall());
        phase = state.phase(); revision = state.revision(); decision = state.decision(); turn = state.turn();
        focus = state.focus(); supplier = state.supplier(); pendingKong = state.pendingKong();
        replacementDraw = state.replacementDraw(); afterKong = state.afterKong(); kongLedgerStart = state.kongLedgerStart();
        for (int seat = 0; seat < 4; seat++) players[seat] = new Player(state.players().get(seat));
        for (var response : state.responses()) {
            if (response.seat() < 0 || response.seat() > 3 || responses[response.seat()] != null)
                throw new IllegalArgumentException("Invalid Sichuan response seat");
            responses[response.seat()] = Objects.requireNonNull(response.action());
        }
        wins.addAll(state.wins()); ledger.addAll(state.ledger()); result = state.result();
        validate();
        revision = Math.addExact(revision, 1); decision = Math.addExact(decision, 1);
    }
    public static SichuanGame restore(State state) { return new SichuanGame(Objects.requireNonNull(state)); }
    public Phase phase() { return phase; }
    public long revision() { return revision; }
    public long decision() { return decision; }
    public int turn() { return turn; }
    public SichuanRules rules() { return rules; }
    public SichuanSettlement.Result result() { return result; }
    public SichuanView view(int seat) { return SichuanView.project(this, seat, true); }
    public List<SichuanAction> actions(int seat) {
        if (seat < 0 || seat > 3 || players[seat].won || phase == Phase.HAND_END) return List.of();
        if (phase == Phase.REACTION && responses[seat] != null) return List.of();
        return legalActions(seat);
    }

    private List<SichuanAction> legalActions(int seat) {
        var player = players[seat];
        if (phase == Phase.VOIDING) return player.voidSuit >= 0 ? List.of() : List.of(
            new SichuanAction(VOID_SUIT, List.of(), 0), new SichuanAction(VOID_SUIT, List.of(), 1),
            new SichuanAction(VOID_SUIT, List.of(), 2));
        var actions = new ArrayList<SichuanAction>();
        if (phase == Phase.REACTION) {
            if (seat == supplier || player.won) return List.of();
            var score = winScore(seat, focus, true);
            if (score != null && score.fan() > player.passedFan) actions.add(new SichuanAction(WIN));
            if (pendingKong < 0 && Tile.kind(focus) / 9 != player.voidSuit) {
                var matching = matching(player, Tile.kind(focus));
                if (matching.size() >= 2) actions.add(new SichuanAction(PUNG, matching.subList(0, 2)));
                if (matching.size() == 3 && wall.remaining() > 0) actions.add(new SichuanAction(DISCARD_KONG, matching));
            }
            if (!actions.isEmpty()) actions.add(new SichuanAction(PASS));
            return List.copyOf(actions);
        }
        if (phase != Phase.TURN || seat != turn) return List.of();
        if (player.hand.size() + 3 * player.melds.size() == 13) return List.of(new SichuanAction(DRAW));
        if (player.drawn != Tile.ABSENT && winScore(seat, player.drawn, false) != null) actions.add(new SichuanAction(WIN));
        boolean missing = player.hand.stream().anyMatch(tile -> Tile.kind(tile) / 9 == player.voidSuit);
        for (int tile : player.hand) if (!missing || Tile.kind(tile) / 9 == player.voidSuit)
            actions.add(new SichuanAction(DISCARD, List.of(tile)));
        if (player.drawn != Tile.ABSENT && wall.remaining() > 0) {
            for (int kind = 0; kind < 27; kind++) if (kind / 9 != player.voidSuit) {
                var matching = matching(player, kind);
                if (matching.size() == 4) actions.add(new SichuanAction(CONCEALED_KONG, matching));
                if (matching.size() == 1 && player.melds.stream().anyMatch(meld -> meld.type() == Meld.Type.TRIPLET
                    && meld.kind() == Tile.kind(matching.get(0)))) actions.add(new SichuanAction(ADDED_KONG, matching));
            }
        }
        return List.copyOf(actions);
    }

    private static List<Integer> matching(Player player, int kind) {
        return player.hand.stream().filter(tile -> Tile.kind(tile) == kind).sorted().toList();
    }

    private SichuanSettlement.Score winScore(int seat, int tile, boolean claim) {
        var player = players[seat];
        var hand = new ArrayList<>(player.hand);
        if (claim) hand.add(tile);
        if (player.voidSuit < 0 || hand.stream().anyMatch(owned -> Tile.kind(owned) / 9 == player.voidSuit)
            || player.melds.stream().anyMatch(meld -> meld.kind() / 9 == player.voidSuit)) return null;
        return SichuanHandAnalyzer.score(hand, player.melds, rules, !claim && afterKong,
            claim && afterKong, claim && pendingKong >= 0, wall.remaining() == 0);
    }

    public boolean act(int seat, long expectedDecision, int index) {
        if (expectedDecision != decision) return false;
        var offered = actions(seat);
        if (index < 0 || index >= offered.size()) return false;
        var action = offered.get(index);
        if (phase == Phase.REACTION) {
            var score = winScore(seat, focus, true);
            if (action.type() != WIN && score != null) players[seat].passedFan = Math.max(players[seat].passedFan, score.fan());
            responses[seat] = action;
            if (responsesComplete()) { resolveResponses(); changed(); }
            else revision = Math.addExact(revision, 1);
            return true;
        }
        switch (action.type()) {
            case VOID_SUIT -> {
                players[seat].voidSuit = action.suit();
                if (Arrays.stream(players).allMatch(player -> player.voidSuit >= 0)) { phase = Phase.TURN; changed(); }
                else revision = Math.addExact(revision, 1);
                return true;
            }
            case DRAW -> draw();
            case DISCARD -> discard(action.tiles().get(0));
            case CONCEALED_KONG -> {
                var player = players[seat];
                player.hand.removeAll(action.tiles());
                player.melds.add(new Meld(Meld.Type.CONCEALED_QUAD, action.tiles(), seat, Tile.ABSENT));
                kongLedgerStart = ledger.size();
                payActive(SichuanSettlement.Type.CONCEALED_KONG, seat, rules.concealedKongPayment());
                replacement();
            }
            case ADDED_KONG -> {
                pendingKong = meldIndex(players[seat], action.tiles().get(0));
                openResponses(seat, action.tiles().get(0));
            }
            case WIN -> {
                var score = Objects.requireNonNull(winScore(seat, players[seat].drawn, false));
                wins.add(new SichuanSettlement.Win(seat, seat, players[seat].drawn, true, false, score));
                payActive(SichuanSettlement.Type.SELF_DRAW_WIN, seat, score.value() + rules.selfDrawBonus());
                players[seat].won = true;
                if (wins.size() == 3) finish(false);
                else advance(seat);
            }
            default -> throw new IllegalStateException("Unexpected Sichuan turn action");
        }
        changed();
        return true;
    }

    private void draw() {
        int tile = wall.draw();
        if (tile == Tile.ABSENT) { finish(true); return; }
        var player = players[turn];
        player.hand.add(tile); player.hand.sort(Tile.ORDER); player.drawn = tile; player.passedFan = -1;
        afterKong = replacementDraw;
        replacementDraw = false;
    }
    private void discard(int tile) {
        var player = players[turn];
        player.hand.remove(Integer.valueOf(tile)); player.drawn = Tile.ABSENT;
        int ready = SichuanHandAnalyzer.readyValue(player.hand, player.melds, player.voidSuit, rules);
        if (ready == 0) player.passedFan = -1;
        var skipped = winScore(turn, tile, true);
        if (skipped != null) player.passedFan = Math.max(player.passedFan, skipped.fan());
        player.river.add(new SichuanPlayerState.Discard(tile, false));
        pendingKong = -1;
        openResponses(turn, tile);
    }
    private void openResponses(int from, int tile) {
        phase = Phase.REACTION; supplier = from; focus = tile;
        Arrays.fill(responses, null);
        for (int seat = 0; seat < 4; seat++) if (legalActions(seat).isEmpty()) responses[seat] = new SichuanAction(PASS);
        if (responsesComplete()) resolveResponses();
    }
    private boolean responsesComplete() { return Arrays.stream(responses).allMatch(Objects::nonNull); }

    private void resolveResponses() {
        int lastWinner = -1;
        for (int step = 1; step < 4; step++) {
            int seat = (supplier + step) % 4;
            if (responses[seat].type() != WIN) continue;
            var score = Objects.requireNonNull(winScore(seat, focus, true));
            wins.add(new SichuanSettlement.Win(seat, supplier, focus, false, pendingKong >= 0, score));
            transfer(SichuanSettlement.Type.DISCARD_WIN, supplier, seat, score.value(), -1);
            players[seat].won = true;
            lastWinner = seat;
        }
        if (lastWinner >= 0) {
            if (pendingKong >= 0) {
                players[supplier].hand.remove(Integer.valueOf(focus));
                players[supplier].drawn = Tile.ABSENT;
            }
            else claimDiscard();
            players[lastWinner].hand.add(focus); players[lastWinner].hand.sort(Tile.ORDER);
            if (afterKong && rules.refundKongOnShoot()) refundKongs(supplier, kongLedgerStart);
            clearReaction();
            if (wins.size() == 3) finish(false);
            else advance(lastWinner);
            return;
        }
        if (pendingKong >= 0) {
            int owner = supplier;
            var player = players[owner];
            var pung = player.melds.get(pendingKong);
            var tiles = new ArrayList<>(pung.tiles()); tiles.add(focus);
            player.melds.set(pendingKong, new Meld(Meld.Type.ADDED_QUAD, tiles, pung.fromSeat(), pung.calledTile()));
            player.hand.remove(Integer.valueOf(focus));
            kongLedgerStart = ledger.size();
            if (focus == player.drawn) payActive(SichuanSettlement.Type.ADDED_KONG, owner, rules.addedKongPayment());
            clearReaction(); turn = owner; replacement();
            return;
        }
        for (int step = 1; step < 4; step++) {
            int seat = (supplier + step) % 4;
            var action = responses[seat];
            if (action.type() != PUNG && action.type() != DISCARD_KONG) continue;
            var player = players[seat];
            player.hand.removeAll(action.tiles());
            var tiles = new ArrayList<>(action.tiles()); tiles.add(focus);
            player.melds.add(new Meld(action.type() == PUNG ? Meld.Type.TRIPLET : Meld.Type.OPEN_QUAD, tiles, supplier, focus));
            claimDiscard();
            if (action.type() == DISCARD_KONG) {
                kongLedgerStart = ledger.size();
                transfer(SichuanSettlement.Type.DISCARD_KONG, supplier, seat, rules.discardKongPayment(), -1);
            }
            clearReaction(); turn = seat; player.drawn = Tile.ABSENT; phase = Phase.TURN;
            afterKong = false;
            if (action.type() == DISCARD_KONG) replacement();
            return;
        }
        int previous = supplier;
        clearReaction(); advance(previous);
    }

    private void claimDiscard() {
        var river = players[supplier].river;
        river.set(river.size() - 1, new SichuanPlayerState.Discard(focus, true));
    }
    private void clearReaction() { focus = Tile.ABSENT; supplier = -1; pendingKong = -1; Arrays.fill(responses, null); }
    private void replacement() {
        players[turn].drawn = Tile.ABSENT; phase = Phase.TURN; afterKong = false; replacementDraw = true;
    }
    private void advance(int previous) {
        afterKong = false; replacementDraw = false; kongLedgerStart = -1;
        for (int step = 1; step <= 4; step++) if (!players[(previous + step) % 4].won) {
            turn = (previous + step) % 4; break;
        }
        phase = Phase.TURN;
        if (wall.remaining() == 0) finish(true);
    }
    private static int meldIndex(Player player, int tile) {
        for (int index = 0; index < player.melds.size(); index++)
            if (player.melds.get(index).type() == Meld.Type.TRIPLET && player.melds.get(index).kind() == Tile.kind(tile)) return index;
        throw new IllegalStateException("No pung to supplement");
    }
    private void payActive(SichuanSettlement.Type type, int recipient, int amount) {
        for (int seat = 0; seat < 4; seat++) if (seat != recipient && !players[seat].won) transfer(type, seat, recipient, amount, -1);
    }
    private void transfer(SichuanSettlement.Type type, int payer, int recipient, int amount, int related) {
        if (amount > 0) ledger.add(new SichuanSettlement.Entry(ledger.size(), type, payer, recipient, amount, related));
    }
    private void refundKongs(int seat, int start) {
        if (start < 0) return;
        var refunded = new HashSet<Integer>();
        for (var entry : ledger) if (entry.type() == SichuanSettlement.Type.KONG_REFUND) refunded.add(entry.relatedEntry());
        for (var entry : List.copyOf(ledger)) if (entry.id() >= start && entry.kong() && entry.recipient() == seat && !refunded.contains(entry.id()))
            transfer(SichuanSettlement.Type.KONG_REFUND, seat, entry.payer(), entry.amount(), entry.id());
    }
    private void finish(boolean exhaustive) {
        var statuses = new ArrayList<SichuanSettlement.DrawStatus>();
        if (exhaustive) {
            int[] ready = new int[4];
            for (int seat = 0; seat < 4; seat++) {
                var player = players[seat];
                boolean missing = player.hand.stream().anyMatch(tile -> Tile.kind(tile) / 9 == player.voidSuit)
                    || player.melds.stream().anyMatch(meld -> meld.kind() / 9 == player.voidSuit);
                ready[seat] = player.won ? 0 : SichuanHandAnalyzer.readyValue(player.hand, player.melds, player.voidSuit, rules);
                var status = player.won ? SichuanSettlement.DrawStatus.WON : missing
                    ? player.river.stream().anyMatch(discard -> Tile.kind(discard.tile()) / 9 != player.voidSuit)
                        ? SichuanSettlement.DrawStatus.ACTIVE_FLOWER_PIG : SichuanSettlement.DrawStatus.PASSIVE_FLOWER_PIG
                    : ready[seat] > 0 ? SichuanSettlement.DrawStatus.READY : SichuanSettlement.DrawStatus.NOT_READY;
                statuses.add(status);
                if (!player.won && ready[seat] == 0 && rules.refundKongWhenNotReady()) refundKongs(seat, 0);
            }
            for (int payer = 0; payer < 4; payer++) if (!players[payer].won) {
                if (statuses.get(payer) == SichuanSettlement.DrawStatus.ACTIVE_FLOWER_PIG)
                    transfer(SichuanSettlement.Type.FLOWER_PIG, payer, -1, rules.activeFlowerPigPenalty(), -1);
                for (int recipient = 0; recipient < 4; recipient++) if (recipient != payer && !players[recipient].won) {
                    if (ready[payer] == 0 && ready[recipient] > 0)
                        transfer(SichuanSettlement.Type.READY_PAYMENT, payer, recipient, ready[recipient], -1);
                }
            }
        }
        phase = Phase.HAND_END; afterKong = false; replacementDraw = false; kongLedgerStart = -1;
        result = new SichuanSettlement.Result(wins, ledger, statuses, exhaustive);
    }
    private void changed() { revision = Math.addExact(revision, 1); decision = Math.addExact(decision, 1); }

    public State save() {
        validate();
        var submitted = new ArrayList<Response>();
        for (int seat = 0; seat < 4; seat++) if (responses[seat] != null) submitted.add(new Response(seat, responses[seat]));
        return new State(State.FORMAT, rules, phase, revision, decision, turn, wall.save(),
            Arrays.stream(players).map(Player::save).toList(), focus, supplier, pendingKong,
            replacementDraw, afterKong, kongLedgerStart, submitted, wins, ledger, result);
    }

    public record Response(int seat, SichuanAction action) {}
    public record State(int format, SichuanRules rules, Phase phase, long revision, long decision, int turn,
                        SichuanWall.State wall, List<SichuanPlayerState> players, int focus, int supplier, int pendingKong,
                        boolean replacementDraw, boolean afterKong, int kongLedgerStart, List<Response> responses,
                        List<SichuanSettlement.Win> wins, List<SichuanSettlement.Entry> ledger, SichuanSettlement.Result result) {
        public static final int FORMAT = 1;
        public State {
            Objects.requireNonNull(rules); Objects.requireNonNull(phase); Objects.requireNonNull(wall);
            players = List.copyOf(players); responses = List.copyOf(responses); wins = List.copyOf(wins); ledger = List.copyOf(ledger);
            if (format != FORMAT || players.size() != 4 || revision < 1 || decision < 1
                || revision >= Long.MAX_VALUE - 1 || decision >= Long.MAX_VALUE - 1 || turn < 0 || turn > 3)
                throw new IllegalArgumentException("Invalid Sichuan game state");
        }
    }

    private static final class Player {
        final ArrayList<Integer> hand = new ArrayList<>();
        final ArrayList<Meld> melds = new ArrayList<>();
        final ArrayList<SichuanPlayerState.Discard> river = new ArrayList<>();
        int voidSuit = -1;
        boolean won;
        int drawn = Tile.ABSENT;
        int passedFan = -1;
        Player() {}
        Player(SichuanPlayerState saved) {
            hand.addAll(saved.hand()); melds.addAll(saved.melds()); river.addAll(saved.river());
            voidSuit = saved.voidSuit(); won = saved.won(); drawn = saved.drawn(); passedFan = saved.passedFan();
        }
        SichuanPlayerState save() { return new SichuanPlayerState(hand, melds, river, voidSuit, won, drawn, passedFan); }
    }

    public void validate() {
        var owned = new HashSet<Integer>();
        for (int tile : wall.save().slots()) if (tile != Tile.ABSENT) own(owned, tile);
        var winningSeats = new HashSet<Integer>();
        for (var win : wins) if (!winningSeats.add(win.seat())) throw new IllegalArgumentException("Duplicate Sichuan winner");
        for (int seat = 0; seat < 4; seat++) {
            var player = players[seat];
            for (int tile : player.hand) own(owned, tile);
            for (var meld : player.melds) {
                int count = meld.type() == Meld.Type.TRIPLET ? 3 : 4;
                if (meld.type() == Meld.Type.SEQUENCE || meld.tiles().size() != count || meld.fromSeat() < 0 || meld.fromSeat() > 3
                    || meld.closed() && (meld.fromSeat() != seat || meld.calledTile() != Tile.ABSENT)
                    || !meld.closed() && (meld.fromSeat() == seat || !meld.tiles().contains(meld.calledTile()))
                    || meld.tiles().stream().anyMatch(tile -> Tile.kind(tile) != meld.kind()))
                    throw new IllegalArgumentException("Invalid Sichuan meld");
                for (int tile : meld.tiles()) own(owned, tile);
            }
            for (var discard : player.river) if (!discard.claimed()) own(owned, discard.tile());
            int size = player.hand.size() + player.melds.size() * 3;
            boolean extra = seat == turn && (phase == Phase.VOIDING || phase == Phase.TURN || phase == Phase.REACTION && pendingKong >= 0);
            if (player.won != winningSeats.contains(seat) || !player.won && size != (extra && !replacementDraw ? 14 : 13)
                && !(phase == Phase.TURN && seat == turn && size == 13)
                || player.won && size != 13 && size != 14 || player.melds.size() > 4
                || phase != Phase.VOIDING && player.voidSuit < 0
                || player.drawn != Tile.ABSENT && !player.hand.contains(player.drawn))
                throw new IllegalArgumentException("Invalid Sichuan player position");
        }
        if (owned.size() != 108) throw new IllegalArgumentException("Sichuan physical tiles are not conserved");
        for (var player : players) for (var discard : player.river)
            if (discard.claimed() && !owned.contains(discard.tile())) throw new IllegalArgumentException("Invalid discard alias");
        if (wins.size() > 3 || (phase == Phase.HAND_END) != (result != null)
            || phase != Phase.HAND_END && (players[turn].won || wins.size() == 3)
            || phase == Phase.VOIDING && (wins.size() > 0 || Arrays.stream(players).allMatch(player -> player.voidSuit >= 0))
            || kongLedgerStart < -1 || kongLedgerStart > ledger.size() || pendingKong < -1
            || phase == Phase.REACTION && (supplier != turn || focus < 0 || focus >= 108 || responsesComplete())
            || phase != Phase.REACTION && (focus != Tile.ABSENT || supplier != -1 || pendingKong != -1
                || Arrays.stream(responses).anyMatch(Objects::nonNull)))
            throw new IllegalArgumentException("Invalid Sichuan phase");
        if (phase == Phase.REACTION) {
            if (pendingKong >= 0) {
                if (pendingKong >= players[supplier].melds.size() || !players[supplier].hand.contains(focus)
                    || meldIndex(players[supplier], focus) != pendingKong || wall.remaining() == 0)
                    throw new IllegalArgumentException("Invalid pending Sichuan kong");
            } else {
                var river = players[supplier].river;
                if (river.isEmpty() || !river.get(river.size() - 1).equals(new SichuanPlayerState.Discard(focus, false)))
                    throw new IllegalArgumentException("Invalid Sichuan discard focus");
            }
            for (int seat = 0; seat < 4; seat++) {
                var options = legalActions(seat);
                var response = responses[seat];
                boolean pass = response != null && response.type() == PASS;
                if (options.isEmpty() ? !pass : response != null && !options.contains(response) && !(response.type() == WIN && winScore(seat, focus, true) != null))
                    throw new IllegalArgumentException("Invalid saved Sichuan response");
            }
        }
        var refunded = new HashSet<Integer>();
        for (int index = 0; index < ledger.size(); index++) {
            var entry = ledger.get(index);
            if (entry.id() != index) throw new IllegalArgumentException("Invalid ledger order");
            int amount = switch (entry.type()) {
                case CONCEALED_KONG -> rules.concealedKongPayment();
                case DISCARD_KONG -> rules.discardKongPayment();
                case ADDED_KONG -> rules.addedKongPayment();
                case FLOWER_PIG -> rules.activeFlowerPigPenalty();
                default -> entry.amount();
            };
            if (entry.amount() != amount || (entry.type() == SichuanSettlement.Type.FLOWER_PIG
                || entry.type() == SichuanSettlement.Type.READY_PAYMENT) && (result == null || !result.exhaustive()))
                throw new IllegalArgumentException("Invalid Sichuan payment");
            if (entry.type() == SichuanSettlement.Type.KONG_REFUND) {
                var original = ledger.get(entry.relatedEntry());
                if (!original.kong() || !refunded.add(original.id()) || entry.payer() != original.recipient()
                    || entry.recipient() != original.payer() || entry.amount() != original.amount())
                    throw new IllegalArgumentException("Invalid kong refund");
            }
        }
        for (var win : wins) {
            var player = players[win.seat()];
            var hand = new ArrayList<>(player.hand);
            if (!hand.contains(win.tile())) hand.add(win.tile());
            if (!owned.contains(win.tile()) || player.voidSuit < 0 || hand.stream().anyMatch(tile -> Tile.kind(tile) / 9 == player.voidSuit)
                || !win.score().equals(SichuanHandAnalyzer.score(hand, player.melds, rules,
                    win.score().patterns().contains(SichuanSettlement.Fan.WIN_AFTER_KONG),
                    win.score().patterns().contains(SichuanSettlement.Fan.SHOOT_AFTER_KONG), win.robbingKong(),
                    win.score().patterns().contains(SichuanSettlement.Fan.UNDER_THE_SEA))))
                throw new IllegalArgumentException("Invalid saved Sichuan winner");
        }
        var payments = ledger.stream().filter(entry -> entry.type() == SichuanSettlement.Type.DISCARD_WIN
            || entry.type() == SichuanSettlement.Type.SELF_DRAW_WIN).toList();
        int payment = 0;
        var retired = new HashSet<Integer>();
        for (var win : wins) {
            if (retired.contains(win.supplier())) throw new IllegalArgumentException("Retired Sichuan supplier");
            for (int payer = 0; payer < 4; payer++) if (payer != win.seat() && !retired.contains(payer)
                && (win.selfDraw() || payer == win.supplier())) {
                if (payment >= payments.size()) throw new IllegalArgumentException("Missing Sichuan win payment");
                var entry = payments.get(payment++);
                if (entry.payer() != payer || entry.recipient() != win.seat()
                    || entry.type() != (win.selfDraw() ? SichuanSettlement.Type.SELF_DRAW_WIN : SichuanSettlement.Type.DISCARD_WIN)
                    || entry.amount() != win.score().value() + (win.selfDraw() ? rules.selfDrawBonus() : 0))
                    throw new IllegalArgumentException("Invalid Sichuan win payment");
            }
            retired.add(win.seat());
        }
        if (payment != payments.size()) throw new IllegalArgumentException("Unmatched Sichuan win payment");
        if (result != null && (!result.wins().equals(wins) || !result.ledger().equals(ledger)
            || result.exhaustive() != (wins.size() < 3) || result.exhaustive() && wall.remaining() != 0))
            throw new IllegalArgumentException("Invalid Sichuan result state");
    }
    private static void own(HashSet<Integer> owned, int tile) {
        if (tile < 0 || tile >= 108 || !owned.add(tile)) throw new IllegalArgumentException("Invalid Sichuan physical identity");
    }
}
