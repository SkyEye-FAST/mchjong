package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
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
    private final SichuanLobbyScreen table;
    private TableRoomView baseline;
    private SichuanRoomSettings baselineSettings;
    private SichuanRules draft, pending;
    private final Map<SichuanRuleOption, String> numbers = new EnumMap<>(SichuanRuleOption.class);
    private final List<AbstractWidget> editors = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private SichuanRuleOption.Group group = SichuanRuleOption.Group.BASIC;
    private int page, pages, pendingTicks;
    private Button apply, reload;
    private boolean rejected;
    private SettingsLayout layout;

    private record Label(Component text, Component description, int x, int y, int width) {}

    public SichuanRulesScreen(Screen parent, TableRoomView room, SichuanRoomSettings settings) {
        super(Component.translatable("sichuan.mchjong.rules.title"));
        this.parent = parent;
        this.table = (SichuanLobbyScreen) TableChildScreen.root(parent); baseline = room; baselineSettings = settings; draft = settings.rules();
    }
    public BlockPos tablePos() { return table.tablePos(); }
    @Override public Screen parent() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    private boolean host() {
        var room = table.room();
        return room != null && room.tableId().equals(baseline.tableId()) && room.lobby() && room.exitVote() == null
            && room.viewerSeat() >= 0 && room.viewerSeat() == room.host();
    }
    private boolean stale() {
        var room = table.room();
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
        var room = table.room(); var settings = table.roomSettings();
        if (room == null || settings == null || !room.tableId().equals(baseline.tableId())) return;
        baseline = room; baselineSettings = settings; draft = settings.rules(); numbers.clear(); rejected = false;
    }

    @Override protected void init() {
        clearWidgets(); editors.clear(); labels.clear();
        layout = SettingsLayout.of(width, height);
        int span = layout.bodyWidth(), left = layout.bodyLeft();
        var matched = SichuanPreset.match(draft);
        var presets = SichuanPreset.values();
        var label = Component.translatable("rules.mchjong.preset", Component.translatable(matched == null
            ? "sichuan.mchjong.rules.custom" : matched.translationKey()));
        var preset = addRenderableWidget(MahjongButton.create(label, ignored -> minecraft.setScreen(new TableChoiceScreen(this,
            Component.translatable("rules.mchjong.mode.preset"), Arrays.stream(presets).map(value -> (Component) Component.translatable(value.translationKey())).toList(),
            matched == null ? -1 : matched.ordinal(), index -> { draft = presets[index].config(); numbers.clear(); rejected = false; })))
            .bounds(left, layout.contentTop(), span, 20).tooltip(Tooltip.create(label)).build());
        editors.add(preset);
        int contentLeft = left, contentWidth = span - 24;
        for (var category : SichuanRuleOption.Group.values()) {
            var text = Component.translatable(category.translationKey());
            addRenderableWidget(MahjongButton.create(text, ignored -> { group = category; page = 0; init(); })
                .bounds(layout.left(), layout.contentTop() + category.ordinal() * 22, layout.rail(), 20).tooltip(Tooltip.create(text)).build().navigation().selected(group == category));
        }
        var options = Arrays.stream(SichuanRuleOption.values()).filter(option -> option.group() == group).toList();
        int rows = Math.max(1, (layout.height() - 136) / 24);
        pages = Math.max(1, (options.size() + rows - 1) / rows); page = Math.clamp(page, 0, pages - 1);
        for (int offset = 0; offset < rows && page * rows + offset < options.size(); offset++) {
            var option = options.get(page * rows + offset);
            int top = layout.top() + 68 + offset * 24;
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
        int buttonWidth = (span - 8) / 3;
        reload = addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.reload"), ignored -> {
            loadConfirmed(); init();
        }).bounds(left, layout.footer(), buttonWidth, 20).build());
        apply = addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.apply"), ignored -> submit())
            .bounds(left + buttonWidth + 4, layout.footer(), buttonWidth, 20).build().primary());
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.cancel"), ignored -> onClose())
            .bounds(left + 2 * (buttonWidth + 4), layout.footer(), buttonWidth, 20).build());
        updateControls();
    }
    private void updateControls() {
        boolean editable = host() && !stale() && pending == null && table.roomSettings() != null && table.roomSettings().rulesEditable();
        editors.forEach(widget -> widget.active = editable);
        if (apply != null) apply.active = editable && !invalid() && !draft.equals(baselineSettings.rules());
        if (reload != null) reload.active = pending == null;
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
        var settings = table.roomSettings();
        if (pending != null && reply) {
            boolean accepted = settings != null && settings.rules().equals(pending) && !staleIdentity();
            pending = null;
            if (accepted) { loadConfirmed(); init(); }
            else rejected = true;
        } else if (pending == null && draft.equals(baselineSettings.rules()) && numbers.isEmpty()) {
            loadConfirmed(); init();
        }
        updateControls();
    }
    private boolean staleIdentity() {
        var room = table.room();
        return room == null || !room.tableId().equals(baseline.tableId()) || !room.incarnation().equals(baseline.incarnation());
    }
    @Override public void tick() {
        if (minecraft.level == null) { minecraft.setScreen(null); return; }
        if (pending != null && ++pendingTicks > 200) { pending = null; rejected = true; }
        updateControls();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateControls();
        int span = layout.bodyWidth(), left = layout.bodyLeft();
        layout.paint(graphics, font, title);
        for (var label : labels) {
            MahjongUi.text(graphics, font, label.text(), label.x(), label.y() + 6, label.width() - 64, MahjongUi.TEXT, false);
            if (mouseX >= label.x() && mouseX < label.x() + label.width() - 64 && mouseY >= label.y() && mouseY < label.y() + 20)
                setTooltipForNextRenderPass(label.text().copy().append(Component.literal(String.valueOf((char)10))).append(label.description()));
        }
        graphics.drawCenteredString(font, (page + 1) + " / " + pages, left + span / 2, layout.paging() + 6, MahjongUi.MUTED);
        String notice = pending != null ? "rules.mchjong.pending" : rejected ? "rules.mchjong.rejected"
            : !host() ? "rules.mchjong.read_only" : table.roomSettings() == null || !table.roomSettings().rulesEditable() ? "sichuan.mchjong.rules.locked"
            : stale() ? "rules.mchjong.stale" : invalid() ? "rules.mchjong.invalid" : null;
        if (notice != null) MahjongUi.text(graphics, font, Component.translatable(notice), left, layout.paging() - 14, span,
            rejected || stale() || invalid() ? MahjongUi.NEGATIVE : MahjongUi.MUTED, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
