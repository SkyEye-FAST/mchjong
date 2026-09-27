package top.skyeyefast.mchjong.engine;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** The mjai boundary contains face names and public events, never physical IDs or seeds. */
public final class MjaiProtocol {
    private MjaiProtocol() {}

    public record Position(int hand, int seat, List<Map<String, Object>> events, TableView view) {
        public Position { events = List.copyOf(events); }
    }

    public static String tile(int tile) {
        int kind = Tile.kind(tile);
        if (kind >= 27) return new String[]{"E", "S", "W", "N", "P", "F", "C"}[kind - 27];
        return (kind % 9 + 1) + "mps".substring(kind / 9, kind / 9 + 1) + (Tile.red(tile) ? "r" : "");
    }

    static List<Map<String, Object>> hand(int seat, int round, int dealer, int honba, int sticks,
            List<Integer> points, List<List<Integer>> hands, List<Integer> dora, List<ReplayHand.Event> events) {
        if (hands.size() != 4) throw new IllegalArgumentException("Mjai requires four players");
        var result = new ArrayList<Map<String, Object>>();
        var concealed = new ArrayList<List<String>>();
        for (int i = 0; i < 4; i++) concealed.add(i == seat ? faces(hands.get(i)) : Collections.nCopies(13, "?"));
        result.add(Map.of("type", "start_kyoku", "bakaze", new String[]{"E", "S", "W", "N"}[round / 4],
            "kyoku", round % 4 + 1, "oya", dealer, "honba", honba, "kyotaku", sticks,
            "scores", List.copyOf(points), "tehais", concealed, "dora_marker", tile(dora.get(0))));
        for (var event : events) {
            int actor = event.seat();
            switch (event.kind()) {
                case DRAW -> result.add(Map.of("type", "tsumo", "actor", actor, "pai", actor == seat ? tile(event.tile()) : "?"));
                case DISCARD -> {
                    if (event.riichi()) result.add(Map.of("type", "reach", "actor", actor));
                    result.add(Map.of("type", "dahai", "actor", actor, "pai", tile(event.tile()), "tsumogiri", event.tsumogiri()));
                }
                case RIICHI -> result.add(Map.of("type", "reach_accepted", "actor", actor));
                case DORA -> {
                    // Delayed kan indicators are public at the discard's reaction window.
                    // Deliver them before the discard so it remains the actionable event.
                    int index = result.size();
                    if (index > 0 && result.get(index - 1).get("type").equals("dahai")) index--;
                    result.add(index, Map.of("type", "dora", "dora_marker", tile(event.tile())));
                }
                case MELD -> {
                    var meld = event.meld();
                    var consumed = new ArrayList<>(meld.tiles());
                    switch (meld.type()) {
                        case CLOSED_KAN -> result.add(Map.of("type", "ankan", "actor", actor, "consumed", faces(consumed)));
                        case ADDED_KAN -> {
                            consumed.remove(Integer.valueOf(event.tile()));
                            result.add(Map.of("type", "kakan", "actor", actor, "pai", tile(event.tile()), "consumed", faces(consumed)));
                        }
                        default -> {
                            consumed.remove(Integer.valueOf(meld.calledTile()));
                            String type = switch (meld.type()) { case CHI -> "chi"; case PON -> "pon"; default -> "daiminkan"; };
                            result.add(Map.of("type", type, "actor", actor, "target", meld.fromSeat(),
                                "pai", tile(meld.calledTile()), "consumed", faces(consumed)));
                        }
                    }
                }
                case NUKI -> throw new IllegalArgumentException("North extraction is outside four-player mjai");
            }
        }
        return List.copyOf(result);
    }

    private static List<String> faces(List<Integer> tiles) { return tiles.stream().map(MjaiProtocol::tile).toList(); }

    static List<Map<String, Object>> result(ReplayHand hand, boolean endGame) {
        var events = new ArrayList<Map<String, Object>>();
        if (hand.wins().isEmpty()) events.add(Map.of("type", "ryukyoku", "deltas", hand.deltas()));
        else for (var win : hand.wins()) events.add(Map.of("type", "hora", "actor", win.seat(),
            "target", win.from() < 0 ? win.seat() : win.from(), "deltas", win.deltas(), "ura_markers", faces(hand.ura())));
        events.add(Map.of("type", "end_kyoku"));
        if (endGame) events.add(Map.of("type", "end_game"));
        return List.copyOf(events);
    }

    /** Match every claim against the server's legal actions, including red identity and call source. */
    static int action(TableView view, JsonObject response, boolean reach) {
        String type = response.get("type").getAsString();
        if (!type.equals("none") && !type.equals("ryukyoku")
            && (!response.has("actor") || response.get("actor").getAsInt() != view.viewerSeat()))
            throw new IllegalArgumentException("Wrong mjai actor");
        Action.Type expected = switch (type) {
            case "none" -> Action.Type.PASS;
            case "dahai" -> reach ? Action.Type.RIICHI : Action.Type.DISCARD;
            case "chi" -> Action.Type.CHI;
            case "pon" -> Action.Type.PON;
            case "daiminkan" -> Action.Type.OPEN_KAN;
            case "ankan" -> Action.Type.CLOSED_KAN;
            case "kakan" -> Action.Type.ADDED_KAN;
            case "hora" -> view.phase() == Game.Phase.TURN ? Action.Type.TSUMO : Action.Type.RON;
            case "ryukyoku" -> Action.Type.ABORT_NINE;
            default -> throw new IllegalArgumentException("Unknown mjai action: " + type);
        };
        for (int i = 0; i < view.actions().size(); i++) {
            var action = view.actions().get(i);
            if (action.type() != expected) continue;
            if (expected == Action.Type.PASS || expected == Action.Type.ABORT_NINE) return i;
            if (expected == Action.Type.RON || expected == Action.Type.TSUMO) {
                int target = expected == Action.Type.TSUMO ? view.viewerSeat() : view.focus().seat();
                if (response.has("target") && response.get("target").getAsInt() == target) return i;
                continue;
            }
            if (expected == Action.Type.DISCARD || expected == Action.Type.RIICHI || expected == Action.Type.ADDED_KAN) {
                int tile = action.tiles().get(0);
                if (!tile(tile).equals(response.get("pai").getAsString())) continue;
                if (expected != Action.Type.ADDED_KAN && response.has("tsumogiri")
                    && response.get("tsumogiri").getAsBoolean() != (tile == view.seats().get(view.viewerSeat()).drawn())) continue;
                if (expected == Action.Type.ADDED_KAN) {
                    var pon = view.seats().get(view.viewerSeat()).melds().stream()
                        .filter(meld -> meld.type() == Meld.Type.PON && meld.kind() == Tile.kind(tile)).findFirst().orElseThrow();
                    if (!consumed(response).equals(sortedFaces(pon.tiles()))) continue;
                }
                return i;
            }
            if (expected != Action.Type.CLOSED_KAN) {
                if (response.get("target").getAsInt() != view.focus().seat()
                    || !response.get("pai").getAsString().equals(tile(view.focus().tile()))) continue;
            }
            if (sortedFaces(action.tiles()).equals(consumed(response))) return i;
        }
        throw new IllegalArgumentException("Mjai response is not a legal action: " + type);
    }

    private static List<String> consumed(JsonObject response) {
        var tiles = new ArrayList<String>();
        response.getAsJsonArray("consumed").forEach(value -> tiles.add(value.getAsString()));
        Collections.sort(tiles);
        return tiles;
    }

    private static List<String> sortedFaces(List<Integer> tiles) {
        var result = new ArrayList<>(faces(tiles));
        Collections.sort(result);
        return result;
    }
}
