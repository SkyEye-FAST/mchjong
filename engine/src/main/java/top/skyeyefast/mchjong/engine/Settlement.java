package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Payments and match progression are separate from action legality and transport. */
final class Settlement {
    private Settlement() {}

    static void win(Game game, List<Integer> winners, int from, int tile) {
        int[] before = points(game);
        game.wins.clear();
        for (int index = 0; index < winners.size(); index++) {
            int seat = winners.get(index);
            HandScore score = LegalActions.score(game, seat, tile, from < 0);
            if (score == null) throw new IllegalStateException("Settlement requires a legal winning hand");
            game.wins.add(new TableView.Win(seat, from, tile, score));
            game.exposed[seat] = true;
            boolean receiveHonba = index == 0;
            var winBefore = ReplayRecorder.points(game);
            payWin(game, seat, from, score, receiveHonba ? game.honba : 0);
            if (game.recorder != null) game.recorder.win(game, seat, from, tile, score, winBefore,
                pao(game, seat, score).keySet().stream().findFirst().orElse(-1), receiveHonba ? game.honba : 0);
        }
        game.players[winners.getFirst()].points += game.riichiSticks * 1000;
        if (game.recorder != null) game.recorder.awardDeposit(game.riichiSticks);
        game.riichiSticks = 0;
        finish(game, winners.contains(game.dealer), false, false, from < 0 ? "tsumo" : "ron", before);
    }

    private static void payWin(Game game, int winner, int from, HandScore score, int honba) {
        Map<Integer, Integer> liable = pao(game, winner, score);
        int liableUnits = liable.values().stream().mapToInt(Integer::intValue).sum();
        int normalUnits = score.yakuman() - liableUnits;
        if (score.yakuman() == 0 || normalUnits > 0) {
            int ron = score.yakuman() == 0 ? score.ron() : normalUnits * (winner == game.dealer ? 48000 : 32000);
            int dealerPay = score.yakuman() == 0 ? score.tsumoDealer() : normalUnits * 16000;
            int childPay = score.yakuman() == 0 ? score.tsumoChild() : normalUnits * (winner == game.dealer ? 16000 : 8000);
            if (from >= 0) transfer(game, from, winner, ron);
            else for (int payer = 0; payer < game.rules.players(); payer++) if (payer != winner) {
                transfer(game, payer, winner, payer == game.dealer ? dealerPay : childPay);
            }
        }
        for (var entry : liable.entrySet()) {
            int payer = entry.getKey();
            int total = entry.getValue() * (winner == game.dealer ? 48000 : 32000);
            if (from < 0) {
                if (game.rules.sanma()) total -= entry.getValue() * (winner == game.dealer ? 16000 : 8000);
                transfer(game, payer, winner, total);
            } else if (payer == from) transfer(game, from, winner, total);
            else {
                transfer(game, payer, winner, total / 2);
                transfer(game, from, winner, total / 2);
            }
        }
        int honbaTotal = honba * 100 * (game.rules.players() - 1);
        if (!liable.isEmpty() && !(from >= 0 && game.rules.paoRonHonbaByDiscarder())
            && !(from < 0 && game.rules.paoTsumoHonbaShared() && normalUnits > 0))
            transfer(game, liable.keySet().iterator().next(), winner, honbaTotal);
        else if (from >= 0) transfer(game, from, winner, honbaTotal);
        else for (int payer = 0; payer < game.rules.players(); payer++) if (payer != winner) {
            transfer(game, payer, winner, honba * 100);
        }
    }

    private static Map<Integer, Integer> pao(Game game, int seat, HandScore score) {
        var liable = new LinkedHashMap<Integer, Integer>();
        if (score.yakuman() == 0) return liable;
        PlayerState player = game.players[seat];
        if (player.dragonPao >= 0 && score.yaku().contains("Daisangen")) liable.merge(player.dragonPao, 1, Integer::sum);
        if (player.windPao >= 0 && score.yaku().contains("Daisushi")) {
            liable.merge(player.windPao, game.rules.doubleYakuman() ? 2 : 1, Integer::sum);
        }
        if (player.kanPao >= 0 && score.yaku().contains("Sukantsu")) liable.merge(player.kanPao, 1, Integer::sum);
        if (game.rules.wholeHandPao() && !liable.isEmpty()) {
            int payer = liable.keySet().iterator().next();
            liable.clear();
            liable.put(payer, score.yakuman());
        } else if (!game.rules.compoundYakuman()) {
            int remaining = score.yakuman();
            for (var entries = liable.entrySet().iterator(); entries.hasNext();) {
                var entry = entries.next();
                int units = Math.min(remaining, entry.getValue());
                if (units == 0) entries.remove();
                else entry.setValue(units);
                remaining -= units;
            }
        }
        return liable;
    }

    static void abort(Game game, String reason) {
        finish(game, true, true, true, reason, points(game));
    }

