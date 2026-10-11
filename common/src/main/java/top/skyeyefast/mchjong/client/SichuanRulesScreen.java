package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.SichuanPreset;
import top.skyeyefast.mchjong.engine.SichuanRoomSettings;
import top.skyeyefast.mchjong.engine.SichuanRuleOption;
import top.skyeyefast.mchjong.engine.SichuanRules;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.SichuanRulesPayload;

public final class SichuanRulesScreen extends Screen implements TableChildScreen {
    private final Screen parent;
    private final BlockPos pos;
    private TableRoomView baseline;
    private SichuanRoomSettings baselineSettings;
    private SichuanRules draft, pending;
    private final Map<SichuanRuleOption, String> numbers = new EnumMap<>(SichuanRuleOption.class);
    private final List<AbstractWidget> editors = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private SichuanRuleOption.Group group = SichuanRuleOption.Group.BASIC;
    private int page, pages, pendingTicks;
    private Button apply, reload, cancel;
    private SichuanPreset selectedPreset;
    private boolean rejected;
    private SettingsLayout layout;

    private record Label(Component text, Component description, int x, int y, int width) {}

    public SichuanRulesScreen(Screen parent, BlockPos pos, TableRoomView room, SichuanRoomSettings settings) {
        super(Component.translatable("sichuan.mchjong.rules.title"));
        this.parent = parent;
        this.pos = pos.immutable(); baseline = room; baselineSettings = settings; draft = settings.rules(); selectedPreset = SichuanPreset.match(draft);
    }
    public BlockPos tablePos() { return pos; }
    private top.skyeyefast.mchjong.world.MahjongTableBlockEntity table() {
        var client = net.minecraft.client.Minecraft.getInstance();
        return client.level != null && client.level.getBlockEntity(pos) instanceof top.skyeyefast.mchjong.world.MahjongTableBlockEntity table ? table : null;
    }
    private TableRoomView room() { return table() == null ? null : table().clientTableRoom(); }
    private SichuanRoomSettings settings() { return table() == null ? null : table().clientSichuanSettings(); }
    @Override public Screen parent() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    private boolean host() {
        var room = room();
        return room != null && room.tableId().equals(baseline.tableId()) && room.lobby() && room.exitVote() == null
            && room.viewerSeat() >= 0 && room.viewerSeat() == room.host();
    }
    private boolean stale() {
        var room = room();
        return room == null || !room.tableId().equals(baseline.tableId()) || !room.incarnation().equals(baseline.incarnation())
            || room.decision() != baseline.decision();
    }
    private boolean invalid() {
        for (var entry : numbers.entrySet()) {
            try { if (!entry.getKey().valid(Integer.parseInt(entry.getValue()))) return true; }
            catch (NumberFormatException failure) { return true; }
        }
        return false;
    }
    private void loadConfirmed() {
        var room = room(); var settings = settings();
        if (room == null || settings == null || !room.tableId().equals(baseline.tableId())) return;
        baseline = room; baselineSettings = settings; draft = settings.rules(); selectedPreset = SichuanPreset.match(draft); numbers.clear(); rejected = false;
    }

