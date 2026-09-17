package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Physical deposits share four central lanes, above the machine panel or directly on the felt. */
public final class TableDeposits {
    public static final float LANE_Z = .136f;
    public static final float HALF_LENGTH = .11f;
    public static final float HALF_WIDTH = .011f;
    public static final float HEIGHT = .012f;
    public record Stick(int seat, int layer, boolean declared) {}
    private TableDeposits() {}

    public static List<Stick> sticks(TableView view) {
        int count = view.riichiSticks();
        int declarations = (int) view.seats().stream().filter(TableView.Seat::riichi).count();
        int carried = Math.max(0, count - declarations);
        int[] layers = new int[view.seats().size()];
        var result = new ArrayList<Stick>();
        for (int i = 0; i < carried; i++) {
            int seat = i % layers.length;
            result.add(new Stick(seat, layers[seat]++, false));
        }
        for (int seat = 0; seat < layers.length && result.size() < count; seat++)
            if (view.seats().get(seat).riichi()) result.add(new Stick(seat, layers[seat]++, true));
        return List.copyOf(result);
    }

    public static void render(TableView view, boolean automatic, TableAnimation animation, boolean animated, long now,
                              PoseStack pose, MultiBufferSource buffers, int light) {
        var vertices = buffers.getBuffer(TileRenderTypes.FACES);
        for (var stick : sticks(view)) {
            double progress = animated && stick.declared() ? animation.riichiProgress(stick.seat(), now) : 1;
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(stick.seat() * 90));
            pose.translate(0, TableGeometry.FELT_Y + (automatic ? .044 : .002) + stick.layer() * (HEIGHT + .002)
                + Math.sin(progress * Math.PI) * .09, TableScene.HAND_Z + (LANE_Z - TableScene.HAND_Z) * progress);
            TileMesh.box(pose, vertices, -HALF_LENGTH, 0, -HALF_WIDTH, HALF_LENGTH, HEIGHT, HALF_WIDTH, 0xfff1ead9, light);
            TileMesh.box(pose, vertices, -.007f, HEIGHT, -.007f, .007f, HEIGHT + .001f, .007f, 0xffbb3737, light);
            pose.popPose();
        }
    }
}
