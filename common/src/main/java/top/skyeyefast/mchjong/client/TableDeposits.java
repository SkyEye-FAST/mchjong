package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import top.skyeyefast.mchjong.config.BuiltinPresets;
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
                              PoseStack pose, MultiBufferSource buffers, net.minecraft.client.renderer.SubmitNodeCollector collector, int light) {
        for (var stick : sticks(view)) {
            double progress = animated && stick.declared() ? animation.riichiProgress(stick.seat(), now) : 1;
            var id = stick.seat() == view.viewerSeat() ? TableSettings.get().riichiStickPreset
                : RiichiStickPresets.forPlayer(view.seats().get(stick.seat()).name());
            boolean nativeStick = BuiltinPresets.STICKS.contains(id);
            boolean bamboo = nativeStick && id.getPath().equals("bamboo");
            var preset = RiichiStickPresets.definition(id);
            float length = preset == null ? 11.2f : preset.length();
            float width = preset == null ? .96f : preset.width();
            float height = preset == null ? .4f : preset.height();
            float renderedHeight = nativeStick ? (bamboo ? .025f : .02f) : HEIGHT * height / .4f;
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(stick.seat() * 90));
            pose.translate(0, TableGeometry.FELT_Y + (automatic ? .044 : .002) + stick.layer() * (renderedHeight + .002)
                + Math.sin(progress * Math.PI) * .09, TableScene.HAND_Z + (LANE_Z - TableScene.HAND_Z) * progress);
            if (nativeStick) {
                // Vanilla block models stand upright. Lay their long axis along the deposit lane.
                float nativeLength = bamboo ? HALF_LENGTH * 2 : HALF_LENGTH * 1.45f;
                float nativeCross = bamboo ? .12f : .08f;
                pose.translate(nativeLength / 2, 0, -nativeCross / 2);
                pose.mulPose(Axis.ZP.rotationDegrees(90));
                pose.scale(nativeCross, nativeLength, nativeCross);
                var block = switch (id.getPath()) {
                    case "bamboo" -> Blocks.BAMBOO;
                    case "lightning_rod" -> Blocks.LIGHTNING_ROD;
                    case "end_rod" -> Blocks.END_ROD;
                    default -> throw new IllegalStateException(id.toString());
                };
                var renderState = new net.minecraft.client.renderer.block.BlockModelRenderState();
                new net.minecraft.client.renderer.block.BlockModelResolver(Minecraft.getInstance().getModelManager())
                    .update(renderState, block.defaultBlockState(),
                    net.minecraft.client.renderer.block.model.BlockDisplayContext.create());
                renderState.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            } else {
                pose.scale(HALF_LENGTH / FurnitureMesh.STICK_HALF_LENGTH * length / 11.2f,
                    renderedHeight / FurnitureMesh.STICK_HEIGHT,
                    HALF_WIDTH / FurnitureMesh.STICK_HALF_WIDTH * width / .96f);
                if (preset == null) FurnitureMesh.stick(pose, buffers, light, 1000);
                else FurnitureMesh.customStick(pose, buffers, light, preset.texture());
            }
            pose.popPose();
        }
    }
}
