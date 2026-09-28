package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** A single-threaded, server-owned MCR match. Seats are fixed indices in turn order. */
public final class McrGame {
    public enum Phase { DRAW, TURN, REACTION, HAND_END, MATCH_END }

    private static final long WALL_SEED_STEP = 0x9e3779b97f4a7c15L;
    private final long seed;
    // Only the physical zones, drawn-tile alias and points are shared with Riichi.
    private final PlayerState[] players = {new PlayerState(), new PlayerState(), new PlayerState(), new PlayerState()};
    private final boolean[] winForbidden = new boolean[4];
    private final List<McrSettlement.Penalty> penalties = new ArrayList<>();
    private final McrAction[] replies = new McrAction[4];
    private List<List<McrAction>> choices;
    private McrWall wall;
    private Phase phase;
    private long decision;
    private long revision = 1;
    private int handIndex;
    private int turn;
    private int claimTile = Tile.ABSENT;
    private int claimFrom = -1;
    private McrAction pendingKong;
    private boolean drawWallLast;
    private McrWinContext.KongWin drawKong = McrWinContext.KongWin.NONE;
    private McrSettlement.Result result;

    public McrGame(long seed) { this(seed, new McrWall(seed)); }

    /** Physical placement and opening are private engine inputs, never client-claimed draw order. */
    public McrGame(long seed, List<Integer> physicalWall, McrOpening opening) {
        this(seed, new McrWall(physicalWall, opening));
    }

    /** Room equipment supplies an unordered, complete stock; the engine owns the shuffle. */
    public static McrGame fromStock(long seed, List<Integer> stock) { return new McrGame(seed, new McrWall(seed, 0, stock)); }

    private McrGame(long seed, McrWall wall) {
        this.seed = seed;
        startHand(wall);
    }

    private McrGame(McrGameState state) {
        seed = state.seed();
        revision = Math.addExact(state.revision(), 1);
        decision = Math.addExact(state.decision(), 1);
        handIndex = state.handNumber() - 1;
        turn = state.turn();
        phase = state.phase();
        wall = McrWall.restore(state.wall());
        claimTile = state.claimTile();
        claimFrom = state.claimFrom();
        pendingKong = state.pendingKong();
        drawWallLast = state.drawWallLast();
        drawKong = state.drawKong();
        result = state.result();
        penalties.addAll(state.penalties());
        for (int seat = 0; seat < 4; seat++) {
            var saved = state.players().get(seat);
            var player = players[seat];
            player.hand.addAll(saved.hand());
            player.melds.addAll(saved.melds());
            player.river.addAll(saved.river());
            player.flowers.addAll(saved.flowers());
            player.drawn = saved.drawn();
            player.points = saved.points();
            winForbidden[seat] = saved.winForbidden();
        }
        validate();
        validateSavedEvents();
        rebuildChoices();
        for (var reply : state.replies()) {
            if (phase != Phase.REACTION || replies[reply.seat()] != null
                || !choices.get(reply.seat()).contains(reply.action()))
                throw new IllegalArgumentException("Invalid saved MCR response");
            replies[reply.seat()] = reply.action();
        }
        if (phase == Phase.REACTION && responsesComplete())
            throw new IllegalArgumentException("Saved reaction window has already completed");
    }

    /** Detached private state. Legal-action caches are rebuilt rather than trusted on restore. */
    public McrGameState save() {
        validate();
        validateSavedEvents();
        var seats = new ArrayList<McrGameState.Player>(4);
        var submitted = new ArrayList<McrGameState.Reply>(3);
        for (int seat = 0; seat < 4; seat++) {
            var player = players[seat];
            seats.add(new McrGameState.Player(player.hand, player.melds, player.river, player.flowers,
                player.drawn, player.points, winForbidden[seat]));
            if (replies[seat] != null) submitted.add(new McrGameState.Reply(seat, replies[seat]));
        }
        return new McrGameState(McrGameState.FORMAT, seed, revision, decision, handNumber(), turn, phase,
            wall.save(), seats, claimTile, claimFrom, pendingKong, drawWallLast, drawKong, submitted, penalties, result);
    }

