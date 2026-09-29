package top.skyeyefast.mchjong.engine;

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
                        PlayerHandVisibility playerHandVisibility, boolean openHands, ExitVote exitVote, Handling handling, AutoPlay autoPlay,
                        boolean ronBlocked, int riichiHan, Map<Integer, Long> riichiSafeTiles,
                        boolean convenienceHints, List<ExternalBot> externalBots, int settlementTicks, int settlementSkippedSeats) {
    public enum Phase { SHUFFLE, BUILD_WALL, DEAL, DRAW, TURN, REACTION, HAND_END, MATCH_END }
    // ronBlocked and riichiHan describe only the recipient. riichiHan is the
    // established declaration, or the current declaration's one/two-han value.
    // riichiSafeTiles contains public passed-discard kind masks, keyed by target seat.
    /** Public physical positions only: slot indices never reveal a concealed tile identity. */
    public record Handling(int builtWalls, int sourceSlot, int packetSize, int diceOne, int diceTwo, boolean diceHeld) {}
    public record Seat(boolean entityBot, String name, boolean occupied, boolean bot, boolean ready, int points,
                       List<Integer> hand, int drawn, List<Meld> melds, List<Discard> river,
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
}
