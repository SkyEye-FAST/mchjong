package top.skyeyefast.mchjong.client;

import java.io.IOException;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TableSettingsScreen extends Screen {
    private final Screen parent;
    private final TableSettings settings = TableSettings.get();
    private int tab;
    private int page;
    private boolean saveFailed;
    private int pages;
    private SettingsLayout layout;
    private final List<AbstractWidget> options = new ArrayList<>();

    public TableSettingsScreen(Screen parent) {
        super(Component.translatable("settings.mchjong.title"));
        this.parent = parent;
    }
    public RiichiTableScreen tableScreen() { return parent instanceof RiichiTableScreen table ? table : null; }
    public McrTableScreen mcrTableScreen() { return parent instanceof McrTableScreen table ? table : null; }
    public SichuanTableScreen sichuanTableScreen() { return parent instanceof SichuanTableScreen table ? table : null; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override protected void init() {
        clearWidgets();
        options.clear();
        layout = SettingsLayout.of(width, height);
        int left = layout.bodyLeft(), span = layout.bodyWidth();
        for (int i = 0; i < 4; i++) {
            final int index = i;
            var button = MahjongButton.create(Component.translatable("settings.mchjong.tab." + i), ignored -> {
                tab = index; page = 0; init();
            }).bounds(layout.left() + 4, 62 + i * 22, layout.rail() - 4, 20).build().navigation();
            button.selected(tab == i);
            addRenderableWidget(button);
        }
        if (tab == 0) {
            for (var information : TableSettings.Information.values()) {
                var toggle = addToggle(information.key(), settings.show(information), () -> settings.toggle(information));
                if (information == TableSettings.Information.REMAINING && !settings.showRiver) {
                    toggle.active = false;
                }
            }
        } else if (tab == 1) {
            addChoice("settings.mchjong.discard", settings.discardMode, () -> {
                var values = TableSettings.DiscardMode.values();
                settings.discardMode = values[Math.floorMod(settings.discardMode.ordinal() + (hasShiftDown() ? -1 : 1), values.length)];
            });
            addChoice("settings.mchjong.guides", settings.guideLines, () -> {
                var values = TableSettings.GuideLines.values();
                settings.guideLines = values[Math.floorMod(settings.guideLines.ordinal() + (hasShiftDown() ? -1 : 1), values.length)];
            });
            addToggle("settings.mchjong.action_tiles", settings.actionTiles, () -> settings.actionTiles = !settings.actionTiles);
            addToggle("settings.mchjong.highlight", settings.highlightTiles, () -> settings.highlightTiles = !settings.highlightTiles);
            addToggle("settings.mchjong.animations", settings.animations, () -> settings.animations = !settings.animations);
            addChoice("settings.mchjong.tile_labels", settings.tileLabels, () -> {
                settings.tileLabels = settings.tileLabels == TableSettings.TileLabels.NAME
                    ? TableSettings.TileLabels.MPSZ : TableSettings.TileLabels.NAME;
            });
            addToggle("settings.mchjong.auto_seat", settings.autoSeat,
                () -> settings.autoSeat = !settings.autoSeat)
                .setTooltip(Tooltip.create(Component.translatable("settings.mchjong.auto_seat_help")));
        } else if (tab == 2) {
            options.add(new CameraSlider(left, 0, span, true));
            options.add(new CameraSlider(left, 0, span, false));
            addAction("settings.mchjong.reset_view", this::resetView);
            addToggle("settings.mchjong.river", settings.showRiver, () -> settings.showRiver = !settings.showRiver);
        } else {
            options.add(new VolumeSlider(left, 0, span, false));
            addToggle("settings.mchjong.countdown", settings.countdownSounds, () -> settings.countdownSounds = !settings.countdownSounds);
            addAction("settings.mchjong.audio_preview", RiichiAudio::preview);
            addChoice("settings.mchjong.voice", settings.voiceSource, () -> {
                var modes = TableSettings.VoiceSource.values();
                settings.voiceSource = modes[Math.floorMod(settings.voiceSource.ordinal() + (hasShiftDown() ? -1 : 1), modes.length)];
                RiichiAudio.settingsChanged();
            }).setTooltip(Tooltip.create(Component.translatable("settings.mchjong.voice_preset_note")));
            var voiceVolume = new VolumeSlider(left, 0, span, true);
            voiceVolume.active = settings.voiceSource == TableSettings.VoiceSource.SELECTED;
            options.add(voiceVolume);
        }
        int rows = layout.rows();
        pages = Math.max(1, (options.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = 0; i < rows && page * rows + i < options.size(); i++) {
            var option = options.get(page * rows + i);
            option.setY(62 + i * 22);
            addRenderableWidget(option);
        }
        if (pages > 1) {
            var previous = MahjongButton.create(Component.literal("<"), ignored -> { page--; init(); })
                .bounds(left, layout.paging(), 30, 20).build().navigation();
            previous.active = page > 0;
            addRenderableWidget(previous);
            var next = MahjongButton.create(Component.literal(">"), ignored -> { page++; init(); })
                .bounds(left + span - 30, layout.paging(), 30, 20).build().navigation();
            next.active = page + 1 < pages;
            addRenderableWidget(next);
        }
        int column = (span - 6) / 2;
        addRenderableWidget(MahjongButton.create(Component.translatable("settings.mchjong.reset"), ignored -> {
            settings.reset(); RiichiAudio.settingsChanged(); resetView(); init();
            RiichiStickPresets.sendChoice();
            VoicePresets.sendChoice();
        }).bounds(left, height - 30, column, 20).build());
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(left + column + 6, height - 30 - MahjongUi.STEP, column, 20).build().primary());
    }

    private Button addToggle(String key, boolean enabled, Runnable toggle) {
        Component label = Component.translatable("settings.mchjong.toggle", Component.translatable(key),
            Component.translatable(enabled ? "options.on" : "options.off"));
        var button = MahjongButton.create(label, ignored -> { toggle.run(); init(); })
            .bounds(layout.bodyLeft(), 0, layout.bodyWidth(), 20).build()
            .option(Component.translatable(key), Component.empty()).checked(enabled);
        options.add(button);
        return button;
    }

    private MahjongButton addChoice(String key, Enum<?> value, Runnable action) {
        Component label = Component.translatable(key);
        Component current = Component.translatable(key + "." + value.name().toLowerCase(Locale.ROOT));
        var button = MahjongButton.create(Component.translatable("settings.mchjong.toggle", label, current),
            ignored -> { action.run(); init(); }).bounds(layout.bodyLeft(), 0, layout.bodyWidth(), 20).build().option(label, current);
        options.add(button);
        return button;
    }

    private void addAction(String key, Runnable action) {
        Component label = Component.translatable(key);
        options.add(MahjongButton.create(label, ignored -> action.run()).bounds(layout.bodyLeft(), 0, layout.bodyWidth(), 20)
            .build().option(label, Component.literal("›")));
    }

    private void resetView() {
        if (parent instanceof RiichiTableScreen table) table.resetView();
        else if (parent instanceof McrTableScreen table) table.resetView();
        else if (parent instanceof SichuanTableScreen table) table.resetView();
        else settings.camera().reset(settings.cameraDistance, settings.cameraHeight);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout.paint(graphics, font, width, Component.translatable("settings.mchjong.scope.personal").append(" › ").append(title),
            Component.translatable("settings.mchjong.tab." + tab));
        graphics.fill(layout.left(), 38, layout.left() + layout.rail(), 152, MahjongUi.INPUT);
        MahjongUi.text(graphics, font, Component.translatable("settings.mchjong.preferences"),
            layout.left() + 7, 44, layout.rail() - 14, MahjongUi.TEXT, false);
        if (pages > 1) graphics.drawCenteredString(font, (page + 1) + " / " + pages,
            layout.bodyLeft() + layout.bodyWidth() / 2, layout.paging() + 6, MahjongUi.MUTED);
        if (saveFailed) graphics.drawCenteredString(font, Component.translatable("settings.mchjong.save_failed"), width / 2, height - 76, MahjongUi.NEGATIVE);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() {
        try {
            settings.save(TableSettings.configPath());
            minecraft.setScreen(minecraft.level == null && parent instanceof RiichiTableScreen ? null : parent);
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
            String key = distance ? "settings.mchjong.camera_distance" : "settings.mchjong.camera_height";
            option(Component.translatable(key + ".label"), Component.literal(String.format(Locale.ROOT, "%.2f", distance ? settings.cameraDistance : settings.cameraHeight)));
            setMessage(Component.translatable(distance ? "settings.mchjong.camera_distance" : "settings.mchjong.camera_height",
                String.format(Locale.ROOT, "%.2f", distance ? settings.cameraDistance : settings.cameraHeight)));
        }
        @Override protected void applyValue() {
            if (distance) settings.cameraDistance = TableSettings.MIN_CAMERA_DISTANCE
                + value * (TableSettings.MAX_CAMERA_DISTANCE - TableSettings.MIN_CAMERA_DISTANCE);
            else settings.cameraHeight = TableSettings.MIN_CAMERA_HEIGHT
                + value * (TableSettings.MAX_CAMERA_HEIGHT - TableSettings.MIN_CAMERA_HEIGHT);
            settings.camera().configure(settings.cameraDistance, settings.cameraHeight);
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
            option(Component.translatable((voice ? "settings.mchjong.voice_volume" : "settings.mchjong.effects_volume") + ".label"),
                Component.literal(Math.round(value * 100) + "%"));
            setMessage(Component.translatable(voice ? "settings.mchjong.voice_volume" : "settings.mchjong.effects_volume", Math.round(value * 100)));
        }
        @Override protected void applyValue() {
            if (voice) settings.voiceVolume = value;
            else settings.effectsVolume = value;
        }
    }
}