    /** Does not deal, settle, apply penalties or resolve pending responses a second time. */
    public static McrGame restore(McrGameState state) { return new McrGame(Objects.requireNonNull(state)); }

    public Phase phase() { return phase; }
    public long decision() { return decision; }
    public long revision() { return revision; }
    public int handNumber() { return handIndex + 1; }
    public int dealer() { return handIndex % 4; }
    public int turn() { return turn; }
    public int roundWind() { return Tile.EAST + handIndex / 4; }
    public int seatWind(int seat) { checkSeat(seat); return Tile.EAST + Math.floorMod(seat - dealer(), 4); }
    public int remaining() { return wall.remaining(); }
    public McrOpening opening() { return wall.opening(); }
    public int points(int seat) { return player(seat).points; }
    public int drawn(int seat) { return player(seat).drawn; }
    public boolean winForbidden(int seat) { checkSeat(seat); return winForbidden[seat]; }
    public List<Integer> hand(int seat) { return List.copyOf(player(seat).hand); }
    public List<Meld> melds(int seat) { return List.copyOf(player(seat).melds); }
    public List<Discard> river(int seat) { return List.copyOf(player(seat).river); }
    public List<Integer> flowers(int seat) { return List.copyOf(player(seat).flowers); }
    public McrSettlement.Result result() { return result; }
    public List<McrSettlement.Penalty> penalties() { return List.copyOf(penalties); }

    /** The host resolves the authorized seat. Use -1 for spectators; never pass a client-claimed seat. */
    public McrView view(int viewerSeat) { return McrView.project(this, viewerSeat); }

    List<Integer> publicWall() { return wall.tiles().stream().map(tile -> tile == Tile.ABSENT ? Tile.ABSENT : Tile.HIDDEN).toList(); }
    boolean responded(int seat) { checkSeat(seat); return replies[seat] != null; }

    /** Engine inspection is private, not a recipient-filtered network snapshot. */
    public List<McrAction> actions(int seat) {
        checkSeat(seat);
        return replies[seat] == null ? choices.get(seat) : List.of();
    }

    /** Accept only a current engine-issued action, once per seat in a response window. */
    public boolean act(int seat, long expectedDecision, int actionIndex) {
        if (seat < 0 || seat >= 4 || expectedDecision != decision || replies[seat] != null
            || actionIndex < 0 || actionIndex >= choices.get(seat).size()) return false;
        McrAction action = choices.get(seat).get(actionIndex);
        if (phase == Phase.REACTION) {
            replies[seat] = action;
            if (responsesComplete()) resolveReactions();
        } else {
            switch (action.type()) {
                case DRAW -> draw(false);
                case DISCARD -> discard(action.tiles().get(0));
                case WIN -> declareSelfDraw();
                case CONCEALED_KONG -> concealedKong(action);
                case MELDED_KONG -> {
                    pendingKong = action;
                    claimTile = action.tiles().get(0);
                    claimFrom = seat;
                    reactions();
                }
                default -> throw new IllegalStateException("Unexpected MCR turn action: " + action.type());
            }
        }
        revision = Math.addExact(revision, 1);
        return true;
    }

    /** Hand advancement is an engine host operation, separate from player tile actions. */
    public boolean nextHand() {
        if (phase != Phase.HAND_END) return false;
        handIndex++;
        startHand(new McrWall(seed + WALL_SEED_STEP * handIndex, dealer(), Tile.mcrSet()));
        revision = Math.addExact(revision, 1);
        return true;
    }

