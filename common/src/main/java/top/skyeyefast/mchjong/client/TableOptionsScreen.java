package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Scope navigation. Administrator edits use the server's permission-checked world commands. */
public final class TableOptionsScreen extends Screen implements TableChildScreen {
    private final Screen parent;
    private final net.minecraft.core.BlockPos pos;
    private int tab = 1;
    private int page;
    private int pages = 1;
    private long revision = -1;
    private top.skyeyefast.mchjong.world.WorldSettings.Policy policy;
    private SettingsLayout layout;
    private record Entry(Component label, Component value, Boolean checked, boolean enabled, Runnable action) {
        Entry(Component label, boolean enabled, Runnable action) { this(label, Component.literal("›"), null, enabled, action); }
        static Entry toggle(String key, boolean checked, boolean enabled, Runnable action) {
            return new Entry(Component.translatable(key), Component.translatable(checked ? "options.on" : "options.off"), checked, enabled, action);
        }
        static Entry choice(String key, Component value, boolean enabled, Runnable action) {
            return new Entry(Component.translatable(key + ".label"), value, null, enabled, action);
        }
        Component message() {
            return value.getString().equals("›") ? label : Component.translatable("settings.mchjong.toggle", label,
                checked == null ? value : Component.translatable(checked ? "options.on" : "options.off"));
        }
    }

    public TableOptionsScreen(Screen parent, net.minecraft.core.BlockPos pos) {
        super(Component.translatable("settings.mchjong.scopes"));
        this.parent = parent;
        this.pos = pos.immutable();
    }
    @Override public Screen parent() { return parent; }
    public net.minecraft.core.BlockPos tablePos() { return pos; }
    private top.skyeyefast.mchjong.world.MahjongTableBlockEntity table() {
        return minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof top.skyeyefast.mchjong.world.MahjongTableBlockEntity table ? table : null;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partialTick) {}

