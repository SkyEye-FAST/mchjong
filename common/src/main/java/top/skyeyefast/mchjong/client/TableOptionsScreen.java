package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.Game;

/** Scope navigation. Administrator edits use the server's permission-checked world commands. */
public final class TableOptionsScreen extends Screen {
    private final TableScreen parent;
    private int tab = 1;
    private int page;
    private int pages = 1;
    private long revision = -1;
    private record Entry(Component label, boolean enabled, Runnable action) {}

    public TableOptionsScreen(TableScreen parent) {
        super(Component.translatable("settings.mchjong.scopes"));
        this.parent = parent;
    }
    public TableScreen tableScreen() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {}

    @Override protected void init() {
        clearWidgets();
        var view = parent.view();
        var room = parent.room();
        if (view == null || room == null) return;
        revision = view.revision();
        int span = Math.min(440, width - 24), left = (width - span) / 2;
        String[] scopes = {"world", "room", "personal"};
        for (int i = 0; i < scopes.length; i++) {
            int index = i;
            addRenderableWidget(MahjongButton.create(Component.translatable("settings.mchjong.scope." + scopes[i]), ignored -> {
                tab = index; page = 0; init();
            }).bounds(left + i * (span / 3), 36, span / 3 - 4, 20).build().selected(tab == i));
        }
        List<Entry> entries = new ArrayList<>();
        boolean host = view.viewerSeat() >= 0 && view.viewerSeat() == room.host() && view.exitVote() == null;
        boolean lobby = view.phase() == Game.Phase.LOBBY;
        if (tab == 0) {
            entries.add(new Entry(toggle("settings.mchjong.invitation_teleport", room.invitationTeleport()), canEditWorld(),
                () -> setWorld("invitationTeleport", !room.invitationTeleport())));
        } else if (tab == 1) {
            entries.add(new Entry(Component.translatable("settings.mchjong.hand_visibility",
                Component.translatable("settings.mchjong.hand_visibility." + view.handVisibility().name().toLowerCase(java.util.Locale.ROOT))),
                host && lobby, () -> {
                    var modes = top.skyeyefast.mchjong.engine.HandVisibility.values();
                    int direction = MahjongUi.shiftDown() ? -1 : 1;
                    parent.configureVisibility(modes[Math.floorMod(view.handVisibility().ordinal() + direction, modes.length)]);
                }));
            entries.add(new Entry(Component.translatable("room.mchjong.participants"), true,
                () -> minecraft.setScreen(new TableSeatsScreen(parent))));
            entries.add(new Entry(Component.translatable("rules.mchjong.title"), true,
                () -> minecraft.setScreen(new TableRulesScreen(parent, view))));
            entries.add(new Entry(Component.translatable("ui.mchjong.clock_settings"), host && lobby,
                () -> minecraft.setScreen(new TableClockScreen(parent, view.timeControl()))));
            entries.add(new Entry(Component.translatable("ui.mchjong.invite"), view.viewerSeat() >= 0 && lobby,
                () -> minecraft.setScreen(new TableInviteScreen(parent))));
        } else {
            entries.add(new Entry(Component.translatable("settings.mchjong.title"), true,
                () -> minecraft.setScreen(new TableSettingsScreen(parent))));
            entries.add(new Entry(Component.translatable("settings.mchjong.personal_presets"), true,
                () -> minecraft.setScreen(new PersonalPresetsScreen(this))));
        }
        int rows = Math.max(1, (height - 154) / 24);
        pages = Math.max(1, (entries.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pages - 1);
        for (int row = 0; row < rows && page * rows + row < entries.size(); row++) {
            var entry = entries.get(page * rows + row);
            var button = MahjongButton.create(entry.label(), ignored -> entry.action().run())
                .bounds(left, 84 + row * 24, span, 20).build();
            button.active = entry.enabled();
            button.setTooltip(Tooltip.create(tab == 0 && !canEditWorld() ? Component.translatable("settings.mchjong.world_locked") : entry.label()));
            addRenderableWidget(button);
        }
        if (pages > 1) {
            var previous = MahjongButton.create(Component.literal("<"), ignored -> { page--; init(); })
                .bounds(width / 2 - 65, height - 60, 30, 20).build();
            previous.active = page > 0;
            addRenderableWidget(previous);
            var next = MahjongButton.create(Component.literal(">"), ignored -> { page++; init(); })
                .bounds(width / 2 + 35, height - 60, 30, 20).build();
            next.active = page + 1 < pages;
            addRenderableWidget(next);
        }
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(left, height - 30, span, 20).build().primary());
    }

    private static Component toggle(String key, boolean enabled) {
        return Component.translatable("settings.mchjong.toggle", Component.translatable(key),
            Component.translatable(enabled ? "options.on" : "options.off"));
    }

    private boolean canEditWorld() {
        if (minecraft.getConnection() == null) return false;
        var command = minecraft.getConnection().getCommands().getRoot().getChild("mchjong");
        return command != null && command.getChild("world") != null;
    }

    private void setWorld(String setting, boolean enabled) {
        if (canEditWorld()) minecraft.getConnection().sendCommand("mchjong world " + setting + " " + enabled);
    }

    @Override public void tick() {
        var view = parent.view();
        if (view == null) { onClose(); return; }
        if (revision != view.revision()) init();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {
        MahjongUi.backdrop(graphics, width, height, 464);
        MahjongUi.text(graphics, font, title, 12, 16, width - 24, MahjongUi.TEXT, true);
        var view = parent.view();
        var room = parent.room();
        Component note = Component.translatable("settings.mchjong.personal_note");
        if (tab == 0) note = Component.translatable(canEditWorld() ? "settings.mchjong.world_admin" : "settings.mchjong.world_locked");
        if (tab == 1 && view != null && room != null) note = Component.translatable("room.mchjong.host",
            room.host() < 0 ? "—" : view.seats().get(room.host()).name());
        MahjongUi.text(graphics, font, note, 16, 66, width - 32, MahjongUi.MUTED, true);
        if (pages > 1) graphics.centeredText(font, (page + 1) + " / " + pages, width / 2, height - 54, MahjongUi.MUTED);
        super.extractRenderState(graphics, x, y, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
