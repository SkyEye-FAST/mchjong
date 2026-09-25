package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;

/** Pure snapshot comparison, shared by playback and tests. A newly observed table is silent. */
public final class TableAudioEvents {
    public record Cue(String sound, String voice, int delay) {}
    private TableAudioEvents() {}

    public static List<Cue> between(TableView before, TableView after) {
        if (before == null || after == null || !before.tableId().equals(after.tableId())
            || before.viewerSeat() != after.viewerSeat() || !before.rules().equals(after.rules())
            || after.revision() <= before.revision()) return List.of();
        var cues = new ArrayList<Cue>();
        if (before.handNumber() != after.handNumber()) {
            if (after.phase() == Game.Phase.TURN) {
                cues.add(effect("wall", 0));
                cues.add(effect("deal", 10));
            }
            return List.copyOf(cues);
        }
        for (int seat = 0; seat < after.seats().size(); seat++) {
            var old = before.seats().get(seat);
            var next = after.seats().get(seat);
            for (int i = old.river().size(); i < next.river().size(); i++) {
                var discard = next.river().get(i);
                cues.add(effect(discard.tsumogiri() ? "tsumogiri" : "tedashi", 0));
                if (discard.riichi() && !old.riichi())
                    cues.add(new Cue("riichi", seat == after.viewerSeat() ? next.doubleRiichi() ? "double_riichi" : "riichi" : null, 0));
            }
            for (int i = 0; i < next.melds().size(); i++) {
                var meld = next.melds().get(i);
                if (i >= old.melds().size() || !meld.equals(old.melds().get(i))) {
                    String type = meld.kan() ? "kan" : meld.type().name().toLowerCase(java.util.Locale.ROOT);
                    cues.add(seat == after.viewerSeat() ? voice(type) : effect(type, 0));
                }
            }
            if (next.norths().size() > old.norths().size())
                cues.add(seat == after.viewerSeat() ? voice("nuki") : effect("nuki", 0));
            if (next.drawn() != Tile.ABSENT && (old.drawn() == Tile.ABSENT || next.hand().size() > old.hand().size()))
                cues.add(effect("draw", 0));
        }
        boolean ended = after.phase() == Game.Phase.HAND_END || after.phase() == Game.Phase.MATCH_END;
        boolean wasEnded = before.phase() == Game.Phase.HAND_END || before.phase() == Game.Phase.MATCH_END;
        if (ended && !wasEnded) {
            String result = after.result().equals("ron") || after.result().equals("tsumo") ? after.result() : "draw_end";
            boolean ownWin = after.wins().stream().anyMatch(win -> win.seat() == after.viewerSeat());
            cues.add(ownWin || result.equals("draw_end") ? voice(result) : effect(result, 0));
        } else if (after.phase() == Game.Phase.TURN && after.viewerSeat() == after.turn()
            && after.decision() != before.decision()) cues.add(effect("turn", 0));
        return List.copyOf(cues);
    }

    private static Cue effect(String sound, int delay) { return new Cue(sound, null, delay); }
    private static Cue voice(String sound) { return new Cue(sound, sound, 0); }
}
