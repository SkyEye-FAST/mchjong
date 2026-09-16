package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.engine.Discard;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.TableGeometry;

/** One geometric description drives both the 3D meshes and the interaction anchors. */
public final class TableScene {
    public enum Area { HAND, WALL, RIVER, MELD, NORTH }
    public record Piece(int tile, int seat, Area area, int index, Vec3 position, float yaw, boolean flat, boolean back) {}
    private TableScene() {}

    private static Piece piece(int tile, int seat, Area area, int index, double x, double y, double z,
            float additionalYaw, boolean flat, boolean back) {
        return new Piece(tile, seat, area, index, TableGeometry.orient(x, y, z, seat),
            seat * 90 + additionalYaw, flat, back);
    }

    public static List<Piece> build(TableView view) {
        List<Piece> result = new ArrayList<>(200);
        double top = TableGeometry.FELT_Y;
        for (int seat = 0; seat < view.seats().size(); seat++) {
            TableView.Seat player = view.seats().get(seat);
            double left = player.melds().isEmpty() ? -(player.hand().size() - 1) * 0.055 : -1.07;
            for (int i = 0; i < player.hand().size(); i++) {
                boolean drawn = player.drawn() != Tile.ABSENT && i == player.hand().size() - 1;
                boolean declaration = view.focus() != null && view.focus().declaration()
                    && view.focus().seat() == seat && view.focus().index() == i;
                boolean flat = player.exposed() || declaration;
                result.add(piece(declaration ? view.focus().tile() : player.hand().get(i), seat, Area.HAND, i,
                    left + i * 0.11 + (drawn ? 0.04 : 0), top + (flat ? 0.036 : 0.081), 1.24, 0, flat, false));
            }
            for (int i = 0; i < player.river().size(); i++) {
                Discard discard = player.river().get(i);
                if (discard.called()) continue;
                double shifted = 0;
                for (int preceding = i / 6 * 6; preceding < i; preceding++)
                    if (player.river().get(preceding).riichi()) shifted += 0.056;
                result.add(piece(discard.tile(), seat, Area.RIVER, i, (i % 6 - 2.5) * 0.117 + shifted + (discard.riichi() ? 0.028 : 0),
                    top + 0.036, 0.31 + i / 6 * 0.175, discard.riichi() ? 90 : 0, true, false));
            }
            double meldRight = 1.16;
            for (Meld meld : player.melds()) {
                List<Integer> tiles = new ArrayList<>(meld.tiles());
                Integer addedTile = meld.type() == Meld.Type.ADDED_KAN ? tiles.removeLast() : null;
                tiles.remove(Integer.valueOf(meld.calledTile()));
                tiles.sort(Integer::compareTo);
                if (!meld.closed()) {
                    int relative = Math.floorMod(meld.fromSeat() - seat, 4);
                    int calledIndex = relative == 3 ? 0 : relative == 2 ? 1 : 2;
                    tiles.add(Math.min(calledIndex, tiles.size()), meld.calledTile());
                }
                int baseCount = tiles.size();
                double[] centers = new double[baseCount];
                double offset = 0;
                for (int i = 0; i < baseCount; i++) {
                    double width = !meld.closed() && tiles.get(i) == meld.calledTile() ? 0.16 : 0.104;
                    centers[i] = offset + width / 2;
                    offset += width + 0.008;
                }
                double start = meldRight - offset;
                if (addedTile != null) tiles.add(addedTile);
                for (int i = 0; i < tiles.size(); i++) {
                    boolean stacked = meld.type() == Meld.Type.ADDED_KAN && i == tiles.size() - 1;
                    int calledIndex = Math.max(0, tiles.indexOf(meld.calledTile()));
                    boolean sideways = !meld.closed() && (tiles.get(i) == meld.calledTile() || stacked);
                    result.add(piece(tiles.get(i), seat, Area.MELD, i, start + centers[stacked ? calledIndex : i],
                        top + 0.036 + (stacked ? 0.072 : 0), 1.23, sideways ? 90 : 0, true,
                        meld.closed() && (i == 0 || i == tiles.size() - 1)));
                }
                meldRight = start - 0.18;
            }
            for (int i = 0; i < player.norths().size(); i++)
                result.add(piece(player.norths().get(i), seat, Area.NORTH, i, -1.17 + (i % 2) * 0.112,
                    top + 0.036, 0.68 - i / 2 * 0.18, 0, true, false));
        }
        int size = view.wall().size();
        if (size > 0) {
            int stacksPerSide = size / (view.rules().sanma() ? 6 : 8);
            for (int i = 0; i < size; i++) {
                int tile = view.wall().get(i);
                if (tile == Tile.ABSENT) continue;
                int display = (i + view.wallBreak()) % size;
                int seat = display / 2 / stacksPerSide;
                int column = display / 2 % stacksPerSide;
                int mate = view.wall().get(i ^ 1);
                boolean upper = mate != Tile.ABSENT && (tile >= 0 || mate < 0 && i % 2 == 0);
                result.add(piece(tile, seat, Area.WALL, i, (column - (stacksPerSide - 1) / 2.0) * 0.108 - 0.08,
                    top + 0.036 + (upper ? 0.072 : 0), 1.00, 0, true, tile < 0));
            }
        }
        return List.copyOf(result);
    }
}