    private void startHand(McrWall nextWall) {
        wall = Objects.requireNonNull(nextWall);
        for (var player : players) player.resetHand();
        Arrays.fill(winForbidden, false);
        result = null;
        pendingKong = null;
        claimTile = Tile.ABSENT;
        claimFrom = -1;
        drawKong = McrWinContext.KongWin.NONE;
        drawWallLast = false;
        turn = dealer();
        if (wall.opening().dealer() != dealer()) throw new IllegalArgumentException("Opening belongs to another dealer");
        // Allocate all raw physical packets, including the first/third-stack jump, before replacing any flower.
        for (var take : McrWallLayout.initialDeal(wall.opening())) {
            int tile = wall.takeRaw(take.slot());
            players[take.seat()].hand.add(tile);
            if (take.seat() == dealer()) players[dealer()].drawn = tile;
        }
        // Finish each seat's complete replacement chain before moving East -> South -> West -> North.
        for (int wind = 0; wind < 4; wind++) {
            int seat = (dealer() + wind) % 4;
            var player = players[seat];
            for (int tile : List.copyOf(player.hand)) if (Tile.isFlower(tile)) {
                player.hand.remove(Integer.valueOf(tile));
                player.flowers.add(tile);
                int replacement = wall.replace(player);
                if (replacement == Tile.ABSENT) { finish(new McrSettlement.Draw()); return; }
                player.hand.add(replacement);
                if (seat == dealer()) player.drawn = replacement;
            }
            player.hand.sort(Tile.ORDER);
        }
        changePhase(Phase.TURN);
        validate();
    }

    private void draw(boolean afterKong) {
        var player = players[turn];
        player.drawn = Tile.ABSENT;
        int flowersBefore = player.flowers.size();
        int tile = afterKong ? wall.replace(player) : wall.draw(player);
        if (tile == Tile.ABSENT) { finish(new McrSettlement.Draw()); return; }
        player.hand.add(tile);
        player.hand.sort(Tile.ORDER);
        player.drawn = tile;
        drawWallLast = wall.remaining() == 0;
        // A flower starts its own supplementary chain, even when the first tail draw followed a kong.
        drawKong = afterKong && player.flowers.size() == flowersBefore
            ? McrWinContext.KongWin.REPLACEMENT : McrWinContext.KongWin.NONE;
        changePhase(Phase.TURN);
    }

    private void discard(int tile) {
        var player = players[turn];
        player.hand.remove(Integer.valueOf(tile));
        player.river.add(new Discard(tile, false, false, tile == player.drawn));
        player.drawn = Tile.ABSENT;
        claimTile = tile;
        claimFrom = turn;
        pendingKong = null;
        reactions();
    }

    private void reactions() {
        changePhase(Phase.REACTION);
        if (responsesComplete()) resolveReactions();
    }

    private boolean responsesComplete() {
        for (int seat = 0; seat < 4; seat++) if (!choices.get(seat).isEmpty() && replies[seat] == null) return false;
        return true;
    }

    private void resolveReactions() {
        McrSettlement.Win win = null;
        // Arrival order cannot select the winner. Every actual invalid declaration is penalized;
        // among qualifying declarations only the nearest seat after the supplier wins.
        for (int distance = 1; distance < 4; distance++) {
            int seat = (claimFrom + distance) % 4;
            if (replies[seat] == null || replies[seat].type() != McrAction.Type.WIN) continue;
            var score = Objects.requireNonNull(score(seat));
            if (!score.meetsMinimum()) wrongWin(seat, claimTile);
            else if (win == null) win = McrSettlement.win(seat, claimFrom, claimTile, winningContext(seat), score);
        }
        if (win != null) {
            var supplier = players[claimFrom];
            if (pendingKong != null) {
                // The added tile stays with its owner until the robbing window closes.
                supplier.hand.remove(Integer.valueOf(claimTile));
            } else {
                int index = supplier.river.size() - 1;
                supplier.river.set(index, supplier.river.get(index).markCalled());
            }
            // The winner owns the one physical tile; result/history entries only reference it.
            // Robbing a kong never invents a discard in the supplier's river.
            players[win.winner()].hand.add(claimTile);
            players[win.winner()].hand.sort(Tile.ORDER);
            supplier.drawn = Tile.ABSENT;
            pendingKong = null;
            finish(win);
            return;
        }
        if (pendingKong != null) { completeAddedKong(); return; }
        int selected = -1, priority = 0;
        for (int distance = 1; distance < 4; distance++) {
            int seat = (claimFrom + distance) % 4;
            int candidate = replies[seat] == null ? 0 : switch (replies[seat].type()) {
                case PUNG, MELDED_KONG -> 2;
                case CHOW -> 1;
                default -> 0;
            };
            if (candidate > priority) { selected = seat; priority = candidate; }
        }
        if (selected >= 0) { claim(selected, replies[selected]); return; }
        turn = (claimFrom + 1) % 4;
        if (wall.remaining() == 0) finish(new McrSettlement.Draw());
        else changePhase(Phase.DRAW);
    }

