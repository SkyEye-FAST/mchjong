package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import top.skyeyefast.mchjong.engine.RiichiView;

/** Player-list skins stay owned and cached by Minecraft; practice bots have a distinct portrait. */
final class PlayerPortrait {
    private static final Identifier MAID_ICON = Identifier.fromNamespaceAndPath("xaerominimap", "entity/icon/sprite/tlm_maid.png");
    private PlayerPortrait() {}

    static int draw(GuiGraphicsExtractor graphics, RiichiView.Seat player, int x, int y, int size) {
        if (!player.occupied()) return 0;
        return draw(graphics, player.entityBot(), player.bot(), player.name(), x, y, size);
    }

    static int draw(GuiGraphicsExtractor graphics, top.skyeyefast.mchjong.engine.TableParticipant player, int x, int y, int size) {
        if (player.id() == null) return 0;
        return draw(graphics, player.entityBot(), player.bot(), player.name(), x, y, size);
    }

    private static int draw(GuiGraphicsExtractor graphics, boolean entityBot, boolean bot, String name, int x, int y, int size) {
        if (graphics != null) {
            if (entityBot) {
                graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, MAID_ICON, x, y, 16, 16, size, size, 32, 32, 64, 64);
            } else if (bot) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(x, y);
                graphics.pose().scale(size / 8f, size / 8f);
                graphics.fill(1, 2, 7, 8, MahjongUi.SURFACE);
                graphics.outline(1, 2, 6, 6, MahjongUi.EDGE);
                graphics.fill(3, 0, 5, 2, MahjongUi.ACCENT);
                graphics.fill(2, 4, 3, 5, MahjongUi.TEXT);
                graphics.fill(5, 4, 6, 5, MahjongUi.TEXT);
                graphics.fill(3, 6, 5, 7, MahjongUi.ACCENT);
                graphics.pose().popMatrix();
            } else {
                var connection = Minecraft.getInstance().getConnection();
                var info = connection == null ? null : connection.getPlayerInfo(name);
                PlayerFaceExtractor.extractRenderState(graphics, info == null ? DefaultPlayerSkin.getDefaultSkin() : info.getSkin(), x, y, size);
            }
        }
        return size + 4;
    }
}
