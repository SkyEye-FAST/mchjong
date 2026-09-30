package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.engine.Tile;

/** Pure snapshot comparison, shared by playback and tests. A newly observed table is silent. */
public final class RiichiAudioEvents {
    public record Cue(String sound, String voice, int delay, int seat) {}
    private RiichiAudioEvents() {}

    public static List<Cue> opening(RiichiView view) {
        return view.phase() == RiichiView.Phase.TURN && view.handNumber() == 1
            && view.seats().stream().allMatch(seat -> seat.river().isEmpty())
            ? List.of(effect("wall", 0), effect("deal", 10)) : List.of();
    }

    public static List<Cue> between(RiichiView before, RiichiView after) {
        if (before == null || after == null || !before.tableId().equals(after.tableId())
            || before.viewerSeat() != after.viewerSeat() || !before.rules().equals(after.rules())
            || after.revision() <= before.revision()) return List.of();
        var cues = new ArrayList<Cue>();
        if (before.handNumber() != after.handNumber()) {
            if (after.phase() == RiichiView.Phase.TURN) {
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
                    cues.add(new Cue("riichi", next.doubleRiichi() ? "double_riichi" : "riichi", 0, seat));
            }
            for (int i = 0; i < next.melds().size(); i++) {
                var meld = next.melds().get(i);
                if (i >= old.melds().size() || !meld.equals(old.melds().get(i))) {
                    String type = switch (meld.type()) {
                        case SEQUENCE -> "chi";
                        case TRIPLET -> "pon";
                        case OPEN_QUAD, CONCEALED_QUAD, ADDED_QUAD -> "kan";
                    };
                    cues.add(voice(type, seat));
                }
            }
            if (next.norths().size() > old.norths().size())
                cues.add(voice("nuki", seat));
            if (next.drawn() != Tile.ABSENT && (old.drawn() == Tile.ABSENT || next.hand().size() > old.hand().size()))
                cues.add(effect("draw", 0));
        }
        boolean ended = after.phase() == RiichiView.Phase.HAND_END || after.phase() == RiichiView.Phase.MATCH_END;
        boolean wasEnded = before.phase() == RiichiView.Phase.HAND_END || before.phase() == RiichiView.Phase.MATCH_END;
        if (ended && !wasEnded) {
            String result = after.result().equals("ron") || after.result().equals("tsumo") ? after.result() : "draw_end";
            if (result.equals("draw_end")) cues.add(voice(result, -1));
            else {
                cues.add(effect(result, 0));
                for (var win : after.wins()) cues.add(new Cue(null, result, 0, win.seat()));
            }
        } else if (after.phase() == RiichiView.Phase.TURN && after.viewerSeat() == after.turn()
            && after.decision() != before.decision()) cues.add(effect("turn", 0));
        return List.copyOf(cues);
    }

    private static Cue effect(String sound, int delay) { return new Cue(sound, null, delay, -1); }
    private static Cue voice(String sound, int seat) { return new Cue(sound, sound, 0, seat); }
}