    private void claim(int seat, McrAction action) {
        var player = players[seat];
        var tiles = new ArrayList<>(action.tiles());
        player.hand.removeAll(tiles);
        tiles.add(claimTile);
        tiles.sort(Tile.ORDER);
        Meld.Type type = switch (action.type()) {
            case CHOW -> Meld.Type.SEQUENCE;
            case PUNG -> Meld.Type.TRIPLET;
            case MELDED_KONG -> Meld.Type.OPEN_QUAD;
            default -> throw new IllegalStateException("Not a meld claim");
        };
        player.melds.add(new Meld(type, tiles, claimFrom, claimTile));
        var river = players[claimFrom].river;
        river.set(river.size() - 1, river.get(river.size() - 1).markCalled());
        player.drawn = Tile.ABSENT;
        turn = seat;
        drawWallLast = false;
        drawKong = McrWinContext.KongWin.NONE;
        if (type == Meld.Type.OPEN_QUAD) draw(true);
        else changePhase(Phase.TURN);
    }

    private void concealedKong(McrAction action) {
        var player = players[turn];
        player.hand.removeAll(action.tiles());
        player.melds.add(new Meld(Meld.Type.CONCEALED_QUAD, action.tiles(), turn, Tile.ABSENT));
        draw(true);
    }

    private void completeAddedKong() {
        var player = players[turn];
        int kind = Tile.kind(claimTile);
        for (int i = 0; i < player.melds.size(); i++) {
            var pung = player.melds.get(i);
            if (pung.type() != Meld.Type.TRIPLET || pung.kind() != kind) continue;
            var tiles = new ArrayList<>(pung.tiles());
            tiles.add(claimTile);
            tiles.sort(Tile.ORDER);
            player.hand.remove(Integer.valueOf(claimTile));
            player.melds.set(i, new Meld(Meld.Type.ADDED_QUAD, tiles, pung.fromSeat(), pung.calledTile()));
            pendingKong = null;
            draw(true);
            return;
        }
        throw new IllegalStateException("Added kong lost its original pung");
    }

    private void declareSelfDraw() {
        var score = Objects.requireNonNull(score(turn));
        int tile = players[turn].drawn;
        if (score.meetsMinimum()) finish(McrSettlement.win(turn, -1, tile, winningContext(turn), score));
        else {
            wrongWin(turn, tile);
            changePhase(Phase.TURN);
        }
    }

    private void wrongWin(int seat, int tile) {
        var penalty = McrSettlement.wrongWin(handNumber(), seat, tile);
        apply(penalty.deltas());
        penalties.add(penalty);
        winForbidden[seat] = true;
    }

    private void finish(McrSettlement.Result outcome) {
        result = outcome;
        apply(outcome.deltas());
        changePhase(handIndex == 15 ? Phase.MATCH_END : Phase.HAND_END);
    }

    private void apply(List<Integer> deltas) {
        for (int seat = 0; seat < 4; seat++) players[seat].points = Math.addExact(players[seat].points, deltas.get(seat));
    }

