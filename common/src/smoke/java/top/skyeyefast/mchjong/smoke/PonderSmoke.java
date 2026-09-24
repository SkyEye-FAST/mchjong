package top.skyeyefast.mchjong.smoke;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Installed-Ponder validation; referenced only by the explicitly enabled client smoke. */
final class PonderSmoke {
    private static final BlockPos TABLE = new BlockPos(3, 1, 3);
    private static final String[] LANGUAGES = {"en_us", "ja_jp", "zh_cn", "zh_tw"};
    private static final Map<String, Integer> TEXT_COUNTS = Map.of(
        "table_placement", 4, "table_equipment", 5, "table_playing", 6,
        "workshop_production", 6, "workshop_dyeing", 5);
    private static int stage;
    private static int language;
    private static int sceneIndex;
    private static int ticks;
    private static String previousLanguage;
    private static int previousWidth, previousHeight, previousScale;
    private static boolean small;
    private static CompletableFuture<Void> reload;
    private static List<PonderScene> scenes;

    private PonderSmoke() {}

    static boolean tick(Minecraft client, Path output) throws IOException {
        if (stage == 5) return true;
        if (stage == 0) {
            verifyRegistration(client);
            previousLanguage = client.getLanguageManager().getSelected();
            previousWidth = client.getWindow().getScreenWidth();
            previousHeight = client.getWindow().getScreenHeight();
            previousScale = client.options.guiScale().get();
            stage = 1;
        }
        if (stage == 1) {
            client.setScreen(null);
            client.getLanguageManager().setSelected(language < LANGUAGES.length ? LANGUAGES[language] : previousLanguage);
            reload = client.reloadResourcePacks();
            stage = language < LANGUAGES.length ? 2 : 4;
            return false;
        }
        if (stage == 2 || stage == 4) {
            if (!reload.isDone() || client.getOverlay() != null) return false;
            reload.join();
            if (stage == 4) {
                client.getWindow().setWindowed(previousWidth, previousHeight);
                client.options.guiScale().set(previousScale);
                client.resizeDisplay();
                Files.writeString(output.resolve("ponder-checks.txt"),
                    "Ponder: " + (workshopInstalled() ? "twelve item entries, five scenes" : "seven item entries, three scenes")
                        + ", four locales, resource reload, full playback, replay reset, native UI and isolated equipment passed.\n");
                stage = 5;
                return true;
            }
            scenes = new java.util.ArrayList<>(PonderIndex.getSceneAccess().compile(MahjongContent.id("mahjong_table")));
            if (workshopInstalled()) scenes.addAll(PonderIndex.getSceneAccess().compile(MahjongContent.id("mahjong_printing_plate")));
            require(scenes.size() == (workshopInstalled() ? 5 : 3), "Scene count changed after language reload");
            for (PonderScene scene : scenes) verifyPlayback(scene);
            sceneIndex = 0;
            show(client);
            stage = 3;
            return false;
        }
        if (++ticks < 25) return false;
        String name = "ponder-" + LANGUAGES[language] + "-" + scenes.get(sceneIndex).getId().getPath()
            + (small ? "-small" : "") + ".png";
        Screenshot.grab(output.toFile(), name, client.getMainRenderTarget(), message -> {});
        if (!small) {
            small = true;
            show(client);
        } else {
            small = false;
            if (++sceneIndex < scenes.size()) show(client);
            else { language++; stage = 1; }
        }
        return false;
    }

    private static void show(Minecraft client) {
        client.getWindow().setWindowed(small ? 960 : 1280, small ? 720 : 800);
        client.options.guiScale().set(small ? 3 : 2);
        client.resizeDisplay();
        PonderScene scene = scenes.get(sceneIndex);
        scene.begin();
        PonderUI screen = new SingleSceneScreen(scene);
        client.setScreen(screen);
        screen.setComfyReadingEnabled(false);
        int keyframe = sceneIndex == 3 ? 4 : sceneIndex == 4 ? 2 : sceneIndex == 2 ? 3 : 2;
        screen.seekToTime(scene.getKeyframeTime(keyframe) + 15);
        ticks = 0;
    }

