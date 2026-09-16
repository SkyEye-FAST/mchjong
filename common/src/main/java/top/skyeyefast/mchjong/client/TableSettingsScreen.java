package top.skyeyefast.mchjong.client;

import java.io.IOException;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
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
        for (int i = 0; i < 3; i++) {
            final int index = i;
            var button = Button.builder(Component.translatable("settings.mchjong.tab." + i), ignored -> {
                tab = index; page = 0; init();
            }).bounds(left + i * (span / 3), 33, span / 3 - 4, 20).build();
            button.active = tab != i;
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
                addToggle(left + (n % 2) * (column + 6), 65 + (n / 2) * 26, column,
                    information.key(), settings.show(information), () -> settings.toggle(information));
            }
            if (values.length > perPage) {
                var previous = Button.builder(Component.literal("<"), ignored -> { page--; init(); })
                    .bounds(width / 2 - 66, height - 61, 30, 20).build();
                previous.active = page > 0;
                addRenderableWidget(previous);
                var next = Button.builder(Component.literal(">"), ignored -> { page++; init(); })
                    .bounds(width / 2 + 36, height - 61, 30, 20).build();
                next.active = (page + 1) * perPage < values.length;
                addRenderableWidget(next);
            }
        } else if (tab == 1) {
            addRenderableWidget(Button.builder(value("settings.mchjong.discard", settings.discardMode), ignored -> {
                var values = TableSettings.DiscardMode.values();
                settings.discardMode = values[(settings.discardMode.ordinal() + 1) % values.length]; init();
            }).bounds(left, 65, span, 20).build());
            addRenderableWidget(Button.builder(value("settings.mchjong.guides", settings.guideLines), ignored -> {
                var values = TableSettings.GuideLines.values();
                settings.guideLines = values[(settings.guideLines.ordinal() + 1) % values.length]; init();
            }).bounds(left, 91, span, 20).build());
            addToggle(left, 117, column, "settings.mchjong.action_tiles", settings.actionTiles, () -> settings.actionTiles = !settings.actionTiles);
            addToggle(left + column + 6, 117, column, "settings.mchjong.highlight", settings.highlightTiles, () -> settings.highlightTiles = !settings.highlightTiles);
        } else {
            addRenderableWidget(new CameraSlider(left, 65, span, true));
            addRenderableWidget(new CameraSlider(left, 91, span, false));
            addRenderableWidget(Button.builder(Component.translatable("settings.mchjong.reset_view"), ignored -> parent.resetView())
                .bounds(left, 117, span, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("settings.mchjong.reset"), ignored -> {
            settings.reset(); parent.resetView(); init();
        }).bounds(left, height - 30, column, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(left + column + 6, height - 30, column, 20).build());
    }

    private void addToggle(int x, int y, int w, String key, boolean enabled, Runnable toggle) {
        Component label = Component.translatable("settings.mchjong.toggle", Component.translatable(key),
            Component.translatable(enabled ? "options.on" : "options.off"));
        var button = Button.builder(label, ignored -> { toggle.run(); init(); }).bounds(x, y, w, 20).build();
        button.setTooltip(Tooltip.create(label));
        addRenderableWidget(button);
    }

    private static Component value(String key, Enum<?> value) {
        return Component.translatable("settings.mchjong.toggle", Component.translatable(key),
            Component.translatable(key + "." + value.name().toLowerCase(Locale.ROOT)));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xe817272c);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xfff0dec1);
        if (tab == 0) {
            int pages = (TableSettings.Information.values().length - 1) / (Math.max(1, (height - 130) / 26) * 2) + 1;
            if (pages > 1) graphics.drawCenteredString(font, (page + 1) + " / " + pages, width / 2, height - 55, 0xffd1e4d9);
        }
        if (saveFailed) graphics.drawCenteredString(font, Component.translatable("settings.mchjong.save_failed"), width / 2, height - 76, 0xffffa09a);
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

    private final class CameraSlider extends AbstractSliderButton {
        private final boolean distance;
        CameraSlider(int x, int y, int w, boolean distance) {
            super(x, y, w, 20, Component.empty(), distance ? (settings.cameraDistance - 2.4) / 1.6 : (settings.cameraHeight - 1.7) / 1.3);
            this.distance = distance;
            updateMessage();
        }
        @Override protected void updateMessage() {
            setMessage(Component.translatable(distance ? "settings.mchjong.camera_distance" : "settings.mchjong.camera_height",
                String.format(Locale.ROOT, "%.2f", distance ? settings.cameraDistance : settings.cameraHeight)));
        }
        @Override protected void applyValue() {
            if (distance) settings.cameraDistance = 2.4 + value * 1.6;
            else settings.cameraHeight = 1.7 + value * 1.3;
        }
    }
}