    static void exhaustive(Game game) {
        int[] before = points(game);
        boolean[] tenpai = new boolean[4];
        var nagashi = new ArrayList<Integer>();
        int count = 0;
        for (int seat = 0; seat < game.rules.players(); seat++) {
            PlayerState player = game.players[seat];
            tenpai[seat] = LegalActions.formalTenpai(game, seat);
            if (tenpai[seat]) count++;
            game.exposed[seat] = tenpai[seat];
            if (game.rules.nagashiMangan() && !player.river.isEmpty()
                && (game.rules.nagashiAllowsCalls() || player.closed())
                && player.river.stream().allMatch(d -> !d.called() && Tile.terminalOrHonor(Tile.kind(d.tile())))) {
                nagashi.add(seat);
            }
        }
        if (!nagashi.isEmpty()) {
            for (int seat : nagashi) {
                HandScore score = new HandScore(5, 0, 0, seat == game.dealer ? 12000 : 8000,
                    4000, seat == game.dealer ? 4000 : 2000, List.of("Nagashi"), 0);
                game.wins.add(new TableView.Win(seat, -1, Tile.ABSENT, score));
                // Nagashi replaces noten payments. It does not take the riichi pot or honba.
                payWin(game, seat, -1, score, 0);
            }
        } else if (count > 0 && count < game.rules.players()) {
            int pool = game.rules.sanma() ? 2000 : 3000;
            for (int seat = 0; seat < game.rules.players(); seat++) {
                game.players[seat].points += tenpai[seat] ? pool / count : -pool / (game.rules.players() - count);
            }
        }
        finish(game, tenpai[game.dealer], true, false, nagashi.isEmpty() ? "exhaustive" : "nagashi", before);
    }

    private static void transfer(Game game, int payer, int recipient, int points) {
        if (payer == recipient || points < 0) throw new IllegalStateException("Invalid point transfer");
        game.players[payer].points -= points;
        game.players[recipient].points += points;
    }

    private static int[] points(Game game) { return Arrays.stream(game.players).mapToInt(player -> player.points).toArray(); }

    private static void finish(Game game, boolean repeats, boolean draw, boolean abort, String reason, int[] before) {
        game.dealerRepeats = repeats;
        game.drawResult = draw;
        game.abortResult = abort;
        game.result = reason;
        for (PlayerState player : game.players) player.ready = false;
        boolean end = matchEnds(game);
        game.newDecision(end ? Game.Phase.MATCH_END : Game.Phase.HAND_END);
        if (end) finalScores(game);
        game.deltas = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) game.deltas.add(game.players[seat].points - before[seat]);
        game.finishReplay();
    }

    private static boolean matchEnds(Game game) {
        int n = game.rules.players();
        if (game.rules.bankruptcy()) for (int seat = 0; seat < n; seat++) if (game.players[seat].points < 0) return true;
        if (game.round < game.rules.scheduledRounds() - 1 || game.abortResult) return false;
        int top = ranking(game).getFirst();
        if (game.dealerRepeats) {
            return game.rules.agariYame() && top == game.dealer && game.players[top].points >= game.rules.targetPoints();
        }
        if (!game.rules.extension()) return true;
        return game.players[top].points >= game.rules.targetPoints()
            || game.round >= game.rules.scheduledRounds() + n - 1;
    }

    private static List<Integer> ranking(Game game) {
        var ranking = new ArrayList<Integer>();
        for (int i = 0; i < game.rules.players(); i++) ranking.add(i);
        ranking.sort(Comparator.<Integer>comparingInt(seat -> -game.players[seat].points)
            .thenComparingInt(seat -> Math.floorMod(seat - game.initialDealer, game.rules.players())));
        return ranking;
    }

    private static void finalScores(Game game) {
        var ranking = ranking(game);
        var groups = new ArrayList<List<Integer>>();
        for (int seat : ranking) {
            if (!groups.isEmpty() && game.rules.sharedRanks()
                && game.players[groups.getLast().getFirst()].points == game.players[seat].points) groups.getLast().add(seat);
            else groups.add(new ArrayList<>(List.of(seat)));
        }
        List<Integer> top = groups.getFirst();
        if (game.rules.awardFinalDeposits()) {
            for (int i = 0; i < top.size(); i++)
                game.players[top.get(i)].points += game.riichiSticks * (10 / top.size() + (i < 10 % top.size() ? 1 : 0)) * 100;
            game.riichiSticks = 0;
        }
        int floating = (int) ranking.stream().filter(seat -> game.players[seat].points >= game.rules.returnPoints()).count();
        int[] bonus = game.rules.placementPoints(floating);
        bonus[0] += (game.rules.returnPoints() - game.rules.startingPoints()) * game.rules.players();
        game.finalScores = new ArrayList<>(Collections.nCopies(game.rules.players(), 0.0));
        game.finalRanks = new ArrayList<>(Collections.nCopies(game.rules.players(), 0));
        int place = 0;
        for (List<Integer> group : groups) {
            int rank = place + 1;
            int placement = 0;
            for (int i = 0; i < group.size(); i++) placement += bonus[place++];
            for (int i = 0; i < group.size(); i++) {
                int seat = group.get(i);
                double share = game.rules.roundSharedPlacement()
                    ? (Math.floorDiv(placement / 100, group.size()) + (i < Math.floorMod(placement / 100, group.size()) ? 1 : 0)) / 10.0
                    : placement / (1000.0 * group.size());
                game.finalRanks.set(seat, rank);
                game.finalScores.set(seat, (game.players[seat].points - game.rules.returnPoints()) / 1000.0 + share);
            }
        }
    }
}
