package top.skyeyefast.mchjong.smoke.mixin;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.skyeyefast.mchjong.client.MahjongTableRenderer;
import top.skyeyefast.mchjong.client.McrTableScene;
import top.skyeyefast.mchjong.engine.McrView;
import top.skyeyefast.mchjong.smoke.WallSeatedSmoke;

/** An unopened wall is a render fixture, never an invalid post-deal engine view. */
@Mixin(value = MahjongTableRenderer.class, remap = false)
abstract class SmokeWallMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target =
        "Ltop/skyeyefast/mchjong/client/McrTableScene;build(Ltop/skyeyefast/mchjong/engine/McrView;)Ljava/util/List;"))
    private List<McrTableScene.Piece> unopenedWall(McrView view) {
        return WallSeatedSmoke.fullMcrWall ? McrTableScene.fullWall() : McrTableScene.build(view);
    }
}
