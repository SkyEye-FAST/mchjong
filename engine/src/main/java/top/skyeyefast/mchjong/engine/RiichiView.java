package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The live S2C view. Private hands are redacted; completed replays use a separate authorized channel. */
public record RiichiView(UUID tableId, long revision, long decision, int handNumber, RiichiRules rules, Phase phase,
                        int viewerSeat, int dealer, int round, int honba, int riichiSticks,
                        int turn, int remaining, int wallBreak, List<Integer> wall, Focus focus,
                        List<Seat> seats, List<RiichiAction> actions, List<Win> wins,
                        String result, List<Integer> deltas, List<Double> finalScores, List<Double> finalUma,
                        TimeControl timeControl, List<TimeControl.Clock> clocks, List<Integer> finalRanks,
                        PlayerHandVisibility playerHandVisibility, boolean openHands, ExitVote exitVote, Handling handling, RiichiAutoPlay autoPlay,
                        boolean ronBlocked, int riichiHan, Map<Integer, Long> riichiSafeTiles,
                        List<ExternalBot> externalBots, int settlementTicks, int settlementSkippedSeats) {
    public enum Phase { SHUFFLE, BUILD_WALL, DEAL, DRAW, TURN, REACTION, HAND_END, MATCH_END }
    // ronBlocked and riichiHan describe only the recipient. riichiHan is the
    // established declaration, or the current declaration's one/two-han value.
    // riichiSafeTiles contains public passed-discard kind masks, keyed by target seat.
    /** Public physical positions only: slot indices never reveal a concealed tile identity. */
    public record Handling(int builtWalls, int sourceSlot, int packetSize, int diceOne, int diceTwo, boolean diceHeld) {}
    public record Seat(boolean entityBot, String name, boolean occupied, boolean bot, boolean ready, int points,
                       List<Integer> hand, int drawn, List<Meld> melds, List<RiichiDiscard> river,
                       List<Integer> norths, boolean riichi, boolean exposed, boolean doubleRiichi) {
        public Seat {
            hand = List.copyOf(hand); melds = List.copyOf(melds); river = List.copyOf(river);
            norths = List.copyOf(norths);
        }
    }
    public record Win(int seat, int from, int tile, HandScore score) {}
    /** A declaration or discard is public, even while its reaction window is open. */
    public record Focus(int seat, int tile, boolean declaration, int index) {}
    public RiichiView {
        wall = List.copyOf(wall); seats = List.copyOf(seats); actions = List.copyOf(actions);
        wins = List.copyOf(wins); deltas = List.copyOf(deltas); finalScores = List.copyOf(finalScores);
        finalUma = List.copyOf(finalUma);
        clocks = List.copyOf(clocks); finalRanks = List.copyOf(finalRanks);
        riichiSafeTiles = Map.copyOf(riichiSafeTiles);
        externalBots = List.copyOf(externalBots);
        if (settlementTicks < 0 || settlementSkippedSeats < 0) throw new IllegalArgumentException("Invalid settlement view");
    }

    static RiichiView project(RiichiGame game, int viewer, SpectatorHandVisibility spectatorVisibility) {
        var seats = new ArrayList<RiichiView.Seat>();
        RiichiView.Focus focus = null;
        for (int seat = 0; seat < game.rules.players(); seat++) {
            RiichiPlayerState player = game.players[seat];
            boolean visible = game.openHands || seat == viewer || game.exposed[seat]
                || viewer >= 0 && game.playerHandVisibility.reveals(game.players[viewer].riichi)
                || viewer < 0 && spectatorVisibility.reveals(game.playerHandVisibility);
            List<Integer> hand = new ArrayList<>(player.hand);
            if (game.manual || player.autoPlay.sort()) hand.sort(Tile.ORDER);
            if (player.drawn >= 0 && (seat != viewer || game.manual || player.autoPlay.sort())
                && hand.remove(Integer.valueOf(player.drawn))) hand.add(player.drawn);
            if (game.phase == RiichiGame.Phase.REACTION && seat == game.lastFrom)
                focus = new RiichiView.Focus(seat, game.lastTile, game.pending != null,
                    game.pending == null ? player.river.size() - 1 : hand.indexOf(game.lastTile));
            if (!visible) hand.replaceAll(tile -> Tile.HIDDEN);
            seats.add(new RiichiView.Seat(player.member.entityBot, player.member.name, player.member.id != null, player.member.bot, player.member.ready, player.points,
                hand, player.drawn < 0 ? Tile.ABSENT : visible ? player.drawn : Tile.HIDDEN,
                player.melds, player.river, player.norths, player.riichi, game.exposed[seat], player.doubleRiichi));
        }
        boolean ura = game.rules.uraDora() && game.wins.stream().anyMatch(win -> game.players[win.seat()].riichi);
        var clocks = new ArrayList<TimeControl.Clock>();
        for (int seat = 0; seat < game.rules.players(); seat++)
            clocks.add(new TimeControl.Clock(game.moveTicks[seat], game.reserveTicks[seat], game.clockActive(seat)));
        return new RiichiView(game.tableId, game.session.revision, game.session.decision, game.handNumber, game.rules, RiichiView.Phase.valueOf(game.phase.name()), viewer, game.dealer, game.round, game.honba, game.riichiSticks,
            game.turn, game.wall == null ? 0 : game.wall.remaining(), game.wall == null ? 0 : game.wall.breakOffset,
            game.wall == null ? List.of() : game.manual ? game.handling.wallView(game, ura) : game.wall.publicTiles(ura), focus, seats, game.actions(viewer), game.wins, game.result, game.deltas, game.finalScores, game.finalUma,
            game.timeControl, clocks, game.finalRanks, game.playerHandVisibility, game.openHands, game.session.exitVote, game.manual ? game.handling.view(game) : null,
            viewer < 0 || game.manual ? null : game.players[viewer].autoPlay,
            viewer >= 0 && (game.players[viewer].temporaryFuriten || game.players[viewer].riichiFuriten),
            viewer < 0 ? 0 : game.players[viewer].doubleRiichi || game.players[viewer].firstTurn && game.uninterrupted ? 2 : 1,
            game.recorder == null ? Map.of() : game.recorder.riichiSafeTiles(),
            game.session.externalBots(), game.settlementTicks(), game.settlementSkippedSeats());
    }
}