    private static void verifyRegistration(Minecraft client) throws IOException {
        var expected = Map.of("mahjong_table", 3, "automatic_mahjong_table", 3, "mahjong_stool", 2,
            "mahjong_box", 1, "table_cloth", 1, "mahjong_tile", 1, "point_stick", 1);
        for (var entry : expected.entrySet()) {
            var compiled = PonderIndex.getSceneAccess().compile(MahjongContent.id(entry.getKey()));
            require(compiled.size() == entry.getValue(), "Missing or duplicate scenes for " + entry.getKey());
            require(compiled.stream().map(PonderScene::getId).distinct().count() == compiled.size(), "Duplicate scene IDs");
        }
        if (workshopInstalled()) for (String item : List.of("mahjong_printing_plate", "incomplete_mahjong_box",
                "mahjong_dye", "red_dora_dye", "undo_dye")) {
            var compiled = PonderIndex.getSceneAccess().compile(MahjongContent.id(item));
            require(compiled.size() == 2 && compiled.stream().map(PonderScene::getId).distinct().count() == 2,
                "Missing or duplicate workshop scenes for " + item);
        }
        require(PonderIndex.streamPlugins().filter(plugin -> plugin.getModId().equals("mchjong")).count() == 1,
            "Plugin registered more than once");
        PonderIndex.reload();
        require(PonderIndex.getSceneAccess().getRegisteredEntries().stream()
            .filter(entry -> entry.getKey().getNamespace().equals("mchjong")).count() == (workshopInstalled() ? 22 : 12),
            "Scene reload changed registration count");
        require(PonderUI.of(MahjongContent.id("mahjong_table")).getActiveScene().getId()
            .equals(MahjongContent.id("table_placement")), "Native item entry did not open the first scene");

        Set<String> keys = new HashSet<>();
        keys.add("mchjong.ponder.tag.mahjong");
        keys.add("mchjong.ponder.tag.mahjong.description");
        if (workshopInstalled()) {
            keys.add("mchjong.ponder.tag.mahjong_workshop");
            keys.add("mchjong.ponder.tag.mahjong_workshop.description");
        }
        TEXT_COUNTS.forEach((id, count) -> {
            if (id.startsWith("workshop_") && !workshopInstalled()) return;
            keys.add("mchjong.ponder." + id + ".header");
            for (int i = 1; i <= count; i++) keys.add("mchjong.ponder." + id + ".text_" + i);
        });
        Map<String, String> english = new java.util.TreeMap<>();
        PonderIndex.getLangAccess().provideLang("mchjong", english::put);
        // Ponder's datagen API registers plugins again; restore the runtime index after collecting defaults.
        PonderIndex.reload();
        require(english.keySet().equals(keys), "Ponder's generated translation keys differ from the resources");
        var resourceKeys = new HashSet<>(keys);
        resourceKeys.add("mchjong.ponder.tag.mahjong_workshop");
        resourceKeys.add("mchjong.ponder.tag.mahjong_workshop.description");
        TEXT_COUNTS.forEach((id, count) -> {
            resourceKeys.add("mchjong.ponder." + id + ".header");
            for (int i = 1; i <= count; i++) resourceKeys.add("mchjong.ponder." + id + ".text_" + i);
        });
        for (String locale : LANGUAGES) {
            try (var reader = client.getResourceManager().openAsReader(MahjongContent.id("lang/" + locale + ".json"))) {
                var translations = JsonParser.parseReader(reader).getAsJsonObject();
                var actual = translations.keySet().stream().filter(key -> key.startsWith("mchjong.ponder.")).collect(java.util.stream.Collectors.toSet());
                require(actual.equals(resourceKeys), "Incomplete Ponder translations in " + locale);
                for (String key : resourceKeys) require(!translations.get(key).getAsString().isBlank(), "Empty translation: " + key);
                if (locale.equals("en_us")) for (String key : keys)
                    require(english.get(key).equals(translations.get(key).getAsString()), "English storyboard text differs: " + key);
            }
        }
    }

    private static void verifyPlayback(PonderScene scene) {
        String id = scene.getId().getPath();
        require(TEXT_COUNTS.containsKey(id), "Unexpected scene: " + id);
        require(scene.getBasePlateSize() == 7, "Incorrect scene bounds");
        String titleKey = "mchjong.ponder." + id + ".header";
        require(I18n.exists(titleKey) && scene.getTitle().equals(I18n.get(titleKey)), "Untranslated scene title");
        for (int i = 1; i <= TEXT_COUNTS.get(id); i++) {
            String key = "mchjong.ponder." + id + ".text_" + i;
            require(I18n.exists(key) && scene.getString("text_" + i).equals(I18n.get(key)), "Untranslated scene text: " + key);
        }
        require(scene.getWorld().isClientSide, "Tutorial world is not client-side");
        require((net.minecraft.world.level.Level) scene.getWorld() != Minecraft.getInstance().level, "Tutorial uses the live game level");
        for (int pass = 0; pass < 2; pass++) {
            scene.begin();
            MahjongTableBlockEntity initial = table(scene);
            require(initial.getBlockState().is(MahjongContent.TABLE), "Replay did not restore the ordinary table");
            require(!initial.equipment().hasCloth() && initial.equipment().boxes().isEmpty()
                && initial.equipment().drawer(0).isEmpty(), "Replay retained demonstration equipment");
            for (int seat = 0; seat < 4; seat++)
                require(scene.getWorld().getBlockState(TableGeometry.stool(TABLE, seat)).is(MahjongContent.STOOL), "Missing stool");
            scene.seekToTime(scene.getTotalTime() + 1);
            require(scene.isFinished(), "Storyboard did not finish: " + id);
            if (id.equals("table_equipment")) {
                require(table(scene).equipment().hasCloth() && table(scene).equipment().deck() != null,
                    "Equipment scene did not prepare a playable set");
                require(table(scene).equipment().drawer(0).getItem(0).getCount() == 1, "Point-stick demo failed");
            } else if (id.equals("table_playing")) {
                require(table(scene).getBlockState().is(MahjongContent.AUTO_TABLE), "Automatic-table transition failed");
            }
        }
        scene.begin();
    }

    private static MahjongTableBlockEntity table(PonderScene scene) {
        require(scene.getWorld().getBlockEntity(TABLE) instanceof MahjongTableBlockEntity, "Missing tutorial table block entity");
        return (MahjongTableBlockEntity) scene.getWorld().getBlockEntity(TABLE);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static boolean workshopInstalled() {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(MahjongContent.id("mahjong_printing_plate"));
    }

    private static final class SingleSceneScreen extends PonderUI {
        private SingleSceneScreen(PonderScene scene) { super(List.of(scene)); }
    }
}
