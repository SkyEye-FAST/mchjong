package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;

/** Public tile presentation only; this never creates or changes an actionable server choice. */
public record ActionPreview(List<Integer> tiles, Meld meld) {
    public ActionPreview { tiles = List.copyOf(tiles); }

    public static ActionPreview of(TableView view, Action action) {
        var tiles = new ArrayList<Integer>();
        Meld meld = null;
        switch (action.type()) {
            case CHI, PON, OPEN_KAN -> {
                tiles.addAll(action.tiles());
                if (view.focus() != null) {
                    tiles.add(view.focus().tile());
                    meld = new Meld(Meld.Type.valueOf(action.type().name()), tiles, view.focus().seat(), view.focus().tile());
                }
            }
            case CLOSED_KAN -> {
                tiles.addAll(action.tiles());
                meld = new Meld(Meld.Type.CLOSED_KAN, tiles, view.viewerSeat(), Tile.ABSENT);
            }
            case ADDED_KAN -> {
                if (view.viewerSeat() >= 0) {
                    for (Meld pon : view.seats().get(view.viewerSeat()).melds()) {
                        if (pon.type() == Meld.Type.PON && pon.kind() == Tile.kind(action.tiles().getFirst())) {
                            tiles.addAll(pon.tiles());
                            tiles.addAll(action.tiles());
                            meld = new Meld(Meld.Type.ADDED_KAN, tiles, pon.fromSeat(), pon.calledTile());
                            break;
                        }
                    }
                }
            }
            case DISCARD, RIICHI, NUKI, RON, TSUMO -> tiles.addAll(action.tiles());
            default -> { }
        }
        return new ActionPreview(tiles, meld);
    }

    public static boolean consumesHand(Action action) {
        return switch (action.type()) {
            case CHI, PON, OPEN_KAN, CLOSED_KAN, ADDED_KAN, RIICHI, NUKI -> true;
            default -> false;
        };
    }

    public static boolean hasGuide(Action action) {
        return switch (action.type()) {
            case CHI, PON, OPEN_KAN, CLOSED_KAN, ADDED_KAN, RIICHI, NUKI, RON, TSUMO -> true;
            default -> false;
        };
    }
}
