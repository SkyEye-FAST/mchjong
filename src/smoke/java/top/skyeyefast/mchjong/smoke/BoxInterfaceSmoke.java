package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.MahjongBoxScreen;
import top.skyeyefast.mchjong.item.MahjongBoxMenu;

/** Actual synchronized box, native language reloads, and a 320x240 logical viewport. */
final class BoxInterfaceSmoke {
    private static final String[] LANGUAGES = {"en_us", "ja_jp", "zh_cn", "zh_tw"};
    private int sample = -1, settled, scale, windowWidth, windowHeight;
    private String language;
    private CompletableFuture<Void> reload;

    boolean tick(Minecraft client, Path output) {
        if (sample == -1) {
            language = client.getLanguageManager().getSelected();
            scale = client.options.guiScale().get();
            windowWidth = client.getWindow().getScreenWidth();
            windowHeight = client.getWindow().getScreenHeight();
            client.options.guiScale().set(3);
            GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 960, 720);
            client.resizeDisplay();
            sample = 0;
            select(client, LANGUAGES[sample]);
            return false;
        }
        if (!reload.isDone() || client.getOverlay() != null) return false;
        reload.join();
        if (++settled < 10) return false;
        if (sample == LANGUAGES.length) return true;
        require(client.screen instanceof MahjongBoxScreen, "Language reload replaced the box screen");
        require(client.screen.width == 320 && client.screen.height == 240, "Small-box fixture is not a 320x240 logical viewport");
        UiControlsSmoke.verify(client);
        var menu = (MahjongBoxMenu) client.player.containerMenu;
        int boxSlots = top.skyeyefast.mchjong.item.MahjongSupplies.BOX_SLOTS;
        require(menu.ownerSlot() == 0 && menu.slots.size() == boxSlots + 36, "Box menu lost its synchronized layout or carrier index");
        require(!menu.slots.get(boxSlots + 27).mayPickup(client.player), "Client allows moving the open carrier");
        verifyTileLabels(client);
        var bounds = ((MahjongBoxScreen) client.screen).browserBounds();
        require(bounds.top() >= 0 && bounds.bottom() <= client.screen.height - 24, "Box overlaps recipe-browser controls");
        for (var slot : menu.slots)
            require(slot.x >= 0 && slot.y >= 0 && slot.x + 16 < bounds.width() && slot.y + 16 < bounds.height(), "Slot outside the box panel");
        Screenshot.grab(output.toFile(), "41-box-" + LANGUAGES[sample] + "-small.png", client.getMainRenderTarget(), ignored -> {});
        sample++;
        if (sample == LANGUAGES.length) {
            client.options.guiScale().set(scale);
            GLFW.glfwSetWindowSize(client.getWindow().getWindow(), windowWidth, windowHeight);
            client.resizeDisplay();
            select(client, language);
        } else select(client, LANGUAGES[sample]);
        return false;
    }

    private static void verifyTileLabels(Minecraft client) {
        var settings = top.skyeyefast.mchjong.client.TableSettings.get();
        var previous = settings.tileLabels;
        try {
            for (int face : new int[]{0, 4, 27, 34, 41}) {
                var data = new top.skyeyefast.mchjong.item.TileData(face, top.skyeyefast.mchjong.item.TileMaterial.BONE, face == 4);
                var stack = top.skyeyefast.mchjong.item.MahjongSupplies.tile(data, net.minecraft.world.item.DyeColor.BLUE, 1);
                for (var style : top.skyeyefast.mchjong.client.TableSettings.TileLabels.values()) {
                    settings.tileLabels = style;
                    var lines = net.minecraft.client.gui.screens.Screen.getTooltipFromItem(client, stack).stream().map(Component::getString).toList();
                    require(lines.contains(data.label(style == top.skyeyefast.mchjong.client.TableSettings.TileLabels.MPSZ).getString()), "Client tooltip ignores the tile-label preference");
                    require(lines.getFirst().equals(Component.translatable("item.mchjong.mahjong_tile").getString()), "Flowers use a different item-name layout");
                }
            }
        } finally { settings.tileLabels = previous; }
    }

    private void select(Minecraft client, String language) {
        client.getLanguageManager().setSelected(language);
        reload = client.reloadResourcePacks();
        settled = 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
