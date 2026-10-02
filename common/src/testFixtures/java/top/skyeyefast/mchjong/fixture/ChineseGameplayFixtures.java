package top.skyeyefast.mchjong.fixture;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import top.skyeyefast.mchjong.engine.*;

/** Real, stock-conserving games driven exclusively by engine-issued actions.
 * Claims are preferred and wins passed to exercise public zones through exhaustion. */
public final class ChineseGameplayFixtures {
    private ChineseGameplayFixtures() {}

    public static McrView mcr(long seed, int remaining, int viewer, Consumer<McrView> inspect) {
        var game = new McrGame(seed);
        var random = new Random(seed);
        for (int step = 0; step < 1000; step++) {
            game.validate();
            var view = game.view(viewer);
            inspect.accept(view);
            boolean ended = view.phase() == McrGame.Phase.HAND_END || view.phase() == McrGame.Phase.MATCH_END;
            if (ended || view.remaining() <= remaining && view.phase() == McrGame.Phase.TURN) return view;
            boolean acted = false;
            for (int seat = 0; seat < 4; seat++) {
                var actions = game.actions(seat);
                if (actions.isEmpty()) continue;
                int index = choose(actions.stream().map(action -> switch (action.type()) {
                    case MELDED_KONG, CONCEALED_KONG -> 0;
                    case PUNG, CHOW -> 1;
                    case DRAW, REPLACE_FLOWER -> 2;
                    case DISCARD -> 3;
                    case PASS -> 4;
                    case WIN -> 20;
                }).toList(), random);
                if (!game.act(seat, game.decision(), index)) throw new IllegalStateException("Rejected MCR fixture action");
                acted = true;
                break;
            }
            if (!acted) throw new IllegalStateException("Stalled MCR fixture");
        }
        throw new IllegalStateException("MCR fixture did not finish");
    }

    public static SichuanView sichuan(long seed, boolean eastWest, int remaining, int viewer, boolean acceptWins, Consumer<SichuanView> inspect) {
        var rules = (eastWest ? SichuanPreset.SBR_2025 : SichuanPreset.TFMJ_2024).config();
        var game = new SichuanGame(seed, rules, Tile.sichuanSet());
        var random = new Random(seed);
        for (int step = 0; step < 1000; step++) {
            game.validate();
            var view = game.view(viewer);
            inspect.accept(view);
            long wallCount = view.wall().slots().stream().filter(tile -> tile != Tile.ABSENT).count();
            if (game.ended() || wallCount <= remaining && view.phase() == SichuanGame.Phase.TURN) return view;
            boolean acted = false;
            for (int seat = 0; seat < 4; seat++) {
                var actions = game.actions(seat);
                if (actions.isEmpty()) continue;
                var own = game.view(seat).seats().get(seat);
                int index = choose(actions.stream().map(action -> switch (action.type()) {
                    case VOID_SUIT -> (int) own.hand().stream().filter(tile -> Tile.kind(tile) / 9 == action.suit()).count();
                    case CONCEALED_KONG, ADDED_KONG, DISCARD_KONG -> 0;
                    case PUNG -> 1;
                    case DRAW -> 2;
                    case DISCARD -> 3;
                    case PASS -> 4;
                    case WIN -> acceptWins ? -1 : 20;
                }).toList(), random);
                if (!game.act(seat, game.decision(), index)) throw new IllegalStateException("Rejected Sichuan fixture action");
                acted = true;
                break;
            }
            if (!acted) throw new IllegalStateException("Stalled Sichuan fixture");
        }
        throw new IllegalStateException("Sichuan fixture did not finish");
    }

    public static McrView fourKongsAndEightFlowers() {
        var opening = McrOpening.of(0, new McrOpening.Roll(2, 3), new McrOpening.Roll(3, 4));
        var slots = new ArrayList<>(java.util.Collections.nCopies(144, Tile.ABSENT));
        var dealerTiles = new ArrayList<Integer>();
        for (int tile = 136; tile < 144; tile++) dealerTiles.add(tile);
        for (int tile = 0; tile < 6; tile++) dealerTiles.add(tile);
        int dealerIndex = 0;
        for (var take : McrWallLayout.initialDeal(opening)) if (take.seat() == 0)
            slots.set(take.slot(), dealerTiles.get(dealerIndex++));
        for (int tail = 0; tail < 12; tail++) slots.set(McrWallLayout.replacementSlot(opening, tail), 6 + tail);
        var unused = new ArrayList<>(Tile.mcrSet());
        unused.removeAll(slots);
        for (int slot = 0; slot < slots.size(); slot++) if (slots.get(slot) == Tile.ABSENT) slots.set(slot, unused.removeFirst());
        var game = new McrGame(711, slots, opening);
        for (int step = 0; step < 20; step++) {
            game.validate(); // Checks every physical ID, including all ordinary copies and flowers.
            var view = game.view(0);
            if (view.phase() == McrGame.Phase.TURN && view.seats().get(0).melds().size() == 4) return view;
            var actions = game.actions(0);
            int index = -1;
            for (int action = 0; action < actions.size(); action++)
                if (actions.get(action).type() == McrAction.Type.REPLACE_FLOWER || actions.get(action).type() == McrAction.Type.CONCEALED_KONG) { index = action; break; }
            if (index < 0 || !game.act(0, game.decision(), index)) throw new IllegalStateException("Cannot reach four-kong flower fixture");
        }
        throw new IllegalStateException("Flower fixture did not finish");
    }

    private static int choose(List<Integer> ranks, Random random) {
        int rank = ranks.stream().mapToInt(Integer::intValue).min().orElseThrow();
        var candidates = new ArrayList<Integer>();
        for (int index = 0; index < ranks.size(); index++) if (ranks.get(index) == rank) candidates.add(index);
        return candidates.get(random.nextInt(candidates.size()));
    }
}
