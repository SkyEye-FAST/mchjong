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
    private int reagentStage;
    private CompletableFuture<Void> reagentUpdate;

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
        if (reagentStage > 0) return reagents(client, output, menu);
        var choices = top.skyeyefast.mchjong.client.TileFacePresets.choices();
        var preset = choices.get(sample % choices.size());
        if (!MahjongSupplies.facePreset(menu.getSlot(0).getItem()).equals(preset)) {
            require(settled < 400, "Face printing timed out: language=" + LANGUAGES[sample]
                + ", requested=" + preset + ", received=" + MahjongSupplies.facePreset(menu.getSlot(0).getItem())
                + ", menu=" + menu.containerId + ", printing=" + printing);
            if (!printing) {
                var selector = client.screen.children().stream()
                    .filter(child -> child instanceof net.minecraft.client.gui.components.AbstractButton)
                    .map(child -> (net.minecraft.client.gui.components.AbstractButton) child)
                    .filter(child -> child.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                        && text.getKey().equals("box.mchjong.preset_choice")).findFirst().orElseThrow();
                String requested = Component.translatable("box.mchjong.preset_choice", Component.translatable(preset.translationKey())).getString();
                for (int i = 0; i < choices.size() && !selector.getMessage().getString().equals(requested); i++) {
                    client.screen.setFocused(selector);
                    selector.setFocused(true);
                    client.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                }
                require(selector.getMessage().getString().equals(requested), "Preset selector did not cycle with keyboard");
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
        reagentStage = 1;
        reagent(client, net.minecraft.world.item.ItemStack.EMPTY);
        return false;
    }

    private boolean reagents(Minecraft client, Path output, MahjongBoxMenu menu) {
        if (!reagentUpdate.isDone() || settled < 10) return false;
        reagentUpdate.join();
        var dye = menu.getSlot(MahjongSupplies.DYE_SLOT).getItem();
        if (reagentStage == 1) {
            require(dye.isEmpty(), "Empty reagent fixture was not synchronized");
            require(!visible(client, "box.mchjong.print") && !visible(client, "box.mchjong.dye_back"), "Empty slot exposes actions");
            capture(client, output, "empty");
            reagentStage = 2;
            reagent(client, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RED_DYE, 2));
            return false;
        }
        if (reagentStage == 2) {
            require(dye.is(net.minecraft.world.item.Items.RED_DYE), "Vanilla dye fixture was not synchronized");
            require(visible(client, "box.mchjong.dye_back") && !visible(client, "box.mchjong.print"), "Wrong vanilla-dye actions");
            capture(client, output, "back");
            if (menu.canDyeBack()) press(client, "box.mchjong.dye_back");
            reagentStage = 3; settled = 0;
            return false;
        }
        require(!menu.canDyeBack(), "Back-dye action did not acknowledge matching color");
        capture(client, output, "back-applied");
        reagentStage = 0;
        reagent(client, new net.minecraft.world.item.ItemStack(top.skyeyefast.mchjong.world.MahjongContent.CREATIVE_MAHJONG_DYE));
        sample++;
        if (sample == LANGUAGES.length) {
            client.options.guiScale().set(scale);
            GLFW.glfwSetWindowSize(client.getWindow().getWindow(), windowWidth, windowHeight);
            client.resizeDisplay();
            select(client, language);
        } else select(client, LANGUAGES[sample]);
        return false;
    }

    private void reagent(Minecraft client, net.minecraft.world.item.ItemStack stack) {
        var id = client.player.getUUID();
        reagentUpdate = client.getSingleplayerServer().submit(() -> {
            var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
            require(player.containerMenu instanceof MahjongBoxMenu, "Reagent fixture lost its real menu");
            player.containerMenu.getSlot(MahjongSupplies.DYE_SLOT).set(stack);
            player.containerMenu.broadcastChanges();
        });
        settled = 0;
    }

    private static boolean visible(Minecraft client, String key) {
        return client.screen.children().stream().anyMatch(child -> child instanceof net.minecraft.client.gui.components.AbstractWidget widget
            && widget.visible && widget.getMessage().getString().equals(Component.translatable(key).getString()));
    }

    private void capture(Minecraft client, Path output, String state) {
        Screenshot.grab(output.toFile(), "41-box-" + LANGUAGES[sample] + "-" + state + ".png", client.getMainRenderTarget(), ignored -> {});
    }

    private static void verifyTileLabels(Minecraft client) {
        var settings = top.skyeyefast.mchjong.client.TableSettings.get();
        var previous = settings.tileLabels;
        try {
            for (var preset : top.skyeyefast.mchjong.client.TileFacePresets.choices()) for (int face : new int[]{0, 4, 27, 34, 38, 39, 40, 41}) {
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
        require(button.active && button.visible, "Inactive preset control: " + key);
        button.onPress();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