    private void changePhase(Phase next) {
        phase = next;
        decision++;
        Arrays.fill(replies, null);
        rebuildChoices();
    }

    private void rebuildChoices() {
        var actions = new ArrayList<List<McrAction>>(4);
        for (int seat = 0; seat < 4; seat++) actions.add(McrLegalActions.forSeat(this, seat));
        choices = List.copyOf(actions);
    }

    /** Facts are derived here, never supplied with a player's declaration. */
    public McrWinContext winningContext(int seat) {
        checkSeat(seat);
        boolean self = phase == Phase.TURN && seat == turn && players[seat].drawn >= 0;
        boolean discard = phase == Phase.REACTION && seat != claimFrom;
        if (!self && !discard) throw new IllegalStateException("No winning-tile source for this seat");
        int tile = self ? players[seat].drawn : claimTile;
        var kong = self ? drawKong : pendingKong != null ? McrWinContext.KongWin.ROBBED : McrWinContext.KongWin.NONE;
        return new McrWinContext(self ? McrWinContext.Method.SELF_DRAW : McrWinContext.Method.DISCARD,
            seatWind(seat), roundWind(), drawWallLast && (self || pendingKong == null), kong,
            lastCopy(tile), players[seat].flowers.size());
    }

    private boolean lastCopy(int winningTile) {
        var visible = new HashSet<Integer>();
        for (var player : players) {
            // Called discards alias exposed melds; concealed tiles cannot establish this fan.
            player.river.forEach(discard -> visible.add(discard.tile()));
            player.melds.stream().filter(meld -> !meld.closed()).forEach(meld -> visible.addAll(meld.tiles()));
        }
        visible.remove(winningTile);
        int kind = Tile.kind(winningTile);
        return visible.stream().filter(tile -> Tile.kind(tile) == kind).count() == 3;
    }

    McrHandScore score(int seat) {
        if (winForbidden[seat]) return null;
        var player = players[seat];
        if (phase == Phase.TURN && seat == turn && player.drawn >= 0) {
            var concealed = new ArrayList<>(player.hand);
            concealed.remove(Integer.valueOf(player.drawn));
            return McrHandAnalyzer.score(concealed, player.melds, seat, player.drawn, winningContext(seat));
        }
        if (phase == Phase.REACTION && seat != claimFrom)
            return McrHandAnalyzer.score(player.hand, player.melds, seat, claimTile, winningContext(seat));
        return null;
    }

    PlayerState player(int seat) { checkSeat(seat); return players[seat]; }
    int claimFrom() { return claimFrom; }
    int claimTile() { return claimTile; }
    boolean robbingKong() { return pendingKong != null; }

