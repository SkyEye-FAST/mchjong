package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.MahjongBoxScreen;
import top.skyeyefast.mchjong.item.MahjongBoxMenu;
import top.skyeyefast.mchjong.item.MahjongSupplies;

/** Actual synchronized box, native language reloads, and a 320x240 logical viewport. */
final class BoxInterfaceSmoke {
    private static final String[] LANGUAGES = {"en_us", "ja_jp", "zh_cn", "zh_tw"};
    private int sample = -1, settled, scale, windowWidth, windowHeight;
    private String language;
    private CompletableFuture<Void> reload;
    private boolean printing;

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
        if (settled == 10) UiControlsSmoke.verify(client);
        var menu = (MahjongBoxMenu) client.player.containerMenu;
        var preset = top.skyeyefast.mchjong.item.TileFacePreset.values()[sample % 2];
        if (MahjongSupplies.facePreset(menu.getSlot(0).getItem()) != preset) {
            require(settled < 400, "Face printing timed out: language=" + LANGUAGES[sample]
                + ", requested=" + preset + ", received=" + MahjongSupplies.facePreset(menu.getSlot(0).getItem())
                + ", menu=" + menu.containerId + ", printing=" + printing);
            if (!printing) {
                press(client, preset.translationKey());
                require(menu.canEngrave(preset), "Preset fixture cannot be printed");
                press(client, "box.mchjong.print");
                printing = true;
                settled = 0;
            }
            return false;
        }
        if (printing) { printing = false; settled = 0; return false; }
        int boxSlots = MahjongSupplies.BOX_SLOTS;
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
            for (var preset : top.skyeyefast.mchjong.item.TileFacePreset.values()) for (int face : new int[]{0, 4, 27, 34, 38, 39, 40, 41}) {
                var data = new top.skyeyefast.mchjong.item.TileData(face, top.skyeyefast.mchjong.item.TileMaterial.BONE, face == 4);
                var stack = top.skyeyefast.mchjong.item.MahjongSupplies.tile(data, net.minecraft.world.item.DyeColor.BLUE, 1);
                stack.set(top.skyeyefast.mchjong.item.MahjongComponents.FACE_PRESET, preset);
                for (var style : top.skyeyefast.mchjong.client.TableSettings.TileLabels.values()) {
                    settings.tileLabels = style;
                    var lines = net.minecraft.client.gui.screens.Screen.getTooltipFromItem(client, stack).stream().map(Component::getString).toList();
                    require(lines.contains(data.label(style == top.skyeyefast.mchjong.client.TableSettings.TileLabels.MPSZ, preset).getString()), "Client tooltip ignores the tile-label preference or preset");
                    require(lines.getFirst().equals(Component.translatable("item.mchjong.mahjong_tile").getString()), "Flowers use a different item-name layout");
                }
            }
        } finally { settings.tileLabels = previous; }
    }

    private void select(Minecraft client, String language) {
        client.getLanguageManager().setSelected(language);
        reload = client.reloadResourcePacks();
        settled = 0;
        printing = false;
    }

    private static void press(Minecraft client, String key) {
        var button = client.screen.children().stream()
            .filter(child -> child instanceof net.minecraft.client.gui.components.AbstractButton)
            .map(child -> (net.minecraft.client.gui.components.AbstractButton) child)
            .filter(child -> child.getMessage().getString().equals(Component.translatable(key).getString()))
            .findFirst().orElseThrow();
        require(button.active, "Inactive preset control: " + key);
        button.onPress();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
