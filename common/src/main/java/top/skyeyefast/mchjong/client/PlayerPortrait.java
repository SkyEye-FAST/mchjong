package top.skyeyefast.mchjong.client;

import top.skyeyefast.mchjong.platform.ResourceIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.TableView;

/** Player-list skins stay owned and cached by Minecraft; practice bots have a distinct portrait. */
final class PlayerPortrait {
    private static final ResourceLocation MAID_ICON = ResourceIds.of("xaerominimap", "entity/icon/sprite/tlm_maid.png");
    private PlayerPortrait() {}

    static int draw(GuiGraphics graphics, TableView.Seat player, int x, int y, int size) {
        if (!player.occupied()) return 0;
        if (graphics != null) {
            if (player.entityBot()) {
                graphics.blit(MAID_ICON, x, y, size, size, 16, 16, 32, 32, 64, 64);
            } else if (player.bot()) {
                graphics.pose().pushPose();
                graphics.pose().translate(x, y, 0);
                graphics.pose().scale(size / 8f, size / 8f, 1);
                graphics.fill(1, 2, 7, 8, MahjongUi.SURFACE);
                graphics.renderOutline(1, 2, 6, 6, MahjongUi.EDGE);
                graphics.fill(3, 0, 5, 2, MahjongUi.ACCENT);
                graphics.fill(2, 4, 3, 5, MahjongUi.TEXT);
                graphics.fill(5, 4, 6, 5, MahjongUi.TEXT);
                graphics.fill(3, 6, 5, 7, MahjongUi.ACCENT);
                graphics.pose().popPose();
            } else {
                var connection = Minecraft.getInstance().getConnection();
                var info = connection == null ? null : connection.getPlayerInfo(player.name());
                PlayerFaceRenderer.draw(graphics, info == null ? DefaultPlayerSkin.getDefaultSkin() : info.getSkinLocation(), x, y, size);
            }
        }
        return size + 4;
    }
}
