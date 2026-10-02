package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.RiichiView;

/** Player-list skins stay owned and cached by Minecraft; practice bots have a distinct portrait. */
final class PlayerPortrait {
    private static final ResourceLocation MAID_ICON = ResourceLocation.fromNamespaceAndPath("xaerominimap", "entity/icon/sprite/tlm_maid.png");
    private PlayerPortrait() {}

    static int draw(GuiGraphics graphics, RiichiView.Seat player, int x, int y, int size) {
        if (!player.occupied()) return 0;
        return draw(graphics, player.entityBot(), player.bot(), player.name(), x, y, size);
    }

    static int draw(GuiGraphics graphics, top.skyeyefast.mchjong.engine.TableParticipant player, int x, int y, int size) {
        if (player.id() == null) return 0;
        return draw(graphics, player.entityBot(), player.bot(), player.name(), x, y, size);
    }

    static int draw(GuiGraphics graphics, boolean entityBot, boolean bot, String name, int x, int y, int size) {
        if (graphics != null) {
            if (entityBot) {
                graphics.blit(MAID_ICON, x, y, size, size, 16, 16, 32, 32, 64, 64);
            } else if (bot) {
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
                var info = connection == null ? null : connection.getPlayerInfo(name);
                PlayerFaceRenderer.draw(graphics, info == null ? DefaultPlayerSkin.getDefaultTexture() : info.getSkin().texture(), x, y, size);
            }
        }
        return size + 4;
    }
}
