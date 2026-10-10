package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.RiichiView;

/** Player-list skins stay owned and cached by Minecraft; practice bots have a distinct portrait. */
final class PlayerPortrait {
    private static final ResourceLocation MAID_ICON = top.skyeyefast.mchjong.platform.ResourceIds.of("xaerominimap", "entity/icon/sprite/tlm_maid.png");
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
                int pixel = Math.max(1, size / 8);
                int top = y + size / 4;
                int eye = size / 4;
                int middle = x + (size - 2 * pixel) / 2;
                graphics.fill(x + pixel, top, x + size - pixel, y + size, MahjongUi.EDGE);
                graphics.fill(x + 2 * pixel, top + pixel, x + size - 2 * pixel, y + size - pixel, MahjongUi.SURFACE);
                graphics.fill(middle, y, middle + 2 * pixel, top, MahjongUi.ACCENT);
                graphics.fill(x + eye, y + size / 2, x + eye + pixel, y + size / 2 + pixel, MahjongUi.TEXT);
                graphics.fill(x + size - eye - pixel, y + size / 2, x + size - eye, y + size / 2 + pixel, MahjongUi.TEXT);
                graphics.fill(middle, y + size * 3 / 4, middle + 2 * pixel, y + size * 3 / 4 + pixel, MahjongUi.ACCENT);
            } else {
                var connection = Minecraft.getInstance().getConnection();
                var info = connection == null ? null : connection.getPlayerInfo(name);
                PlayerFaceRenderer.draw(graphics, info == null ? DefaultPlayerSkin.getDefaultSkin() : info.getSkinLocation(), x, y, size);
            }
        }
        return size + 4;
    }
}
