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

public final class TableSettingsScreen extends Screen implements TableChildScreen {
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
    public RiichiTableScreen tableScreen() { return TableChildScreen.root(parent) instanceof RiichiTableScreen table ? table : null; }
    public McrTableScreen mcrTableScreen() { return TableChildScreen.root(parent) instanceof McrTableScreen table ? table : null; }
    public SichuanTableScreen sichuanTableScreen() { return TableChildScreen.root(parent) instanceof SichuanTableScreen table ? table : null; }
    @Override public Screen parent() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics) {}

    @Override protected void init() {
        clearWidgets();
        options.clear();
        layout = SettingsLayout.of(width, height);
        int left = layout.bodyLeft(), span = layout.bodyWidth();
        for (int i = 0; i < 4; i++) {
            final int index = i;
            var button = MahjongButton.create(Component.translatable("settings.mchjong.tab." + i), ignored -> {
                tab = index; page = 0; init();
            }).bounds(layout.left() + 4, layout.contentTop() + i * 22, layout.rail() - 4, 20).build().navigation();
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
            addChoice("settings.mchjong.discard", settings.discardMode, value -> settings.discardMode = value);
            addChoice("settings.mchjong.guides", settings.guideLines, value -> settings.guideLines = value);
            addToggle("settings.mchjong.action_tiles", settings.actionTiles, () -> settings.actionTiles = !settings.actionTiles);
            addToggle("settings.mchjong.highlight", settings.highlightTiles, () -> settings.highlightTiles = !settings.highlightTiles);
            addToggle("settings.mchjong.animations", settings.animations, () -> settings.animations = !settings.animations);
            addChoice("settings.mchjong.tile_labels", settings.tileLabels, value -> settings.tileLabels = value);
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
            addChoice("settings.mchjong.voice", settings.voiceSource, value -> {
                settings.voiceSource = value; RiichiAudio.settingsChanged();
            }).setTooltip(Tooltip.create(Component.translatable("settings.mchjong.voice_preset_note")));
            var voiceVolume = new VolumeSlider(left, 0, span, true);
            voiceVolume.active = settings.voiceSource == TableSettings.VoiceSource.SELECTED;
            options.add(voiceVolume);
        }
        int rows = layout.rows();
        pages = Math.max(1, (options.size() + rows - 1) / rows);
        page = net.minecraft.util.Mth.clamp(page, 0, pages - 1);
        for (int i = 0; i < rows && page * rows + i < options.size(); i++) {
            var option = options.get(page * rows + i);
            option.setY(layout.contentTop() + i * 22);
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
        addRenderableWidget(MahjongButton.create(Component.translatable("settings.mchjong.reset_category"), ignored -> {
            settings.reset(TableSettings.Category.values()[tab]);
            if (tab == 3) RiichiAudio.settingsChanged();
            if (tab == 2) resetView();
            init();
        }).bounds(left, layout.footer(), column, 20).build());
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(left + column + 6, layout.footer(), column, 20).build().primary());
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

    private <E extends Enum<E>> MahjongButton addChoice(String key, E value, java.util.function.Consumer<E> choose) {
        Component label = Component.translatable(key);
        Component current = Component.translatable(key + "." + value.name().toLowerCase(Locale.ROOT));
        var values = value.getDeclaringClass().getEnumConstants();
        var captions = java.util.Arrays.stream(values).map(option -> (Component) Component.translatable(key + "." + option.name().toLowerCase(Locale.ROOT))).toList();
        var button = MahjongButton.create(Component.translatable("settings.mchjong.toggle", label, current),
            ignored -> minecraft.setScreen(new TableChoiceScreen(this, label, captions, value.ordinal(), index -> choose.accept(values[index]))))
            .bounds(layout.bodyLeft(), 0, layout.bodyWidth(), 20).build().option(label, current);
        options.add(button);
        return button;
    }

    private void addAction(String key, Runnable action) {
        Component label = Component.translatable(key);
        options.add(MahjongButton.create(label, ignored -> action.run()).bounds(layout.bodyLeft(), 0, layout.bodyWidth(), 20)
            .build().option(label, Component.literal("›")));
    }

    private void resetView() {
        if (TableChildScreen.root(parent) instanceof RiichiTableScreen table) table.resetView();
        else if (TableChildScreen.root(parent) instanceof McrTableScreen table) table.resetView();
        else if (TableChildScreen.root(parent) instanceof SichuanTableScreen table) table.resetView();
        else if (TableChildScreen.root(parent) instanceof TaiwanTableScreen table) table.resetView();
        else settings.camera().reset(settings.cameraDistance, settings.cameraHeight);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout.paint(graphics, font, title);
        if (pages > 1) graphics.drawCenteredString(font, (page + 1) + " / " + pages,
            layout.bodyLeft() + layout.bodyWidth() / 2, layout.paging() + 6, MahjongUi.MUTED);
        if (saveFailed) MahjongUi.text(graphics, font, Component.translatable("settings.mchjong.save_failed"),
            layout.bodyLeft(), layout.paging() - 10, layout.bodyWidth(), MahjongUi.NEGATIVE, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() {
        try {
            settings.save(TableSettings.configPath());
            var root = TableChildScreen.root(parent);
            boolean table = root instanceof RiichiTableScreen || root instanceof McrTableScreen || root instanceof SichuanTableScreen
                || root instanceof McrLobbyScreen || root instanceof SichuanLobbyScreen || root instanceof TaiwanTableScreen || root instanceof TaiwanLobbyScreen;
            minecraft.setScreen(minecraft.level == null && table ? null : parent);
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
