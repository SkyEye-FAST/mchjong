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

public final class SichuanRulesScreen extends Screen {
    private final SichuanLobbyScreen parent;
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

    private record Label(Component text, Component description, int x, int y, int width) {}

    public SichuanRulesScreen(SichuanLobbyScreen parent, TableRoomView room, SichuanRoomSettings settings) {
        super(Component.translatable("sichuan.mchjong.rules.title"));
        this.parent = parent; baseline = room; baselineSettings = settings; draft = settings.rules();
    }
    public BlockPos tablePos() { return parent.tablePos(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    private boolean host() {
        var room = parent.room();
        return room != null && room.tableId().equals(baseline.tableId()) && room.lobby() && room.exitVote() == null
            && room.viewerSeat() >= 0 && room.viewerSeat() == room.host();
    }
    private boolean stale() {
        var room = parent.room();
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
        var room = parent.room(); var settings = parent.roomSettings();
        if (room == null || settings == null || !room.tableId().equals(baseline.tableId())) return;
        baseline = room; baselineSettings = settings; draft = settings.rules(); numbers.clear(); rejected = false;
    }

    @Override protected void init() {
        clearWidgets(); editors.clear(); labels.clear();
        int span = Math.min(540, width - 24), left = (width - span) / 2;
        var matched = SichuanPreset.match(draft);
        var nextPreset = SichuanPreset.values()[matched == null ? 0 : (matched.ordinal() + 1) % SichuanPreset.values().length];
        var preset = addRenderableWidget(MahjongButton.create(Component.translatable(nextPreset.translationKey()), ignored -> {
            var next = nextPreset;
            draft = next.config(); numbers.clear(); rejected = false; init();
        }).bounds(left + span - 108, 34, 108, 20).build());
        editors.add(preset);
        int rail = Math.min(100, span / 3), contentLeft = left + rail + 10, contentWidth = span - rail - 10;
        for (var category : SichuanRuleOption.Group.values()) {
            var text = Component.translatable(category.translationKey());
            addRenderableWidget(MahjongButton.create(text, ignored -> { group = category; page = 0; init(); })
                .bounds(left, 72 + category.ordinal() * 26, rail, 20).tooltip(Tooltip.create(text)).build().selected(group == category));
        }
        var options = Arrays.stream(SichuanRuleOption.values()).filter(option -> option.group() == group).toList();
        int rows = Math.max(1, (height - 158) / 54);
        pages = Math.max(1, (options.size() + rows - 1) / rows); page = Math.clamp(page, 0, pages - 1);
        for (int offset = 0; offset < rows && page * rows + offset < options.size(); offset++) {
            var option = options.get(page * rows + offset);
            int top = 76 + offset * 54;
            var caption = Component.translatable(option.translationKey());
            var description = Component.translatable(option.descriptionKey());
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
            .bounds(width / 2 - 75, height - 59, 30, 20).tooltip(Tooltip.create(Component.translatable("rules.mchjong.previous"))).build());
        previous.active = page > 0;
        var next = addRenderableWidget(MahjongButton.create(Component.literal("›"), ignored -> { page++; init(); })
            .bounds(width / 2 + 45, height - 59, 30, 20).tooltip(Tooltip.create(Component.translatable("rules.mchjong.next"))).build());
        next.active = page + 1 < pages;
        int buttonWidth = (span - 8) / 3;
        reload = addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.reload"), ignored -> {
            loadConfirmed(); init();
        }).bounds(left, height - 30, buttonWidth, 20).build());
        apply = addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.apply"), ignored -> submit())
            .bounds(left + buttonWidth + 4, height - 30 - MahjongUi.STEP, buttonWidth, 20).build().primary());
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.cancel"), ignored -> onClose())
            .bounds(left + 2 * (buttonWidth + 4), height - 30, buttonWidth, 20).build());
        updateControls();
    }
    private void updateControls() {
        boolean editable = host() && !stale() && pending == null && parent.roomSettings() != null && parent.roomSettings().rulesEditable();
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
        var settings = parent.roomSettings();
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
        var room = parent.room();
        return room == null || !room.tableId().equals(baseline.tableId()) || !room.incarnation().equals(baseline.incarnation());
    }
    @Override public void tick() {
        if (minecraft.level == null) { minecraft.setScreen(null); return; }
        if (pending != null && ++pendingTicks > 200) { pending = null; rejected = true; }
        updateControls();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateControls();
        int span = Math.min(540, width - 24), left = (width - span) / 2;
        MahjongUi.backdrop(graphics, width, height, 580);
        MahjongUi.titlePlaque(graphics, font, Component.translatable("settings.mchjong.scope.room").append(" › ").append(title),
            left - MahjongUi.OFFSET, 7, span);
        var matched = SichuanPreset.match(draft);
        MahjongUi.text(graphics, font, Component.translatable("rules.mchjong.preset",
            Component.translatable(matched == null ? "sichuan.mchjong.rules.custom" : matched.translationKey())), left, 40, span - 116, MahjongUi.TEXT, false);
        for (var label : labels) {
            MahjongUi.text(graphics, font, label.text(), label.x(), label.y() + 6, label.width() - 64, MahjongUi.TEXT, false);
            graphics.drawWordWrap(font, label.description(), label.x(), label.y() + 25, label.width(), MahjongUi.MUTED);
            if (mouseX >= label.x() && mouseX < label.x() + label.width() - 64 && mouseY >= label.y() && mouseY < label.y() + 20)
                setTooltipForNextRenderPass(label.text());
        }
        graphics.drawCenteredString(font, (page + 1) + " / " + pages, width / 2, height - 53, MahjongUi.MUTED);
        String notice = pending != null ? "rules.mchjong.pending" : rejected ? "rules.mchjong.rejected"
            : !host() ? "rules.mchjong.read_only" : parent.roomSettings() == null || !parent.roomSettings().rulesEditable() ? "sichuan.mchjong.rules.locked"
            : stale() ? "rules.mchjong.stale" : invalid() ? "rules.mchjong.invalid" : "sichuan.mchjong.rules.apply_note";
        MahjongUi.text(graphics, font, Component.translatable(notice), left, height - 82, span,
            rejected || stale() || invalid() ? MahjongUi.NEGATIVE : MahjongUi.MUTED, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
