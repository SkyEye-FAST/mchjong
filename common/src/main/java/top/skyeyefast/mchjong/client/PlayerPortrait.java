package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import top.skyeyefast.mchjong.engine.TableView;

/** Player-list skins stay owned and cached by Minecraft; practice bots have a distinct portrait. */
final class PlayerPortrait {
    private PlayerPortrait() {}

    static int draw(GuiGraphics graphics, TableView.Seat player, int x, int y, int size) {
        if (!player.occupied()) return 0;
        if (graphics != null) {
            if (player.bot()) {
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
