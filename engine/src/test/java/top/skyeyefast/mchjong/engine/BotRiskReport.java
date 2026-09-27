package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Observed-policy discard outcomes. Public features are captured before execution. */
final class BotRiskReport {
    record Features(long seed, int rotation, int hand, int seat, String role, int turn, int remaining,
                    int shanten, int live, double points, String mode, double pressure, double danger,
                    int riichiOpponents, boolean locked, boolean declare, boolean open, int tile) {}
    record Pending(Features features, TableView view) {}
    record Outcome(Features features, boolean dealIn, int loss, List<Integer> winners, TableView view) {}
    private final Path path;
    private final List<Pending> pending = new ArrayList<>();
    private final Gson gson = new Gson();
    private int finishedHand = -1;

    BotRiskReport(String path) {
        this.path = Path.of(path).toAbsolutePath();
        try { Files.createDirectories(this.path.getParent()); Files.writeString(this.path, ""); }
        catch (IOException error) { throw new UncheckedIOException(error); }
    }

    void observe(long seed, int rotation, TableView view, Action action) {
        if (action.type() != Action.Type.DISCARD && action.type() != Action.Type.RIICHI) return;
        var analysis = new BotAnalysis(view, BotDifficulty.HARD);
        var state = analysis.initial().discard(action.tiles().getFirst(), action.type() == Action.Type.RIICHI);
        var hand = analysis.evaluate(state, analysis.shape(state), analysis.unseen);
        var self = view.seats().get(view.viewerSeat());
        var features = new Features(seed, rotation, view.handNumber(), view.viewerSeat(),
            view.viewerSeat() == rotation ? "challenger" : "field", self.river().size(), view.remaining(),
            hand.shanten(), hand.live(), hand.points(), analysis.defence.mode(hand).name(), analysis.defence.pressure(),
            analysis.defence.danger(action.tiles().getFirst()),
            (int) analysis.defence.threats.stream().filter(BotDefence.Threat::getRiichi).count(),
            self.riichi(), action.type() == Action.Type.RIICHI, self.melds().stream().anyMatch(m -> !m.closed()), action.tiles().getFirst());
        pending.add(new Pending(features, view));
    }

    void finish(ReplayHand hand) {
        if (hand.number() == finishedHand) return;
        var discards = hand.events().stream().filter(e -> e.kind() == ReplayHand.Kind.DISCARD).toList();
        if (discards.size() != pending.size()) throw new IllegalStateException("Missing observed discard");
        var from = hand.wins().stream().map(ReplayHand.Win::from).filter(s -> s >= 0).collect(Collectors.toSet());
        int terminal = terminalDiscard(hand.events(), from);
        for (int i = 0; i < pending.size(); i++) {
            var sample = pending.get(i);
            if (discards.get(i).seat() != sample.features.seat()) throw new IllegalStateException("Discard order mismatch");
            boolean dealt = i == terminal;
            int loss = dealt ? hand.wins().stream().mapToInt(w -> Math.max(0, -w.deltas().get(sample.features.seat()))).sum() : 0;
            // Only failed decisions retain the redacted input for diagnosis.
            var winners = dealt ? hand.wins().stream().filter(w -> w.from() == sample.features.seat()).map(ReplayHand.Win::seat).toList()
                : List.<Integer>of();
            var row = new Outcome(sample.features, dealt, loss, winners, dealt ? sample.view : null);
            try { Files.writeString(path, gson.toJson(row) + "\n", StandardOpenOption.APPEND); }
            catch (IOException error) { throw new UncheckedIOException(error); }
        }
        pending.clear();
        finishedHand = hand.number();
    }

    static int terminalDiscard(List<ReplayHand.Event> events, Set<Integer> ronFrom) {
        int discard = -1, terminal = -1;
        for (var event : events) {
            if (event.kind() == ReplayHand.Kind.DISCARD) {
                discard++;
                terminal = ronFrom.contains(event.seat()) ? discard : -1;
            } else if (event.kind() == ReplayHand.Kind.MELD || event.kind() == ReplayHand.Kind.NUKI || event.kind() == ReplayHand.Kind.DRAW) {
                terminal = -1;
            }
        }
        return terminal;
    }
}
