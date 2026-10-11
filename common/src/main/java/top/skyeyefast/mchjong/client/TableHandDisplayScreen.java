package top.skyeyefast.mchjong.client;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import top.skyeyefast.mchjong.engine.PlayerHandVisibility;
import top.skyeyefast.mchjong.network.RiichiControlPayload;

/** Independent room visibility and physical exposure, using confirmed room values. */
final class TableHandDisplayScreen extends Screen implements TableChildScreen {
    private final Screen parent;
    private final RiichiTableScreen table;
    private SettingsLayout layout;
    private List<FormattedCharSequence> description = List.of();
    private long revision = -1;
    TableHandDisplayScreen(Screen parent, RiichiTableScreen table) {
        super(Component.translatable("settings.mchjong.hand_display"));
        this.parent = parent; this.table = table;
    }
    @Override public Screen parent() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g) {}
    @Override protected void init() {
        clearWidgets();
        var room = table.room(); var settings = table.roomSettings();
        if (room == null || settings == null) return;
        revision = room.revision(); layout = SettingsLayout.of(width, height, 0, 96);
        description = font.split(Component.translatable("settings.mchjong.open_hands.description"), layout.span());
        boolean editable = editReason() == null;
        var values = PlayerHandVisibility.values();
        var visibility = addRenderableWidget(MahjongButton.create(Component.translatable("settings.mchjong.toggle",
            Component.translatable("settings.mchjong.hand_visibility.label"), visibilityName(settings.playerHandVisibility())), ignored ->
                minecraft.setScreen(new TableChoiceScreen(this, Component.translatable("settings.mchjong.hand_visibility.label"),
                    Arrays.stream(values).map(TableHandDisplayScreen::visibilityName).toList(), settings.playerHandVisibility().ordinal(),
                    i -> table.configureVisibility(values[i]), i -> editReason())))
            .bounds(layout.left(), layout.contentTop(), layout.span(), 20).build());
        visibility.active = editable;
        if (!editable) visibility.setTooltip(Tooltip.create(editReason()));
        var open = addRenderableWidget(MahjongButton.create(Component.translatable("settings.mchjong.toggle",
            Component.translatable("settings.mchjong.open_hands"), Component.translatable(settings.openHands() ? "options.on" : "options.off")), ignored ->
                table.control(room, RiichiControlPayload.Operation.OPEN_HANDS, room.decision(), !settings.openHands()))
            .bounds(layout.left(), layout.contentTop() + 24, layout.span(), 20)
            .tooltip(Tooltip.create(Component.translatable("settings.mchjong.open_hands.description"))).build().checked(settings.openHands()));
        open.active = editable;
        if (!editable) open.setTooltip(Tooltip.create(editReason()));
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.back"), ignored -> onClose())
            .bounds(layout.left(), layout.footer(), layout.span(), 20).build());
    }
    private static Component visibilityName(PlayerHandVisibility value) {
        return Component.translatable("settings.mchjong.hand_visibility." + value.name().toLowerCase(java.util.Locale.ROOT));
    }
    private Component editReason() {
        if (minecraft.getConnection() == null) return Component.translatable("rules.mchjong.disconnected");
        var room = table.room();
        if (room == null || !room.lobby() || room.exitVote() != null) return Component.translatable("rules.mchjong.phase_locked");
        return RoomLobby.host(room) ? null : Component.translatable("rules.mchjong.host_only");
    }
    @Override public void tick() {
        if (table.room() == null) { onClose(); return; }
        if (revision != table.room().revision()) init();
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        if (layout != null) {
            layout.paint(g, font, title);
            for (int i = 0; i < description.size(); i++)
                g.drawString(font, description.get(i), layout.left(), layout.contentTop() + 52 + i * 12, MahjongUi.MUTED, false);
        }
        super.render(g, x, y, partial);
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