    @Override protected void init() {
        clearWidgets(); editors.clear(); labels.clear();
        var options = Arrays.stream(SichuanRuleOption.values()).filter(option -> option.group() == group).toList();
        layout = SettingsLayout.of(width, height, SichuanRuleOption.Group.values().length * 22, 24 + options.size() * 24 + 14);
        int span = layout.bodyWidth(), left = layout.bodyLeft();
        int contentLeft = left, contentWidth = span - 24;
        for (var category : SichuanRuleOption.Group.values()) {
            var text = Component.translatable(category.translationKey());
            addRenderableWidget(MahjongButton.create(text, ignored -> { group = category; page = 0; init(); })
                .bounds(layout.left(), layout.contentTop() + category.ordinal() * 22, layout.rail(), 20).tooltip(Tooltip.create(text)).build().navigation().selected(group == category));
        }
        int extras = group == SichuanRuleOption.Group.BASIC ? 2 : 0;
        int rows = Math.max(1, (layout.paging() - layout.contentTop() - 14) / 24);
        pages = Math.max(1, (options.size() + extras + rows - 1) / rows); page = Math.clamp(page, 0, pages - 1);
        for (int offset = 0; offset < rows && page * rows + offset < options.size() + extras; offset++) {
            int index = page * rows + offset;
            int top = layout.contentTop() + offset * 24;
            if (index < extras) {
                var matched = SichuanPreset.match(draft);
                var label = Component.translatable("rules.mchjong.preset", Component.translatable(matched == null ? "sichuan.mchjong.rules.custom" : matched.translationKey()));
                var button = addRenderableWidget(MahjongButton.create(index == 0 ? label : Component.translatable("rules.mchjong.restore_defaults"), ignored -> {
                    if (index == 0) minecraft.setScreen(new TableChoiceScreen(this, Component.translatable("rules.mchjong.choose_preset"),
                        Arrays.stream(SichuanPreset.values()).map(value -> (Component) Component.translatable(value.translationKey())).toList(),
                        selectedPreset == null ? -1 : selectedPreset.ordinal(), value -> {
                            var next = SichuanPreset.values()[value];
                            if (next != selectedPreset) { selectedPreset = next; draft = next.config(); numbers.clear(); rejected = false; }
                        }, value -> editReason()));
                    else if (selectedPreset != null) { draft = selectedPreset.config(); numbers.clear(); rejected = false; init(); }
                }).bounds(left, top, span, 20).build());
                if (index == 1) { if (selectedPreset != null) editors.add(button); else button.active = false; }
                continue;
            }
            var option = options.get(index - extras);
            var caption = Component.translatable(option.translationKey());
            var description = Component.translatable(option.descriptionKey());
            addRenderableWidget(RuleHelp.setting(this, option, caption, description, left + span - 20, top));
            labels.add(new Label(caption, description, contentLeft, top, contentWidth));
            if (option.toggle()) {
                boolean enabled = option.get(draft) != 0;
                editors.add(addRenderableWidget(MahjongButton.create(Component.translatable(enabled ? "rules.mchjong.yes" : "rules.mchjong.no"), ignored -> {
                    draft = option.with(draft, enabled ? 0 : 1); rejected = false; init();
                }).bounds(contentLeft + contentWidth - 56, top, 56, 20).tooltip(Tooltip.create(description)).build().selected(enabled)));
            } else {
                var field = new MahjongEditBox(font, contentLeft + contentWidth - 56, top, 56, 20, caption);
                field.setMaxLength(3); field.setFilter(value -> value.matches("[0-9]*"));
                field.setValue(numbers.getOrDefault(option, Integer.toString(option.get(draft))));
                field.setTooltip(Tooltip.create(Component.translatable("rules.mchjong.range", caption, option.min(), option.max(), 1)));
                field.setResponder(value -> {
                    numbers.put(option, value);
                    try {
                        int number = Integer.parseInt(value);
                        if (option.valid(number)) draft = option.with(draft, number);
                    } catch (NumberFormatException ignored) {}
                    rejected = false; updateControls();
                });
                editors.add(addRenderableWidget(field));
            }
        }
        var previous = addRenderableWidget(MahjongButton.create(Component.literal("‹"), ignored -> { page--; init(); })
            .bounds(left, layout.paging(), 30, 20).tooltip(Tooltip.create(Component.translatable("rules.mchjong.previous"))).build());
        previous.active = page > 0;
        var next = addRenderableWidget(MahjongButton.create(Component.literal("›"), ignored -> { page++; init(); })
            .bounds(left + span - 30, layout.paging(), 30, 20).tooltip(Tooltip.create(Component.translatable("rules.mchjong.next"))).build());
        next.active = page + 1 < pages;
        int buttonWidth = (layout.span() - 4) / 2;
        reload = addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.reload"), ignored -> { loadConfirmed(); init(); })
            .bounds(left + 34, layout.paging(), span - 68, 20).build());
        apply = addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.apply"), ignored -> submit())
            .bounds(layout.left(), layout.footer(), buttonWidth, 20).build().primary());
        cancel = addRenderableWidget(MahjongButton.create(Component.translatable("gui.cancel"), ignored -> onClose())
            .bounds(layout.left() + buttonWidth + 4, layout.footer(), buttonWidth, 20).build());
        updateControls();
    }
    private Component editReason() {
        if (minecraft.getConnection() == null) return Component.translatable("rules.mchjong.disconnected");
        if (pending != null) return Component.translatable("rules.mchjong.pending");
        if (stale()) return Component.translatable("rules.mchjong.stale");
        if (!room().lobby() || room().exitVote() != null) return Component.translatable("rules.mchjong.phase_locked");
        if (!host()) return Component.translatable("rules.mchjong.host_only");
        return settings() == null || !settings().rulesEditable() ? Component.translatable("sichuan.mchjong.rules.locked") : null;
    }
    private void updateControls() {
        var reason = editReason();
        editors.forEach(widget -> { widget.active = reason == null; if (widget instanceof MahjongEditBox field) field.setEditable(reason == null); if (reason != null) widget.setTooltip(Tooltip.create(reason)); });
        if (apply != null) apply.active = reason == null && !invalid() && !draft.equals(baselineSettings.rules());
        if (reload != null) { reload.active = pending == null; reload.visible = stale() || rejected; }
        if (cancel != null) cancel.setMessage(Component.translatable(rejected || editReason() != null ? "gui.back" : "gui.cancel"));
    }
    private void submit() {
        updateControls();
        if (apply == null || !apply.active || minecraft.getConnection() == null) return;
        pending = draft; pendingTicks = 0;
        minecraft.getConnection().send(PayloadPackets.serverbound(new SichuanRulesPayload(tablePos(), baseline.tableId(),
            baseline.incarnation(), baseline.decision(), pending)));
        updateControls();
    }
    public void receivedView(boolean reply) {
        var settings = settings();
        if (pending != null && reply) {
            boolean accepted = settings != null && settings.rules().equals(pending) && !staleIdentity();
            pending = null;
            if (accepted) { onClose(); return; }
            else rejected = true;
        }
        updateControls();
    }
    private boolean staleIdentity() {
        var room = room();
        return room == null || !room.tableId().equals(baseline.tableId()) || !room.incarnation().equals(baseline.incarnation());
    }
    @Override public void tick() {
        if (minecraft.level == null) { minecraft.setScreen(null); return; }
        if (pending != null && ++pendingTicks > 200) { pending = null; rejected = true; }
        updateControls();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        updateControls();
        int span = layout.bodyWidth(), left = layout.bodyLeft();
        layout.paint(graphics, font, title);
        for (var label : labels) {
            MahjongUi.text(graphics, font, label.text(), label.x(), label.y() + 6, label.width() - 64, MahjongUi.TEXT, false);
            if (mouseX >= label.x() && mouseX < label.x() + label.width() - 64 && mouseY >= label.y() && mouseY < label.y() + 20)
                graphics.setTooltipForNextFrame(font, label.text().copy().append("\n").append(label.description()), mouseX, mouseY);
        }
        if (!reload.visible) graphics.centeredText(font, (page + 1) + " / " + pages, left + span / 2, layout.paging() + 6, MahjongUi.MUTED);
        Component notice = rejected ? Component.translatable("rules.mchjong.rejected") : editReason();
        if (notice == null && invalid()) notice = Component.translatable("rules.mchjong.invalid");
        if (notice != null) MahjongUi.text(graphics, font, notice, left, layout.paging() - 14, span,
            rejected || stale() || invalid() ? MahjongUi.NEGATIVE : MahjongUi.MUTED, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() {
        if (minecraft.level == null || room() == null) { minecraft.setScreen(null); return; }
        var root = TableChildScreen.root(parent);
        if (!room().lobby() && root instanceof SichuanLobbyScreen) {
            var view = table().clientSichuanView();
            minecraft.setScreen(view != null && view.game().result() != null ? new SichuanResultsScreen(pos, false) : new SichuanTableScreen(pos));
        } else if (room().lobby() && !(root instanceof SichuanLobbyScreen)) minecraft.setScreen(new SichuanLobbyScreen(pos));
        else minecraft.setScreen(parent);
    }
}
