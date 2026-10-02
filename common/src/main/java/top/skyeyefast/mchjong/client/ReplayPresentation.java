package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.*;

/** Authorized replay records adapted to the same controls, table and receipts for every variant. */
record ReplayPresentation(List<Frame> frames, List<Decision> decisions, TableResultState result,
                          List<Integer> wall, List<List<Integer>> wallSlots, int deadStart,
                          List<Integer> doraSlots, List<Integer> uraSlots) {
    record Frame(TableBoardState board, Component caption, boolean settled, List<Integer> dora, int rawCursor, int replacements) {}
    record Decision(int index, int cursor, int seat, int selected, List<Component> options) {}
    static List<TableParticipant> participants(ReplayMatch match) {
        return match.participants().stream().map(p -> new TableParticipant(p.id(), p.name(), p.bot(), false, BotDifficulty.EASY, null, false)).toList();
    }
    static Component roundLabel(ReplayMatch match, int index) {
        return switch (match.variant()) {
            case RIICHI -> {
                var h = match.riichi().hands().get(index); int players = match.participants().size();
                yield Component.translatable("ui.mchjong.round", Component.translatable("wind.mchjong."
                    + new String[]{"east", "south", "west", "north"}[Math.min(3, h.round() / players)]), h.round() % players + 1, h.honba());
            }
            case MCR -> Component.translatable("mcr.mchjong.indicator_round", Component.translatable("wind.mchjong."
                + new String[]{"east", "south", "west", "north"}[index / 4] + ".short"), index + 1);
            case SICHUAN -> Component.translatable("sichuan.mchjong.indicator_round", index + 1, match.sichuan().rules().matchHands());
        };
    }
    static ReplayPresentation of(ReplayMatch match, int index) {
        return switch (match.variant()) {
            case RIICHI -> riichi(match, index);
            case MCR -> mcr(match, index);
            case SICHUAN -> sichuan(match, index);
        };
    }
    private static Component caption(ReplayMatch match, int seat, String key) {
        return seat < 0 ? Component.translatable(key) : Component.translatable("replay.mchjong.player_action", match.participants().get(seat).name(), Component.translatable(key));
    }
    private static Component action(String key, List<Integer> tiles) {
        var label = Component.translatable(key);
        for (int tile : tiles) label.append(" ").append(Tile.notation(Tile.kind(tile)));
        return label;
    }
    private static List<List<Integer>> slots(int size, int players, int breakOffset) {
        var sides = new ArrayList<List<Integer>>();
        int stacks = size / (players * 2);
        for (int seat = 0; seat < players; seat++) {
            var entries = new ArrayList<Integer>();
            for (int layer = 0; layer < 2; layer++) for (int column = 0; column < stacks; column++) {
                int target = seat * stacks + column, found = -1;
                for (int slot = layer; slot < size; slot += 2) if (WallLayout.stack(slot, breakOffset, size) == target) { found = slot; break; }
                entries.add(found);
            }
            sides.add(List.copyOf(entries));
        }
        return List.copyOf(sides);
    }
    private static ReplayPresentation riichi(ReplayMatch match, int index) {
        var h = match.riichi().hands().get(index); var timeline = ReplayPlayback.timeline(match, index);
        var frames = timeline.frames().stream().map(f -> {
            var e = f.event(); String key = e == null ? "replay.mchjong.initial" : switch (e.kind()) {
                case DRAW -> "replay.mchjong.draw";
                case DISCARD -> e.riichi() ? "action.mchjong.riichi" : "action.mchjong.discard";
                case NUKI -> "action.mchjong.nuki";
                case MELD -> "action.mchjong." + e.meld().type().name().toLowerCase(Locale.ROOT);
                case RIICHI -> "replay.mchjong.deposit";
                case DORA -> "ui.mchjong.result_indicators";
            };
            return new Frame(TableBoardState.replay(match, h, f, 0), f.settled() ? Component.translatable("result.mchjong." + h.result())
                : caption(match, e == null ? -1 : e.seat(), key), f.settled(), f.dora(), f.rawCursor(), (int) h.events().stream().limit(f.rawCursor()).filter(event -> event.committed() && (event.kind() == ReplayHand.Kind.NUKI || event.kind() == ReplayHand.Kind.MELD && event.meld() != null && event.meld().quad())).count());
        }).toList();
        var decisions = new ArrayList<Decision>();
        for (int i = 0; i < h.decisions().size(); i++) {
            var d = h.decisions().get(i); int cursor = 0;
            for (int n = 0; n < frames.size() && frames.get(n).rawCursor() <= d.eventCursor(); n++) cursor = n;
            if (d.options().size() > 1 || d.options().get(d.selected()).type() != RiichiAction.Type.DISCARD && d.options().get(d.selected()).type() != RiichiAction.Type.PASS)
                decisions.add(new Decision(i, cursor, d.seat(), d.selected(), d.options().stream().map(a -> action(a.translationKey(), a.tiles())).toList()));
        }
        return new ReplayPresentation(frames, List.copyOf(decisions), TableResultState.replay(match, h, 0), h.wall().tiles(),
            slots(h.wall().tiles().size(), match.participants().size(), h.wall().breakOffset()), h.wall().tiles().size() - 14, h.wall().dora(), h.wall().ura());
    }
    private static int beforeAction(List<Integer> cursors, int action) {
        for (int i = 1; i < cursors.size(); i++) if (cursors.get(i) >= action) return i - 1;
        return cursors.size() - 1;
    }
    private static ReplayPresentation mcr(ReplayMatch match, int index) {
        var h = match.mcr().hands().get(index); var timeline = McrReplayPlayback.timeline(match, index);
        var frames = timeline.frames().stream().map(f -> {
            var e = f.event(); String key = e == null ? "replay.mchjong.initial" : switch (e.kind()) {
                case DRAW -> "mcr.mchjong.action.draw";
                case FLOWER_REPLACEMENT -> "mcr.mchjong.action.replace_flower";
                case DISCARD -> "mcr.mchjong.action.discard";
                case RESPONSE -> "mcr.mchjong.responded";
                case CHOW -> "mcr.mchjong.action.chow";
                case PUNG -> "mcr.mchjong.action.pung";
                case KONG -> "mcr.mchjong.action.melded_kong";
                case WIN -> "mcr.mchjong.action.win";
                case PASS -> "mcr.mchjong.action.pass";
                case WRONG_WIN -> "mcr.mchjong.win_forbidden";
                case SETTLEMENT -> "mcr.mchjong.results";
            };
            return new Frame(TableBoardState.mcr(f.view(), 0).replay(0), caption(match, e == null ? -1 : e.seat(), key),
                f.view().result() != null, List.of(), e == null ? 0 : e.actionCursor(), 0);
        }).toList();
        var cursors = frames.stream().map(Frame::rawCursor).toList(); var decisions = new ArrayList<Decision>();
        for (int i = 0; i < h.decisions().size(); i++) { var d = h.decisions().get(i);
            if (d.options().size() > 1 || switch (d.options().get(d.selected()).type()) { case DRAW, DISCARD, REPLACE_FLOWER, PASS -> false; default -> true; }) decisions.add(new Decision(i, beforeAction(cursors, i + 1), d.seat(), d.selected(), d.options().stream()
                .map(a -> action("mcr.mchjong.action." + a.type().name().toLowerCase(Locale.ROOT), a.tiles())).toList())); }
        var sides = new ArrayList<List<Integer>>();
        for (int seat = 0; seat < 4; seat++) { var side = new ArrayList<Integer>();
            for (var layer : McrWallLayout.Layer.values()) for (int stack = 0; stack < 18; stack++) side.add(McrWallLayout.slot(McrWallLayout.stack(seat, stack), layer));
            sides.add(List.copyOf(side)); }
        return new ReplayPresentation(frames, List.copyOf(decisions), TableResultState.mcr(timeline.frames().getLast().view(), participants(match), 0),
            h.wall(), List.copyOf(sides), h.wall().size(), List.of(), List.of());
    }
    private static ReplayPresentation sichuan(ReplayMatch match, int index) {
        var h = match.sichuan().hands().get(index); var timeline = SichuanReplayPlayback.timeline(match, index);
        var frames = timeline.frames().stream().map(f -> {
            var e = f.event(); String key = e == null ? "replay.mchjong.initial" : switch (e.kind()) {
                case VOID_SUIT, VOID_SUITS -> "sichuan.mchjong.action.void_suit";
                case DRAW -> "sichuan.mchjong.action.draw";
                case DISCARD -> "sichuan.mchjong.action.discard";
                case RESPONSE -> "sichuan.mchjong.responded";
                case PASS -> "sichuan.mchjong.action.pass";
                case PUNG -> "sichuan.mchjong.action.pung";
                case KONG -> "sichuan.mchjong.action.discard_kong";
                case ADDED_KONG -> "sichuan.mchjong.action.added_kong";
                case WIN -> "sichuan.mchjong.action.win";
                case PAYMENT -> "sichuan.mchjong.payment." + f.state().ledger().get(e.ledgerId()).type().name().toLowerCase(Locale.ROOT);
                case SETTLEMENT -> "sichuan.mchjong.results";
            };
            var label = e != null && e.kind() == SichuanReplayHand.Kind.SETTLEMENT
                ? Component.translatable(key, f.state().handNumber())
                : caption(match, e == null ? -1 : e.seat(), key);
            return new Frame(TableBoardState.replay(f, 0), label, f.state().result() != null,
                List.of(), e == null ? 0 : e.actionCursor(), 0);
        }).toList();
        var cursors = frames.stream().map(Frame::rawCursor).toList(); var decisions = new ArrayList<Decision>();
        for (int i = 0; i < h.decisions().size(); i++) { var d = h.decisions().get(i);
            if (!d.adjudication() && (d.options().size() > 1 || switch (d.options().get(d.selected()).type()) { case DRAW, DISCARD, PASS -> false; default -> true; })) decisions.add(new Decision(i, beforeAction(cursors, i + 1), d.seat(), d.selected(), d.options().stream()
                .map(a -> action(a.type() == SichuanAction.Type.VOID_SUIT ? "sichuan.mchjong.suit." + a.suit()
                    : "sichuan.mchjong.action." + a.type().name().toLowerCase(Locale.ROOT), a.tiles())).toList())); }
        var sides = new ArrayList<List<Integer>>();
        for (int seat = 0; seat < 4; seat++) { var side = new ArrayList<Integer>();
            for (int layer = 0; layer < 2; layer++) for (int stack = 0; stack < SichuanWallLayout.stacks(seat, h.opening().eastWestLongWall()); stack++)
                side.add(SichuanWallLayout.slot(seat, stack, layer, h.opening().eastWestLongWall()));
            sides.add(List.copyOf(side)); }
        var last = timeline.frames().getLast();
        return new ReplayPresentation(frames, List.copyOf(decisions), TableResultState.sichuan(match.sichuan().rules(), h.result(), last.seats(), h.finalPoints(), 0,
            participants(match)), h.opening().slots(), List.copyOf(sides), 108, List.of(), List.of());
    }
}
