package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.MahjongVariant;

/** Real locale reloads and window sizes for the fixed and regional rule pages. */
final class VariantRuleLayoutSmoke {
    private static final List<String> LANGUAGES = List.of("en_us", "ja_jp", "zh_cn", "zh_tw");
    private final MahjongVariant variant;
    private Screen lobby, editor;
    private String originalLanguage;
    private CompletableFuture<Void> reload;
    private int stage, locale, size, ticks;
    VariantRuleLayoutSmoke(MahjongVariant variant) { this.variant = variant; }
    boolean tick(Minecraft client, Path output) {
        if (reload != null) {
            if (!reload.isDone() || client.getOverlay() != null) return false;
            reload.join(); reload = null;
        }
        if (stage == 0) {
            lobby = client.screen; originalLanguage = client.getLanguageManager().getSelected();
            press(client, "rules.mchjong.title"); editor = client.screen;
            stage = 1;
        }
        if (stage == 1) {
            client.getLanguageManager().setSelected(LANGUAGES.get(locale));
            reload = client.reloadResourcePacks(); stage = 2; return false;
        }
        if (stage == 2) {
            client.getWindow().setWindowed(size == 0 ? 960 : 1280, size == 0 ? 720 : 800);
            client.options.guiScale().set(size == 0 ? 3 : 2); client.resizeDisplay();
            client.setScreen(editor);
            var groups = switch (variant) {
                case SICHUAN -> List.of("sichuan.mchjong.rules.group.basic", "sichuan.mchjong.rules.group.kong", "sichuan.mchjong.rules.group.settlement");
                case TAIWAN -> List.of("rules.mchjong.group.basic", "taiwan.mchjong.payment_rules");
                default -> List.<String>of();
            };
            for (String group : groups) {
                press(client, group);
                for (int page = 0; page < 20; page++) {
                    AutomationControlsSmoke.checkBounds(client);
                    var next = editor.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(b -> b.visible && b.getMessage().getString().equals("›")).findFirst();
                    if (next.isEmpty() || !next.get().active) break;
                    next.get().onPress();
                }
            }
            if (!groups.isEmpty()) {
                press(client, groups.getFirst()); RuleEditorSmoke.presets(client);
                AutomationControlsSmoke.checkBounds(client); client.screen.onClose();
                if (client.screen != editor) throw new IllegalStateException("Regional preset cancellation lost its editor");
            }
            AutomationControlsSmoke.checkBounds(client);
            ticks = 0; stage = 3;
        } else if (stage == 3 && ++ticks >= 10) {
            SmokeScreenshots.grab(output.toFile(), variant.name().toLowerCase(java.util.Locale.ROOT) + "-rules-" + LANGUAGES.get(locale)
                + "-" + editor.width + "x" + editor.height + ".png", client.getMainRenderTarget(), ignored -> {});
            if (++size < 2) stage = 2;
            else if (++locale < LANGUAGES.size()) { size = 0; stage = 1; }
            else {
                client.getLanguageManager().setSelected(originalLanguage);
                reload = client.reloadResourcePacks(); stage = 4;
            }
        } else if (stage == 4) {
            client.setScreen(lobby); stage = 5;
        }
        return stage == 5;
    }
    private static void press(Minecraft client, String key) {
        var button = LobbySmoke.find(client, Component.translatable(key).getString());
        if (button == null || !button.active) throw new IllegalStateException("Missing regional rule control: " + key);
        button.onPress();
    }
}