    @Override protected void init() {
        clearWidgets();
        var room = table() == null ? null : table().clientTableRoom();
        var world = table() == null ? null : table().clientWorldPolicy();
        if (room == null || world == null) return;
        revision = room.revision();
        policy = world;
        layout = SettingsLayout.of(width, height);
        int span = layout.bodyWidth(), left = layout.bodyLeft();
        String[] scopes = {"world", "room", "personal"};
        for (int i = 0; i < scopes.length; i++) {
            int index = i;
            addRenderableWidget(MahjongButton.create(Component.translatable("settings.mchjong.scope." + scopes[i]), ignored -> {
                tab = index; page = 0; init();
            }).bounds(layout.left(), layout.contentTop() + i * 24, layout.rail(), 22).build().navigation().selected(tab == i));
        }
        List<Entry> entries = new ArrayList<>();
        if (tab == 0) {
            boolean edit = canEditWorld();
            entries.add(Entry.toggle("settings.mchjong.invitations_enabled", world.invitationsEnabled(), edit,
                () -> setWorld("invitationsEnabled", !world.invitationsEnabled())));
            entries.add(Entry.toggle("settings.mchjong.invitation_teleport", world.invitationTeleport(), edit,
                () -> setWorld("invitationTeleport", !world.invitationTeleport())));
            entries.add(Entry.toggle("settings.mchjong.spectating_enabled", world.spectatingEnabled(), edit,
                () -> setWorld("spectatingEnabled", !world.spectatingEnabled())));
            entries.add(Entry.choice("settings.mchjong.spectator_hand_visibility",
                Component.translatable("settings.mchjong.spectator_hand_visibility."
                    + world.spectatorHandVisibility().name().toLowerCase(java.util.Locale.ROOT)), edit, () -> {
                        var modes = top.skyeyefast.mchjong.engine.SpectatorHandVisibility.values();
                        int direction = Screen.hasShiftDown() ? -1 : 1;
                        var next = modes[Math.floorMod(world.spectatorHandVisibility().ordinal() + direction, modes.length)];
                        setWorldValue("spectatorHandVisibility", next.name().toLowerCase(java.util.Locale.ROOT));
                    }));
            entries.add(Entry.toggle("settings.mchjong.allow_convenience_hints", world.allowConvenienceHints(), edit,
                () -> setWorld("allowConvenienceHints", !world.allowConvenienceHints())));
            entries.add(Entry.toggle("settings.mchjong.allow_experience_rewards", world.allowExperienceRewards(), edit,
                () -> setWorld("allowExperienceRewards", !world.allowExperienceRewards())));
            entries.add(Entry.toggle("settings.mchjong.deduct_negative_experience", world.deductNegativeExperience(), edit,
                () -> setWorld("deductNegativeExperience", !world.deductNegativeExperience())));
            entries.add(Entry.choice("settings.mchjong.max_experience_change", Component.literal(Integer.toString(world.maxExperienceChange())), edit,
                () -> setWorldValue("maxExperienceChange", Integer.toString(nextExperienceLimit(
                    world.maxExperienceChange(), Screen.hasShiftDown() ? -1 : 1)))));
            entries.add(Entry.toggle("settings.mchjong.replays_enabled", world.replaysEnabled(), edit,
                () -> setWorld("replaysEnabled", !world.replaysEnabled())));
            entries.add(Entry.toggle("settings.mchjong.allow_bots", world.allowBots(), edit,
                () -> setWorld("allowBots", !world.allowBots())));
            entries.add(Entry.toggle("settings.mchjong.allow_companion_players", world.allowCompanionPlayers(), edit,
                () -> setWorld("allowCompanionPlayers", !world.allowCompanionPlayers())));
            entries.add(Entry.toggle("settings.mchjong.allow_custom_rules", world.allowCustomRules(), edit,
                () -> setWorld("allowCustomRules", !world.allowCustomRules())));
            Component forced = world.forcedPreset() == null ? Component.translatable("settings.mchjong.none")
                : Component.translatable(world.forcedPreset().translationKey());
            entries.add(Entry.choice("settings.mchjong.forced_preset", forced, edit, () -> {
                var next = nextPreset(world.forcedPreset(), Screen.hasShiftDown() ? -1 : 1);
                setWorldValue("forcedPreset", next == null ? "none" : next.name().toLowerCase(java.util.Locale.ROOT));
            }));
        } else if (tab == 1) {
            for (var option : RoomLobby.options(this, pos, room))
                entries.add(new Entry(option.label(), option.value(), option.checked(), option.enabled(), option.action()));
        } else {
            entries.add(new Entry(Component.translatable("settings.mchjong.title"), true,
                () -> minecraft.setScreen(new TableSettingsScreen(this))));
            entries.add(new Entry(Component.translatable("settings.mchjong.personal_presets"), true,
                () -> minecraft.setScreen(new PersonalPresetsScreen(this))));
        }
        int rows = layout.rows();
        pages = Math.max(1, (entries.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pages - 1);
        for (int row = 0; row < rows && page * rows + row < entries.size(); row++) {
            var entry = entries.get(page * rows + row);
            var button = MahjongButton.create(entry.message(), ignored -> entry.action().run())
                .bounds(left, layout.contentTop() + row * 22, span, 20).build().option(entry.label(), entry.value());
            if (entry.checked() != null) button.checked(entry.checked());
            button.active = entry.enabled();
            Component help = tab == 0 && !canEditWorld() ? Component.translatable("settings.mchjong.world_locked")
                : entry.label().getString().equals(Component.translatable("settings.mchjong.convenience_hints").getString())
                    ? entry.message().copy().append("\n").append(Component.translatable("settings.mchjong.convenience_hints_help"))
                    : entry.message();
            button.setTooltip(Tooltip.create(help));
            addRenderableWidget(button);
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
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(left + span - Math.min(120, span), layout.footer(), Math.min(120, span), 20).build().primary());
    }

    private boolean canEditWorld() {
        if (minecraft.getConnection() == null) return false;
        var command = minecraft.getConnection().getCommands().getRoot().getChild("mchjong");
        return command != null && command.getChild("world") != null;
    }

    private void setWorld(String setting, boolean enabled) {
        setWorldValue(setting, Boolean.toString(enabled));
    }

    private void setWorldValue(String setting, String value) {
        if (canEditWorld()) minecraft.getConnection().sendCommand("mchjong world " + setting + " " + value);
    }

    private static int nextExperienceLimit(int current, int direction) {
        int[] values = {0, 500, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000, 100_000};
        if (direction > 0) {
            for (int value : values) if (value > current) return value;
            return values[0];
        }
        for (int index = values.length - 1; index >= 0; index--) if (values[index] < current) return values[index];
        return values[values.length - 1];
    }

    private static top.skyeyefast.mchjong.engine.RiichiPreset nextPreset(top.skyeyefast.mchjong.engine.RiichiPreset current, int direction) {
        var values = top.skyeyefast.mchjong.engine.RiichiPreset.values();
        int slot = current == null ? 0 : current.ordinal() + 1;
        slot = Math.floorMod(slot + direction, values.length + 1);
        return slot == 0 ? null : values[slot - 1];
    }

    @Override public void tick() {
        var room = table() == null ? null : table().clientTableRoom();
        if (room == null) { onClose(); return; }
        if (revision != room.revision() || !java.util.Objects.equals(policy, table().clientWorldPolicy())) init();
    }

    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        if (layout == null) return;
        layout.paint(graphics, font, title);
        if (pages > 1) graphics.drawCenteredString(font, (page + 1) + " / " + pages,
            layout.bodyLeft() + layout.bodyWidth() / 2, layout.paging() + 6, MahjongUi.MUTED);
        super.render(graphics, x, y, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
