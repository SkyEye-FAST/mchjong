package top.skyeyefast.mchjong.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.client.TileFacePresets;
import top.skyeyefast.mchjong.client.TileBackPresets;
import top.skyeyefast.mchjong.client.TileMesh;
import top.skyeyefast.mchjong.client.RiichiStickModel;
import top.skyeyefast.mchjong.client.RiichiStickPresets;
import top.skyeyefast.mchjong.client.VoicePresets;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.MahjongBoxMenu;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Real pack selection, native model baking, cosmetic-ID packets and removal/reload. */
final class ResourcePackSmoke {
    private static final TileFacePreset CUSTOM = new TileFacePreset(ResourceLocation.parse("smoke:custom"));
    private static final TileFacePreset SERVER = new TileFacePreset(ResourceLocation.parse("smoke:server"));
    private final DepositVisualSmoke baseline = new DepositVisualSmoke(), customized = new DepositVisualSmoke(true);
    private CompletableFuture<Void> pending;
    private CompletableFuture<?> serverSync;
    private CompletableFuture<?> voiceDecode;
    private Path localArchive;
    private Path localBackArchive;
    private Path localStickArchive;
    private Path localVoiceArchive;
    private List<String> selected;
    private int stage, ticks;

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) throws Exception {
        if (++ticks > 1800) throw new IllegalStateException("Resource pack smoke timed out at " + stage);
        if (stage == 1 && serverSync.isCompletedExceptionally()) serverSync.join();
        if (stage == 0) {
            Files.createDirectories(output.resolve("resource-default"));
            if (!baseline.tick(client, table, output.resolve("resource-default"))) return false;
            selected = List.copyOf(client.getResourcePackRepository().getSelectedIds());
            Path pack = client.gameDirectory.toPath().resolve("resourcepacks/mchjong-smoke-custom");
            write(pack, "pack.mcmeta", "{\"pack\":{\"pack_format\":34,\"description\":\"MChjong resource smoke\"}}");
            String definition = "{\"atlas\":\"mchjong:textures/tiles.png\",\"glyphs\":\"mchjong:textures/tile_glyphs.png\"}";
            Path images = output.resolve("preset-tiles");
            for (String key : top.skyeyefast.mchjong.config.PresetArchives.TILE_KEYS)
                tileImage(images.resolve(key + ".png"), key);
            localArchive = client.gameDirectory.toPath().resolve("config/mchjong/presets/faces/local.zip");
            archive(localArchive, "custom", "Local Test", images);
            Path serverConfig = output.resolve("server-config");
            archive(serverConfig.resolve("mchjong/server-presets/faces/server.zip"), "server", "Server Test", images);
            Path backImage = output.resolve("back.png");
            backPattern(backImage);
            localBackArchive = client.gameDirectory.toPath().resolve("config/mchjong/presets/backs/local.zip");
            backArchive(localBackArchive, "custom_back", "Local Back", backImage);
            backArchive(serverConfig.resolve("mchjong/server-presets/backs/server.zip"), "server_back", "Server Back", backImage);
            Path stickImage = output.resolve("stick.png");
            pattern(stickImage, 384, 32);
            localStickArchive = client.gameDirectory.toPath().resolve("config/mchjong/presets/sticks/local.zip");
            stickArchive(localStickArchive, "custom_stick", "Local Stick", stickImage);
            stickArchive(serverConfig.resolve("mchjong/server-presets/sticks/server.zip"), "server_stick", "Server Stick", stickImage);
            byte[] recording;
            try (var sound = client.getResourceManager().open(ResourceLocation.parse("minecraft:sounds/random/click.ogg"))) {
                recording = sound.readAllBytes();
            }
            localVoiceArchive = client.gameDirectory.toPath().resolve("config/mchjong/presets/voices/local.zip");
            voiceArchive(localVoiceArchive, "custom_voice", "Local Voice", recording);
            voiceArchive(serverConfig.resolve("mchjong/server-presets/voices/server.zip"), "server_voice", "Server Voice", recording);
            serverSync = client.getSingleplayerServer().submit(() -> {
                top.skyeyefast.mchjong.config.ServerPresets.load(serverConfig);
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                top.skyeyefast.mchjong.config.ServerPresets.send(player);
            });
            write(pack, "assets/mchjong/tile_face_presets/kanto.json", definition);
            backPattern(pack.resolve("assets/mchjong/textures/tile/back.png"));
            pattern(pack.resolve("assets/mchjong/textures/furniture/cloth_pattern.png"), 256, 256);
            pattern(pack.resolve("assets/mchjong/textures/item/riichi_stick.png"), 384, 32);
            try (var reader = client.getResourceManager().openAsReader(MahjongContent.id("models/item/riichi_stick.json"))) {
                var model = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonArray("to").set(1, new com.google.gson.JsonPrimitive(1.2));
                write(pack, "assets/mchjong/models/item/riichi_stick.json", model.toString());
            }
            client.getResourcePackRepository().reload();
            var packs = new java.util.ArrayList<>(selected);
            packs.add("file/mchjong-smoke-custom");
            client.getResourcePackRepository().setSelected(packs);
            pending = client.reloadResourcePacks();
            stage = 1; ticks = 0;
        } else if (stage == 1 && ready(client) && serverSync.isDone() && TileFacePresets.choices().contains(SERVER)
            && TileBackPresets.choices().contains(ResourceLocation.parse("smoke:server_back"))
            && RiichiStickPresets.choices().contains(ResourceLocation.parse("smoke:server_stick"))
            && VoicePresets.choices().contains(ResourceLocation.parse("smoke:server_voice"))) {
            serverSync.join();
            require(TileFacePresets.choices().contains(CUSTOM), "Custom preset was not discovered");
            require(TileBackPresets.choices().contains(ResourceLocation.parse("smoke:custom_back")), "Local back was not discovered");
            require(RiichiStickPresets.choices().contains(ResourceLocation.parse("smoke:custom_stick")), "Local stick was not discovered");
            for (var id : top.skyeyefast.mchjong.config.BuiltinPresets.BACKS) {
                require(TileBackPresets.choices().contains(id), "Built-in back was not listed: " + id);
                require(client.getResourceManager().getResource(TileBackPresets.texture(id)).isPresent(),
                    "Built-in back artwork was missing: " + id);
            }
            for (var id : top.skyeyefast.mchjong.config.BuiltinPresets.STICKS) {
                require(RiichiStickPresets.choices().contains(id), "Built-in stick was not listed: " + id);
            }
            require(VoicePresets.choices().contains(ResourceLocation.parse("smoke:custom_voice")), "Local voice was not discovered");
            require(RiichiStickPresets.definition(ResourceLocation.parse("smoke:server_stick")).length() == 12,
                "Server stick model was not delivered");
            require(!TileBackPresets.texture(ResourceLocation.parse("smoke:server_back")).equals(TileMesh.BACK),
                "Server back artwork was not delivered");
            require(TileMesh.atlas(SERVER).getPath().contains("server_faces"), "Server ZIP artwork was not delivered");
            var worldFaces = (net.minecraft.client.renderer.texture.DynamicTexture) client.getTextureManager()
                .getTexture(TileMesh.glyphs(SERVER));
            require(worldFaces.getPixels() != null && (worldFaces.getPixels().getPixelRGBA(0, 0) >>> 24) == 255,
                "Server face texture lost its opaque white backing");
            require(TileMesh.atlas(TileFacePreset.KANTO).equals(TileMesh.ATLAS), "Built-in preset override was ignored");
            float maxY = 0;
            for (var quad : RiichiStickModel.baked().getQuads(null, null, net.minecraft.util.RandomSource.create(0))) {
                int[] vertices = quad.getVertices();
                for (int i = 1; i < vertices.length; i += 8) maxY = Math.max(maxY, Float.intBitsToFloat(vertices[i]));
            }
            require(maxY > .07f, "Native riichi model override was not baked");
            client.getConnection().send(top.skyeyefast.mchjong.network.PayloadPackets.serverbound(
                new top.skyeyefast.mchjong.network.StickChoicePayload(ResourceLocation.parse("smoke:server_stick"))));
            var id = client.player.getUUID();
            pending = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                var box = MahjongSupplies.completeBox(top.skyeyefast.mchjong.item.TileMaterial.BONE);
                var items = MahjongSupplies.contents(box);
                items.set(MahjongSupplies.DYE_SLOT, new net.minecraft.world.item.ItemStack(MahjongContent.CREATIVE_MAHJONG_DYE));
                MahjongSupplies.setContents(box, items);
                player.getInventory().setItem(0, box);
                player.getInventory().selected = 0;
                box.getItem().use(player.serverLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
            });
            stage = 2; ticks = 0;
        } else if (stage == 2 && pending.isDone() && client.screen instanceof top.skyeyefast.mchjong.client.MahjongBoxScreen && ticks > 20) {
            pending.join();
            require(RiichiStickPresets.forPlayer(client.player.getGameProfile().getName())
                .equals(ResourceLocation.parse("smoke:server_stick")), "Shared stick selection was not synchronized");
            var selector = button(client, "box.mchjong.preset_choice");
            String serverLabel = net.minecraft.network.chat.Component.translatable("box.mchjong.preset_choice",
                net.minecraft.network.chat.Component.literal("Server Test")).getString();
            for (int i = 0; i < TileFacePresets.choices().size() && !selector.getMessage().getString().equals(serverLabel); i++) selector.onPress();
            require(selector.getMessage().getString().equals(serverLabel), "Server preset missing from selector");
            stage = 21; ticks = 0;
        } else if (stage == 21 && ticks > 4) {
            Screenshot.grab(output.toFile(), "59-resource-server-box.png", client.getMainRenderTarget(), ignored -> {});
            var selector = button(client, "box.mchjong.preset_choice");
            String label = net.minecraft.network.chat.Component.translatable("box.mchjong.preset_choice",
                net.minecraft.network.chat.Component.literal("Local Test")).getString();
            for (int i = 0; i < TileFacePresets.choices().size() && !selector.getMessage().getString().equals(label); i++) selector.onPress();
            require(selector.getMessage().getString().equals(label), "New preset missing from selector");
            button(client, "box.mchjong.print").onPress();
            stage = 3; ticks = 0;
        } else if (stage == 3 && ticks > 20) {
            var menu = (MahjongBoxMenu) client.player.containerMenu;
            require(MahjongSupplies.facePreset(menu.getSlot(0).getItem()).equals(CUSTOM), "Custom preset packet did not print");
            button(client, "box.mchjong.back_choice").onPress();
            stage = 31; ticks = 0;
        } else if (stage == 31 && client.screen instanceof top.skyeyefast.mchjong.client.MahjongBoxBackScreen && ticks > 5) {
            Screenshot.grab(output.toFile(), "60-resource-back-choices.png", client.getMainRenderTarget(), ignored -> {});
            var choice = client.screen.children().stream()
                .filter(child -> child instanceof net.minecraft.client.gui.components.Button)
                .map(child -> (net.minecraft.client.gui.components.Button) child)
                .filter(button -> button.getMessage().getString().equals("Local Back")).findFirst().orElseThrow();
            choice.onPress();
            stage = 32; ticks = 0;
        } else if (stage == 32 && ticks > 20) {
            var menu = (MahjongBoxMenu) client.player.containerMenu;
            require(MahjongSupplies.backPreset(menu.getSlot(0).getItem()).equals(ResourceLocation.parse("smoke:custom_back")),
                "Back preset packet did not update physical tiles");
            Screenshot.grab(output.toFile(), "60-resource-custom-box.png", client.getMainRenderTarget(), ignored -> {});
            client.screen.onClose();
            stage = 4; ticks = 0;
        } else if (stage == 4) {
            Files.createDirectories(output.resolve("resource-custom"));
            if (!customized.tick(client, table, output.resolve("resource-custom"))) return false;
            client.getWindow().setWindowed(1280, 800);
            client.options.guiScale().set(2);
            client.resizeDisplay();
            var screen = new top.skyeyefast.mchjong.client.TableScreen(table.getBlockPos());
            client.setScreen(screen);
            screen.resetView();
            stage = 8; ticks = 0;
        } else if (stage == 8 && ticks > 20) {
            Screenshot.grab(output.toFile(), "61-resource-custom-wall.png", client.getMainRenderTarget(), ignored -> {});
            var screen = (top.skyeyefast.mchjong.client.TableScreen) client.screen;
            client.setScreen(new top.skyeyefast.mchjong.client.TableOptionsScreen(screen));
            stage = 81; ticks = 0;
        } else if (stage == 81 && ticks > 5) {
            button(client, "settings.mchjong.scope.personal").onPress();
            button(client, "settings.mchjong.personal_presets").onPress();
            stage = 82; ticks = 0;
        } else if (stage == 82 && client.screen instanceof top.skyeyefast.mchjong.client.PersonalPresetsScreen && ticks > 5) {
            Screenshot.grab(output.toFile(), "61-resource-stick-choices.png", client.getMainRenderTarget(), ignored -> {});
            client.getWindow().setWindowed(640, 480);
            client.resizeDisplay();
            stage = 83; ticks = 0;
        } else if (stage == 83 && ticks > 8) {
            if (ticks == 9) Screenshot.grab(output.toFile(), "61-resource-stick-choices-small.png",
                client.getMainRenderTarget(), ignored -> {});
            var choice = client.screen.children().stream()
                .filter(child -> child instanceof net.minecraft.client.gui.components.Button)
                .map(child -> (net.minecraft.client.gui.components.Button) child)
                .filter(button -> button.getMessage().getString().equals("Local Stick")).findFirst();
            if (choice.isEmpty()) {
                var next = client.screen.children().stream()
                    .filter(child -> child instanceof net.minecraft.client.gui.components.Button)
                    .map(child -> (net.minecraft.client.gui.components.Button) child)
                    .filter(button -> button.getMessage().getString().equals(">") && button.active)
                    .findFirst().orElseThrow();
                next.onPress();
                return false;
            }
            choice.orElseThrow().onPress();
            require(top.skyeyefast.mchjong.client.TableSettings.get().riichiStickPreset.equals(ResourceLocation.parse("smoke:custom_stick")),
                "Personal stick selection was not saved");
            button(client, "settings.mchjong.voice_preset").onPress();
            stage = 85; ticks = 0;
        } else if (stage == 85 && ticks > 5) {
            Screenshot.grab(output.toFile(), "61-resource-voice-choices-small.png", client.getMainRenderTarget(), ignored -> {});
            client.getWindow().setWindowed(1280, 800);
            client.resizeDisplay();
            stage = 86; ticks = 0;
        } else if (stage == 86 && ticks > 8) {
            Screenshot.grab(output.toFile(), "61-resource-voice-choices.png", client.getMainRenderTarget(), ignored -> {});
            var choice = client.screen.children().stream()
                .filter(child -> child instanceof net.minecraft.client.gui.components.Button)
                .map(child -> (net.minecraft.client.gui.components.Button) child)
                .filter(button -> button.getMessage().getString().equals("Local Voice")).findFirst().orElseThrow();
            choice.onPress();
            require(top.skyeyefast.mchjong.client.TableSettings.get().voicePreset.equals(ResourceLocation.parse("smoke:custom_voice")),
                "Personal voice selection was not saved");
            var path = VoicePresets.audioPath(ResourceLocation.parse("smoke:custom_voice"), "ron");
            require(path != null, "Selected voice recording was missing");
            voiceDecode = new net.minecraft.client.sounds.SoundBufferLibrary(client.getResourceManager()).getCompleteBuffer(path);
            top.skyeyefast.mchjong.client.TableAudio.preview();
            stage = 87; ticks = 0;
        } else if (stage == 87 && voiceDecode.isDone() && ticks > 5) {
            voiceDecode.join();
            client.screen.onClose();
            client.screen.onClose();
            stage = 84; ticks = 0;
        } else if (stage == 84 && ticks > 10) {
            require(RiichiStickPresets.forPlayer(client.player.getGameProfile().getName()).equals(RiichiStickPresets.DEFAULT),
                "Client-only stick selection was shared with the server");
            var screen = (top.skyeyefast.mchjong.client.TableScreen) client.screen;
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
            stage = 6; ticks = 0;
        } else if (stage == 6 && ticks > 20) {
            require(client.screen instanceof top.skyeyefast.mchjong.client.TableScreen screen && screen.immersive(), "Resource fixture did not enter immersive view");
            Screenshot.grab(output.toFile(), "61-resource-custom-immersive.png", client.getMainRenderTarget(), ignored -> {});
            client.getWindow().setWindowed(640, 480);
            client.resizeDisplay();
            stage = 7; ticks = 0;
        } else if (stage == 7 && ticks > 20) {
            Screenshot.grab(output.toFile(), "62-resource-custom-immersive-small.png", client.getMainRenderTarget(), ignored -> {});
            client.screen.onClose();
            Files.delete(localArchive);
            Files.delete(localBackArchive);
            Files.delete(localStickArchive);
            Files.delete(localVoiceArchive);
            client.getResourcePackRepository().setSelected(selected);
            pending = client.reloadResourcePacks();
            stage = 5; ticks = 0;
        } else if (stage == 5 && ready(client)) {
            require(!TileFacePresets.choices().contains(CUSTOM), "Removed pack left a stale preset");
            require(!TileBackPresets.choices().contains(ResourceLocation.parse("smoke:custom_back")), "Removed back left a stale preset");
            require(!RiichiStickPresets.choices().contains(ResourceLocation.parse("smoke:custom_stick")), "Removed stick left a stale preset");
            require(!VoicePresets.choices().contains(ResourceLocation.parse("smoke:custom_voice")), "Removed voice left a stale preset");
            require(TileBackPresets.texture(ResourceLocation.parse("smoke:custom_back")).equals(TileMesh.BACK),
                "Unavailable back did not use the default pattern");
            require(TileMesh.atlas(CUSTOM).equals(TileMesh.atlas(TileFacePreset.KANSAI)),
                "Unavailable faces did not use the default Kansai atlas");
            require(TileMesh.glyphs(CUSTOM).equals(TileMesh.glyphs(TileFacePreset.KANSAI)),
                "Unavailable faces did not use the default Kansai engravings");
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
    private static void tileImage(Path path, String key) throws java.io.IOException {
        Files.createDirectories(path.getParent());
        try (var image = new NativeImage(16, 16, false)) {
            int color = 0xff000000 | (key.hashCode() & 0x00ffffff);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                image.setPixelRGBA(x, y, x >= 3 && x < 13 && y >= 3 && y < 13 ? color : 0);
            image.writeToFile(path);
        }
    }
    private static void archive(Path target, String preset, String label, Path images) throws java.io.IOException {
        Files.createDirectories(target.getParent());
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(target))) {
            String root = "smoke/" + preset;
            zip.putNextEntry(new java.util.zip.ZipEntry(root + "/preset.toml"));
            zip.write(("name = \"" + label + "\"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            for (String key : top.skyeyefast.mchjong.config.PresetArchives.TILE_KEYS) {
                zip.putNextEntry(new java.util.zip.ZipEntry(root + "/tiles/" + key + ".png"));
                zip.write(Files.readAllBytes(images.resolve(key + ".png")));
                zip.closeEntry();
            }
        }
    }
    private static void backArchive(Path target, String preset, String label, Path image) throws java.io.IOException {
        Files.createDirectories(target.getParent());
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(target))) {
            String root = "smoke/" + preset;
            zip.putNextEntry(new java.util.zip.ZipEntry(root + "/preset.toml"));
            zip.write(("name = \"" + label + "\"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry(root + "/back.png"));
            zip.write(Files.readAllBytes(image));
            zip.closeEntry();
        }
    }
    private static void stickArchive(Path target, String preset, String label, Path image) throws java.io.IOException {
        Files.createDirectories(target.getParent());
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(target))) {
            String root = "smoke/" + preset;
            zip.putNextEntry(new java.util.zip.ZipEntry(root + "/preset.toml"));
            zip.write(("name = \"" + label + "\"\nlength = 12\nwidth = 1\nheight = 0.5\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry(root + "/stick.png"));
            zip.write(Files.readAllBytes(image));
            zip.closeEntry();
        }
    }
    private static void voiceArchive(Path target, String preset, String label, byte[] recording) throws java.io.IOException {
        Files.createDirectories(target.getParent());
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(target))) {
            String root = "smoke/" + preset;
            zip.putNextEntry(new java.util.zip.ZipEntry(root + "/preset.toml"));
            zip.write(("name = \"" + label + "\"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry(root + "/voices/ron.ogg"));
            zip.write(recording);
            zip.closeEntry();
        }
    }
    private static void pattern(Path path, int width, int height) throws java.io.IOException {
        Files.createDirectories(path.getParent());
        try (var image = new NativeImage(width, height, false)) {
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                image.setPixelRGBA(x, y, Math.abs(x - width / 2) < width / 12 || Math.abs(y - height / 2) < height / 12 ? 0xff20e040 : 0);
            image.writeToFile(path);
        }
    }
    private static void backPattern(Path path) throws java.io.IOException {
        Files.createDirectories(path.getParent());
        try (var image = new NativeImage(256, 384, false)) {
            // Contrasting top and bottom halves expose a rotated wall back in the world capture.
            for (int y = 0; y < 384; y++) for (int x = 0; x < 256; x++)
                image.setPixelRGBA(x, y, y < 192 ? 0xff2040e0 : 0xffe04020);
            image.writeToFile(path);
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
