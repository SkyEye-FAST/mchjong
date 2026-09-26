package top.skyeyefast.mchjong.compat.patchouli;

import top.skyeyefast.mchjong.world.MahjongContent;
import vazkii.patchouli.api.PatchouliAPI;

/** Referenced by client entry points only after checking Patchouli's presence. */
public final class PatchouliBook {
    private PatchouliBook() {}

    public static void open() {
        PatchouliAPI.get().openBookGUI(MahjongContent.id("guide"));
    }
}