    /** Private integrity check, including all 144 physical identities and zero-sum points. */
    public void validate() {
        Objects.requireNonNull(phase);
        Objects.requireNonNull(drawKong);
        checkSeat(turn);
        if (handIndex < 0 || handIndex >= 16 || revision < 1 || revision == Long.MAX_VALUE
            || decision < 1 || decision == Long.MAX_VALUE || wall.save().front() < 53 || wall.opening().dealer() != dealer())
            throw new IllegalStateException("Invalid MCR position");
        boolean ended = phase == Phase.HAND_END || phase == Phase.MATCH_END;
        if (ended != (result != null) || ended && (phase == Phase.MATCH_END) != (handIndex == 15))
            throw new IllegalStateException("MCR phase and result disagree");
        if (claimTile == Tile.ABSENT ? claimFrom != -1 : claimFrom < 0 || claimFrom > 3 || claimTile < 0 || claimTile >= 136)
            throw new IllegalStateException("Invalid MCR claim source");
        if (phase == Phase.REACTION && (claimFrom != turn || claimTile == Tile.ABSENT))
            throw new IllegalStateException("Reaction has no current claim");
        if (phase == Phase.DRAW && (wall.remaining() == 0 || claimFrom < 0 || turn != (claimFrom + 1) % 4))
            throw new IllegalStateException("Invalid next draw position");
        if (drawKong == McrWinContext.KongWin.ROBBED || drawWallLast && wall.remaining() != 0)
            throw new IllegalStateException("Invalid MCR draw origin");
        if (pendingKong != null && (phase != Phase.REACTION || pendingKong.type() != McrAction.Type.MELDED_KONG
            || !pendingKong.tiles().equals(List.of(claimTile)) || wall.remaining() == 0
            || players[claimFrom].drawn < 0 || !players[claimFrom].hand.contains(claimTile)
            || players[claimFrom].melds.stream().noneMatch(meld -> meld.type() == Meld.Type.TRIPLET && meld.kind() == Tile.kind(claimTile))))
            throw new IllegalStateException("Invalid pending MCR kong");
        if (phase == Phase.REACTION && pendingKong == null) {
            var river = players[claimFrom].river;
            if (river.isEmpty() || river.get(river.size() - 1).tile() != claimTile || river.get(river.size() - 1).called())
                throw new IllegalStateException("Reaction does not refer to the last unclaimed discard");
        }
        if ((phase == Phase.TURN || phase == Phase.REACTION) && drawWallLast != (wall.remaining() == 0))
            throw new IllegalStateException("Last-wall context disagrees with the wall");
        if (phase == Phase.TURN && drawKong == McrWinContext.KongWin.REPLACEMENT
            && (players[turn].drawn < 0 || players[turn].melds.isEmpty()
                || !players[turn].melds.get(players[turn].melds.size() - 1).quad()
                    && players[turn].melds.stream().noneMatch(meld -> meld.type() == Meld.Type.ADDED_QUAD)))
            throw new IllegalStateException("Kong replacement has no completed kong");
        if (result instanceof McrSettlement.Draw && wall.remaining() != 0)
            throw new IllegalStateException("Exhaustive draw still has wall tiles");
        wall.assertConservation(players);
        if (Arrays.stream(players).mapToLong(player -> player.points).sum() != 0)
            throw new IllegalStateException("MCR point conservation failed");
        for (int seat = 0; seat < 4; seat++) {
            var player = players[seat];
            boolean extra = phase == Phase.TURN && seat == turn
                || phase == Phase.REACTION && pendingKong != null && seat == claimFrom
                || result instanceof McrSettlement.Win win && win.winner() == seat;
            if (player.hand.size() + 3 * player.melds.size() != (extra ? 14 : 13))
                throw new IllegalStateException("Invalid MCR concealed hand size at seat " + seat);
            boolean canHaveDraw = phase == Phase.TURN && seat == turn
                || phase == Phase.REACTION && pendingKong != null && seat == claimFrom
                || result instanceof McrSettlement.Win win && win.fromSeat() == -1 && win.winner() == seat;
            if (player.drawn != Tile.ABSENT && (!canHaveDraw || !player.hand.contains(player.drawn)))
                throw new IllegalStateException("Invalid drawn-tile ownership");
            if (!player.norths.isEmpty() || player.river.stream().anyMatch(Discard::riichi))
                throw new IllegalStateException("Riichi state in an MCR hand");
            var concealed = new ArrayList<>(player.hand);
            if (extra) concealed.remove(concealed.size() - 1);
            McrHandAnalyzer.validateHand(concealed, player.melds, seat);
        }
    }

