package top.skyeyefast.mchjong.client;

import java.io.IOException;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TableSettingsScreen extends Screen {
    private final TableScreen parent;
    private final TableSettings settings = TableSettings.get();
    private int tab;
    private int page;
    private boolean saveFailed;

    public TableSettingsScreen(TableScreen parent) {
        super(Component.translatable("settings.mchjong.title"));
        this.parent = parent;
    }
    public TableScreen tableScreen() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override protected void init() {
        clearWidgets();
        int span = Math.min(560, width - 24);
        int left = (width - span) / 2;
        int tabs = 4;
        for (int i = 0; i < tabs; i++) {
            final int index = i;
            var button = MahjongButton.create(Component.translatable("settings.mchjong.tab." + i), ignored -> {
                tab = index; page = 0; init();
            }).bounds(left + i * (span / tabs), 33, span / tabs - 4, 20).build();
            button.selected(tab == i);
            addRenderableWidget(button);
        }
        int column = (span - 6) / 2;
        int rows = Math.max(1, (height - 130) / 26);
        if (tab == 0) {
            var values = TableSettings.Information.values();
            int perPage = rows * 2;
            page = Math.clamp(page, 0, (values.length - 1) / perPage);
            for (int n = 0; n < perPage && page * perPage + n < values.length; n++) {
                var information = values[page * perPage + n];
                var toggle = addToggle(left + (n % 2) * (column + 6), 65 + (n / 2) * 26, column,
                    information.key(), settings.show(information), () -> settings.toggle(information));
                if (information == TableSettings.Information.REMAINING && !settings.showRiver) {
                    toggle.active = false;
                }
            }
            if (values.length > perPage) {
                var previous = MahjongButton.create(Component.literal("<"), ignored -> { page--; init(); })
                    .bounds(width / 2 - 66, height - 61, 30, 20).build();
                previous.active = page > 0;
                addRenderableWidget(previous);
                var next = MahjongButton.create(Component.literal(">"), ignored -> { page++; init(); })
                    .bounds(width / 2 + 36, height - 61, 30, 20).build();
                next.active = (page + 1) * perPage < values.length;
                addRenderableWidget(next);
            }
        } else if (tab == 1) {
            addRenderableWidget(MahjongButton.create(value("settings.mchjong.discard", settings.discardMode), ignored -> {
                var values = TableSettings.DiscardMode.values();
                settings.discardMode = values[(settings.discardMode.ordinal() + 1) % values.length]; init();
            }).bounds(left, 65, span, 20).build());
            addRenderableWidget(MahjongButton.create(value("settings.mchjong.guides", settings.guideLines), ignored -> {
                var values = TableSettings.GuideLines.values();
                settings.guideLines = values[(settings.guideLines.ordinal() + 1) % values.length]; init();
            }).bounds(left, 91, span, 20).build());
            addToggle(left, 117, column, "settings.mchjong.action_tiles", settings.actionTiles, () -> settings.actionTiles = !settings.actionTiles);
            addToggle(left + column + 6, 117, column, "settings.mchjong.highlight", settings.highlightTiles, () -> settings.highlightTiles = !settings.highlightTiles);
            addToggle(left, 143, span, "settings.mchjong.animations", settings.animations, () -> settings.animations = !settings.animations);
        } else if (tab == 2) {
            addRenderableWidget(new CameraSlider(left, 65, span, true));
            addRenderableWidget(new CameraSlider(left, 91, span, false));
            addRenderableWidget(MahjongButton.create(Component.translatable("settings.mchjong.reset_view"), ignored -> parent.resetView())
                .bounds(left, 117, span, 20).build());
            addToggle(left, 143, span, "settings.mchjong.river", settings.showRiver, () -> settings.showRiver = !settings.showRiver);
        } else {
            addRenderableWidget(new VolumeSlider(left, 65, column, false));
            var voiceVolume = addRenderableWidget(new VolumeSlider(left + column + 6, 65, column, true));
            voiceVolume.active = settings.voiceSource == TableSettings.VoiceSource.RESOURCE_PACK;
            addRenderableWidget(MahjongButton.create(value("settings.mchjong.voice", settings.voiceSource), ignored -> {
                var modes = TableSettings.VoiceSource.values();
                settings.voiceSource = modes[(settings.voiceSource.ordinal() + 1) % modes.length];
                TableAudio.settingsChanged(); init();
            }).bounds(left, 91, span, 20).build());
            addToggle(left, 117, column, "settings.mchjong.countdown", settings.countdownSounds,
                () -> settings.countdownSounds = !settings.countdownSounds);
            addRenderableWidget(MahjongButton.create(Component.translatable("settings.mchjong.audio_preview"), ignored -> TableAudio.preview())
                .bounds(left + column + 6, 117, column, 20).build());
        }
        addRenderableWidget(MahjongButton.create(Component.translatable("settings.mchjong.reset"), ignored -> {
            settings.reset(); TableAudio.settingsChanged(); parent.resetView(); init();
        }).bounds(left, height - 30, column, 20).build());
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(left + column + 6, height - 30, column, 20).build().primary());
    }

    private Button addToggle(int x, int y, int w, String key, boolean enabled, Runnable toggle) {
        Component label = Component.translatable("settings.mchjong.toggle", Component.translatable(key),
            Component.translatable(enabled ? "options.on" : "options.off"));
        var button = MahjongButton.create(label, ignored -> { toggle.run(); init(); }).bounds(x, y, w, 20).build().selected(enabled);
        button.setTooltip(Tooltip.create(label));
        return addRenderableWidget(button);
    }

    private static Component value(String key, Enum<?> value) {
        return Component.translatable("settings.mchjong.toggle", Component.translatable(key),
            Component.translatable(key + "." + value.name().toLowerCase(Locale.ROOT)));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MahjongUi.backdrop(graphics, width, height, 560);
        MahjongUi.text(graphics, font, title, 12, 16, width - 24, MahjongUi.TEXT, true);
        if (tab == 0) {
            int pages = (TableSettings.Information.values().length - 1) / (Math.max(1, (height - 130) / 26) * 2) + 1;
            if (pages > 1) graphics.drawCenteredString(font, (page + 1) + " / " + pages, width / 2, height - 55, MahjongUi.MUTED);
        }
        if (saveFailed) graphics.drawCenteredString(font, Component.translatable("settings.mchjong.save_failed"), width / 2, height - 76, MahjongUi.NEGATIVE);
        if (tab == 3) {
            int y = 145;
            for (var line : font.split(Component.translatable("settings.mchjong.pack_voice_note"), Math.min(540, width - 32))) {
                graphics.drawCenteredString(font, line, width / 2, y, MahjongUi.MUTED);
                y += 11;
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() {
        try {
            settings.save(TableSettings.configPath());
            minecraft.setScreen(minecraft.level == null ? null : parent);
        } catch (IOException failure) {
            org.slf4j.LoggerFactory.getLogger("mchjong").error("Cannot save table settings", failure);
            saveFailed = true;
        }
    }

    private final class CameraSlider extends MahjongSlider {
        private final boolean distance;
        CameraSlider(int x, int y, int w, boolean distance) {
            super(x, y, w, 20, Component.empty(), distance
                ? (settings.cameraDistance - TableSettings.MIN_CAMERA_DISTANCE) / (TableSettings.MAX_CAMERA_DISTANCE - TableSettings.MIN_CAMERA_DISTANCE)
                : (settings.cameraHeight - TableSettings.MIN_CAMERA_HEIGHT) / (TableSettings.MAX_CAMERA_HEIGHT - TableSettings.MIN_CAMERA_HEIGHT));
            this.distance = distance;
            updateMessage();
        }
        @Override protected void updateMessage() {
            setMessage(Component.translatable(distance ? "settings.mchjong.camera_distance" : "settings.mchjong.camera_height",
                String.format(Locale.ROOT, "%.2f", distance ? settings.cameraDistance : settings.cameraHeight)));
        }
        @Override protected void applyValue() {
            if (distance) settings.cameraDistance = TableSettings.MIN_CAMERA_DISTANCE
                + value * (TableSettings.MAX_CAMERA_DISTANCE - TableSettings.MIN_CAMERA_DISTANCE);
            else settings.cameraHeight = TableSettings.MIN_CAMERA_HEIGHT
                + value * (TableSettings.MAX_CAMERA_HEIGHT - TableSettings.MIN_CAMERA_HEIGHT);
            parent.resetView();
        }
    }

    private final class VolumeSlider extends MahjongSlider {
        private final boolean voice;
        VolumeSlider(int x, int y, int width, boolean voice) {
            super(x, y, width, 20, Component.empty(), voice ? settings.voiceVolume : settings.effectsVolume);
            this.voice = voice;
            updateMessage();
        }
        @Override protected void updateMessage() {
            setMessage(Component.translatable(voice ? "settings.mchjong.voice_volume" : "settings.mchjong.effects_volume", Math.round(value * 100)));
        }
        @Override protected void applyValue() {
            if (voice) settings.voiceVolume = value;
            else settings.effectsVolume = value;
        }
    }
}
