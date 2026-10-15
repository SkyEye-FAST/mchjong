package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Detached private match state; room identity and configuration live in RiichiSession.State. */
public record RiichiGameState(int handNumber, int age, List<RiichiPlayerState.Saved> players, RiichiGame.Phase phase,
                              int dealer, int initialDealer, int round, int honba, int riichiSticks, int turn,
                              Wall.Saved wall, int lastTile, int lastFrom, RiichiAction pending,
                              boolean uninterrupted, boolean fourKanAbort, boolean dealerRepeats,
                              boolean drawResult, boolean abortResult, List<Boolean> exposed,
                              List<Integer> replies, List<List<RiichiAction>> options, List<RiichiView.Win> wins,
                              long presentedDecision, int presentedSeats, long skippedDecision, int skippedSeats,
                              String result, List<Integer> deltas, List<Double> finalScores,
                              List<Double> finalUma, List<Integer> finalRanks, ReplayMatch replay,
                              ReplayRecorderState recorder, List<Integer> moveTicks, List<Integer> reserveTicks,
                              ManualHandling.Saved handling) {
    public RiichiGameState {
        players = List.copyOf(players);
        exposed = List.copyOf(exposed);
        replies = List.copyOf(replies);
        options = options.stream().map(List::copyOf).toList();
        wins = List.copyOf(wins);
        deltas = List.copyOf(deltas);
        finalScores = List.copyOf(finalScores);
        finalUma = List.copyOf(finalUma);
        finalRanks = List.copyOf(finalRanks);
        moveTicks = List.copyOf(moveTicks);
        reserveTicks = List.copyOf(reserveTicks);
        Objects.requireNonNull(phase);
        Objects.requireNonNull(result);
        Objects.requireNonNull(handling);
        if (handNumber < 1 || players.size() != 4 || exposed.size() != 4 || replies.size() != 4
            || options.size() != 4 || moveTicks.size() != 4 || reserveTicks.size() != 4
            || presentedSeats < 0 || presentedSeats > 15 || skippedSeats < 0 || skippedSeats > 15
            || recorder != null && wall == null)
            throw new IllegalArgumentException("Invalid Riichi match state");
    }

    static RiichiGameState of(RiichiGame game) {
        validate(game);
        return new RiichiGameState(game.handNumber, game.age, Arrays.stream(game.players).map(RiichiPlayerState::save).toList(), game.phase,
            game.dealer, game.initialDealer, game.round, game.honba, game.riichiSticks, game.turn, game.wall == null ? null : game.wall.save(),
            game.lastTile, game.lastFrom, game.pending, game.uninterrupted, game.fourKanAbort, game.dealerRepeats, game.drawResult, game.abortResult,
            java.util.stream.IntStream.range(0, 4).mapToObj(i -> game.exposed[i]).toList(),
            Arrays.stream(game.replies).boxed().toList(), game.options, game.wins,
            game.presentedDecision, game.presentedSeats, game.skippedDecision, game.skippedSeats, game.result, game.deltas,
            game.finalScores, game.finalUma, game.finalRanks, game.replay, game.recorder == null ? null : game.recorder.save(),
            Arrays.stream(game.moveTicks).boxed().toList(), Arrays.stream(game.reserveTicks).boxed().toList(), game.handling.save());
    }

    static RiichiGame restore(RiichiSession session, RiichiGameState state, long savedDecision) {
        var game = new RiichiGame(session);
        game.handNumber = state.handNumber();
        game.age = state.age();
        game.players = new RiichiPlayerState[4];
        for (int seat = 0; seat < 4; seat++)
            game.players[seat] = RiichiPlayerState.restore(state.players().get(seat), session.participants[seat]);
        game.phase = state.phase();
        game.dealer = state.dealer();
        game.initialDealer = state.initialDealer();
        game.round = state.round();
        game.honba = state.honba();
        game.riichiSticks = state.riichiSticks();
        game.turn = state.turn();
        game.wall = state.wall() == null ? null : Wall.restore(state.wall());
        game.lastTile = state.lastTile();
        game.lastFrom = state.lastFrom();
        game.pending = state.pending();
        game.uninterrupted = state.uninterrupted();
        game.fourKanAbort = state.fourKanAbort();
        game.dealerRepeats = state.dealerRepeats();
        game.drawResult = state.drawResult();
        game.abortResult = state.abortResult();
        for (int seat = 0; seat < 4; seat++) {
            game.exposed[seat] = state.exposed().get(seat);
            game.replies[seat] = state.replies().get(seat);
            game.moveTicks[seat] = state.moveTicks().get(seat);
            game.reserveTicks[seat] = state.reserveTicks().get(seat);
        }
        game.options = new ArrayList<>(state.options());
        game.wins = new ArrayList<>(state.wins());
        game.presentedDecision = state.presentedDecision();
        game.presentedSeats = state.presentedSeats();
        game.skippedDecision = state.skippedDecision();
        game.skippedSeats = state.skippedSeats();
        game.result = state.result();
        game.deltas = new ArrayList<>(state.deltas());
        game.finalScores = new ArrayList<>(state.finalScores());
        game.finalUma = new ArrayList<>(state.finalUma());
        game.finalRanks = new ArrayList<>(state.finalRanks());
        game.replay = state.replay();
        game.handling = ManualHandling.restore(state.handling());
        if (state.recorder() != null) game.recorder = ReplayRecorder.restore(game, state.recorder());
        if (game.presentedDecision == savedDecision) game.presentedDecision = session.decision;
        if (game.skippedDecision == savedDecision) game.skippedDecision = session.decision;
        validate(game);
        return game;
    }

    /** Used on loading a saved table and by conservation tests, never as a network input. */
    static void validate(RiichiGame game) {
        game.session.validateRoom();
        if (game.players == null || game.players.length != 4 || Arrays.stream(game.players).anyMatch(Objects::isNull))
            throw new IllegalStateException("Invalid Riichi player state");
        game.rebindPlayers();
        if (game.session.capacity != game.rules.players() || game.session.variant != MahjongVariant.RIICHI)
            throw new IllegalStateException("Riichi rules do not match the room");
        Objects.requireNonNull(game.tableId); Objects.requireNonNull(game.rules); Objects.requireNonNull(game.phase);
        if (game.session.lifecycle != (game.phase == RiichiGame.Phase.MATCH_END ? TableSession.Lifecycle.FINISHED : TableSession.Lifecycle.PLAYING))
            throw new IllegalStateException("Riichi phase does not match the room lifecycle");
        Objects.requireNonNull(game.session.seating).validate(game.rules.players());
        Objects.requireNonNull(game.timeControl); Objects.requireNonNull(game.finalRanks);
        Objects.requireNonNull(game.finalUma);
        Objects.requireNonNull(game.playerHandVisibility);
        Objects.requireNonNull(game.suppliedTiles); Objects.requireNonNull(game.handling);
        if (!game.suppliedTiles.isEmpty() && !Tile.validSet(game.suppliedTiles)) throw new IllegalStateException("Invalid physical set");
        game.handling.validate(game);
        if (game.moveTicks.length != 4 || game.reserveTicks.length != 4) throw new IllegalStateException("Invalid clocks");
        for (int seat = 0; seat < 4; seat++) {
            if (game.moveTicks[seat] < 0 || game.moveTicks[seat] > game.timeControl.moveSeconds() * 20
                || game.reserveTicks[seat] < 0 || game.reserveTicks[seat] > game.timeControl.reserveSeconds() * 20)
                throw new IllegalStateException("Invalid clock allowance");
        }
        if (game.players.length != 4 || game.options.size() != 4 || game.dealer < 0 || game.dealer >= game.rules.players()
            || game.round < 0 || game.round >= game.rules.scheduledRounds() + game.rules.players() || game.honba < 0 || game.riichiSticks < 0) {
            throw new IllegalStateException("Invalid saved table");
        }
        for (RiichiPlayerState player : game.players) {
            Objects.requireNonNull(player.autoPlay);
        }
        if (game.wall == null) return;
        Set<Integer> seen = new HashSet<>();
        for (int tile : game.wall.tiles) if (tile >= 0 && !seen.add(tile)) throw new IllegalStateException("Duplicated wall tile");
        for (int seat = 0; seat < game.rules.players(); seat++) {
            RiichiPlayerState player = game.players[seat];
            for (int tile : player.physicalTiles()) if (!seen.add(tile)) throw new IllegalStateException("Duplicated physical tile: " + tile);
        }
        var supplied = game.suppliedTiles;
        if (!seen.equals(new HashSet<>(supplied))) throw new IllegalStateException("Tile conservation failed");
        long points = game.riichiSticks * 1000L;
        for (int i = 0; i < game.rules.players(); i++) points += game.players[i].points;
        if (points != (long) game.rules.players() * game.rules.startingPoints()) throw new IllegalStateException("Point conservation failed");
    }
}
