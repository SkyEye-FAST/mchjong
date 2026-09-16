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
    public static final float TILE_SCALE = 0.82f;
    public static final double HAND_Z = 1.25;
    public static final double HAND_STEP = 0.1;
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
            // Concealed tiles occupy the left rail after calling; melds pack from the right.
            // Four kans and the two remaining hand tiles fit without crossing either corner.
            double left = player.melds().isEmpty() ? -(player.hand().size() - 1) * HAND_STEP / 2 : -1.12;
            for (int i = 0; i < player.hand().size(); i++) {
                boolean drawn = player.drawn() != Tile.ABSENT && i == player.hand().size() - 1;
                boolean declaration = view.focus() != null && view.focus().declaration()
                    && view.focus().seat() == seat && view.focus().index() == i;
                boolean flat = player.exposed() || declaration;
                result.add(piece(declaration ? view.focus().tile() : player.hand().get(i), seat, Area.HAND, i,
                    left + i * HAND_STEP + (drawn ? 0.035 : 0), top + (flat ? 0.036 : 0.081) * TILE_SCALE, HAND_Z, 0, flat, false));
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
            double meldRight = 1.12;
            for (Meld meld : player.melds()) {
                MeldLayout layout = MeldLayout.of(meld, seat);
                double start = meldRight - layout.width() * TILE_SCALE;
                for (int i = 0; i < layout.parts().size(); i++) {
                    var part = layout.parts().get(i);
                    result.add(piece(part.tile(), seat, Area.MELD, i, start + part.x() * TILE_SCALE,
                        top + (0.036 + (part.stacked() ? 0.072 : 0)) * TILE_SCALE, HAND_Z - 0.015,
                        part.sideways() ? 90 : 0, true, part.back()));
                }
                meldRight = start - 0.035;
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
