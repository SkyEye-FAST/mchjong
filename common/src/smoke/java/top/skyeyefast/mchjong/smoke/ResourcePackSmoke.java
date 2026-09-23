package top.skyeyefast.mchjong.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.Identifier;
import top.skyeyefast.mchjong.client.TileFacePresets;
import top.skyeyefast.mchjong.client.TileMesh;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.MahjongBoxMenu;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Real pack selection, tile-face discovery, cosmetic-ID packets and removal/reload. */
final class ResourcePackSmoke {
    private static final TileFacePreset CUSTOM = new TileFacePreset(Identifier.parse("smoke:custom"));
    private final DepositVisualSmoke baseline = new DepositVisualSmoke(), customized = new DepositVisualSmoke(true);
    private CompletableFuture<Void> pending;
    private List<String> selected;
    private int stage, ticks;

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) throws Exception {
        if (++ticks > 1800) throw new IllegalStateException("Resource pack smoke timed out at " + stage);
        if (stage == 0) {
            Files.createDirectories(output.resolve("resource-default"));
            if (!baseline.tick(client, table, output.resolve("resource-default"))) return false;
            selected = List.copyOf(client.getResourcePackRepository().getSelectedIds());
            Path pack = client.gameDirectory.toPath().resolve("resourcepacks/mchjong-smoke-custom");
            write(pack, "pack.mcmeta", "{\"pack\":{\"pack_format\":34,\"description\":\"MChjong resource smoke\"}}");
            String definition = "{\"atlas\":\"mchjong:textures/tiles.png\",\"glyphs\":\"mchjong:textures/tile_glyphs.png\"}";
            write(pack, "assets/smoke/tile_face_presets/custom.json", definition);
            for (String language : List.of("en_us", "ja_jp", "zh_cn", "zh_tw"))
                write(pack, "assets/smoke/lang/" + language + ".json", "{\"preset.smoke.custom\":\"Resource Pack Test\"}");
            write(pack, "assets/mchjong/tile_face_presets/kanto.json", definition);
            backPattern(pack.resolve("assets/mchjong/textures/tile/back.png"));
            pattern(pack.resolve("assets/mchjong/textures/furniture/cloth_pattern.png"), 256, 256);
            pattern(pack.resolve("assets/mchjong/textures/item/riichi_stick.png"), 384, 32);
            client.getResourcePackRepository().reload();
            var packs = new java.util.ArrayList<>(selected);
            packs.add("file/mchjong-smoke-custom");
            client.getResourcePackRepository().setSelected(packs);
            pending = client.reloadResourcePacks();
            stage = 1; ticks = 0;
        } else if (stage == 1 && ready(client)) {
            require(TileFacePresets.choices().contains(CUSTOM), "Custom preset was not discovered");
            require(TileMesh.atlas(TileFacePreset.KANTO).equals(TileMesh.ATLAS), "Built-in preset override was ignored");
            var id = client.player.getUUID();
            pending = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                var box = MahjongSupplies.completeBox(top.skyeyefast.mchjong.item.TileMaterial.BONE);
                var items = MahjongSupplies.contents(box);
                items.set(MahjongSupplies.DYE_SLOT, new net.minecraft.world.item.ItemStack(MahjongContent.CREATIVE_MAHJONG_DYE));
                MahjongSupplies.setContents(box, items);
                player.getInventory().setItem(0, box);
                player.getInventory().setSelectedSlot(0);
                box.getItem().use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
            });
            stage = 2; ticks = 0;
        } else if (stage == 2 && pending.isDone() && client.screen instanceof top.skyeyefast.mchjong.client.MahjongBoxScreen && ticks > 20) {
            pending.join();
            var selector = button(client, "box.mchjong.preset_choice");
            String label = net.minecraft.network.chat.Component.translatable("box.mchjong.preset_choice",
                net.minecraft.network.chat.Component.translatable(CUSTOM.translationKey())).getString();
            for (int i = 0; i < TileFacePresets.choices().size() && !selector.getMessage().getString().equals(label); i++) selector.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
            require(selector.getMessage().getString().equals(label), "New preset missing from selector");
            button(client, "box.mchjong.print").onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
            stage = 3; ticks = 0;
        } else if (stage == 3 && ticks > 20) {
            var menu = (MahjongBoxMenu) client.player.containerMenu;
            require(MahjongSupplies.facePreset(menu.getSlot(0).getItem()).equals(CUSTOM), "Custom preset packet did not print");
            Screenshot.grab(output.toFile(), "60-resource-custom-box.png", client.getMainRenderTarget(), 1, ignored -> {});
            client.screen.onClose();
            stage = 4; ticks = 0;
        } else if (stage == 4) {
            Files.createDirectories(output.resolve("resource-custom"));
            if (!customized.tick(client, table, output.resolve("resource-custom"))) return false;
            client.getWindow().setWindowed(1280, 800);
            client.options.guiScale().set(2);
            client.resizeGui();
            var screen = new top.skyeyefast.mchjong.client.TableScreen(table.getBlockPos());
            client.setScreen(screen);
            screen.resetView();
            stage = 8; ticks = 0;
        } else if (stage == 8 && ticks > 20) {
            Screenshot.grab(output.toFile(), "61-resource-custom-wall.png", client.getMainRenderTarget(), 1, ignored -> {});
            var screen = (top.skyeyefast.mchjong.client.TableScreen) client.screen;
            screen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0));
            stage = 6; ticks = 0;
        } else if (stage == 6 && ticks > 20) {
            require(client.screen instanceof top.skyeyefast.mchjong.client.TableScreen screen && screen.immersive(), "Resource fixture did not enter immersive view");
            Screenshot.grab(output.toFile(), "61-resource-custom-immersive.png", client.getMainRenderTarget(), 1, ignored -> {});
            client.getWindow().setWindowed(640, 480);
            client.resizeGui();
            stage = 7; ticks = 0;
        } else if (stage == 7 && ticks > 20) {
            Screenshot.grab(output.toFile(), "62-resource-custom-immersive-small.png", client.getMainRenderTarget(), 1, ignored -> {});
            client.screen.onClose();
            client.getResourcePackRepository().setSelected(selected);
            pending = client.reloadResourcePacks();
            stage = 5; ticks = 0;
        } else if (stage == 5 && ready(client)) {
            require(!TileFacePresets.choices().contains(CUSTOM), "Removed pack left a stale preset");
            require(!TileMesh.atlas(TileFacePreset.KANTO).equals(TileMesh.ATLAS), "Removed pack left a stale override");
            return true;
        }
        return false;
    }

    private boolean ready(Minecraft client) {
        if (!pending.isDone() || client.getOverlay() != null || ticks < 20) return false;
        pending.join();
        return true;
    }
    private static net.minecraft.client.gui.components.Button button(Minecraft client, String key) {
        return client.screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Button)
            .map(child -> (net.minecraft.client.gui.components.Button) child)
            .filter(button -> button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && text.getKey().equals(key)).findFirst().orElseThrow();
    }
    private static void write(Path root, String name, String content) throws java.io.IOException {
        Path target = root.resolve(name);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
    private static void pattern(Path path, int width, int height) throws java.io.IOException {
        Files.createDirectories(path.getParent());
        try (var image = new NativeImage(width, height, false)) {
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                image.setPixel(x, y, Math.abs(x - width / 2) < width / 12 || Math.abs(y - height / 2) < height / 12 ? 0xff20e040 : 0);
            image.writeToFile(path);
        }
    }
    private static void backPattern(Path path) throws java.io.IOException {
        Files.createDirectories(path.getParent());
        try (var image = new NativeImage(256, 384, false)) {
            // Contrasting top and bottom halves expose a rotated wall back in the world capture.
            for (int y = 0; y < 384; y++) for (int x = 0; x < 256; x++)
                image.setPixel(x, y, y < 192 ? 0xff2040e0 : 0xffe04020);
            image.writeToFile(path);
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