    private void validateSavedEvents() {
        if (penalties.size() > 64) throw new IllegalArgumentException("Too many MCR penalties");
        var penaltyKeys = new HashSet<Integer>();
        int previousHand = 0;
        for (var penalty : penalties) {
            if (penalty.handNumber() < previousHand || penalty.handNumber() > handNumber()
                || penalty.tile() < 0 || penalty.tile() >= 136
                || !penaltyKeys.add(penalty.handNumber() * 4 + penalty.offender())
                || !penalty.equals(McrSettlement.wrongWin(penalty.handNumber(), penalty.offender(), penalty.tile())))
                throw new IllegalArgumentException("Invalid saved MCR penalty");
            previousHand = penalty.handNumber();
        }
        for (int seat = 0; seat < 4; seat++) if (winForbidden[seat] != penaltyKeys.contains(handNumber() * 4 + seat))
            throw new IllegalArgumentException("Stop-win state disagrees with current-hand penalties");

        var riverTiles = new HashSet<Integer>();
        for (int seat = 0; seat < 4; seat++) {
            for (var discard : players[seat].river) {
                if (!riverTiles.add(discard.tile()) || discard.tile() < 0 || discard.tile() >= 136)
                    throw new IllegalArgumentException("Invalid saved MCR discard history");
                if (discard.called()) {
                    int source = seat;
                    boolean meldClaim = Arrays.stream(players).flatMap(player -> player.melds.stream())
                        .anyMatch(meld -> !meld.closed() && meld.fromSeat() == source && meld.calledTile() == discard.tile());
                    boolean winningClaim = result instanceof McrSettlement.Win win && win.fromSeat() == seat
                        && win.tile() == discard.tile() && win.context().kongWin() != McrWinContext.KongWin.ROBBED;
                    if (!meldClaim && !winningClaim) throw new IllegalArgumentException("Called discard has no destination");
                }
            }
            for (var meld : players[seat].melds) if (!meld.closed()
                && players[meld.fromSeat()].river.stream().noneMatch(discard -> discard.called() && discard.tile() == meld.calledTile()))
                throw new IllegalArgumentException("Exposed meld has no source discard");
        }
        if (result instanceof McrSettlement.Win win) {
            checkSeat(win.winner());
            boolean self = win.context().method() == McrWinContext.Method.SELF_DRAW;
            if (!self) checkSeat(win.fromSeat());
            var winner = players[win.winner()];
            if (winForbidden[win.winner()] || !winner.hand.contains(win.tile())
                || win.context().seatWind() != seatWind(win.winner()) || win.context().roundWind() != roundWind()
                || win.context().flowerCount() != winner.flowers.size() || win.context().lastCopy() != lastCopy(win.tile())
                || win.context().wallLast() != (drawWallLast && win.context().kongWin() != McrWinContext.KongWin.ROBBED)
                || self && (turn != win.winner() || winner.drawn != win.tile() || win.context().kongWin() != drawKong)
                || !self && (win.fromSeat() != claimFrom || win.tile() != claimTile || turn != claimFrom))
                throw new IllegalArgumentException("Saved MCR winning context disagrees with the position");
            if (!self) {
                var supplier = players[win.fromSeat()];
                if (win.context().kongWin() == McrWinContext.KongWin.ROBBED) {
                    if (supplier.melds.stream().noneMatch(meld -> meld.type() == Meld.Type.TRIPLET && meld.kind() == Tile.kind(win.tile())))
                        throw new IllegalArgumentException("Robbed kong has no original pung");
                } else if (supplier.river.isEmpty() || supplier.river.get(supplier.river.size() - 1).tile() != win.tile()
                    || !supplier.river.get(supplier.river.size() - 1).called()) {
                    throw new IllegalArgumentException("Winning claim has no source discard");
                }
            }
            var concealed = new ArrayList<>(winner.hand);
            concealed.remove(Integer.valueOf(win.tile()));
            if (!win.score().equals(McrHandAnalyzer.score(concealed, winner.melds, win.winner(), win.tile(), win.context()))
                || !win.equals(McrSettlement.win(win.winner(), win.fromSeat(), win.tile(), win.context(), win.score())))
                throw new IllegalArgumentException("Invalid saved MCR winning result");
        }
    }

    private static void checkSeat(int seat) {
        if (seat < 0 || seat >= 4) throw new IllegalArgumentException("MCR seats must be in 0..3");
    }
}
