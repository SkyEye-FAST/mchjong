package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.*;
import top.skyeyefast.mchjong.network.*;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** A single rule and payment draft retained throughout category and child-page navigation. */
public final class TaiwanRulesScreen extends Screen implements TableChildScreen {
    private final Screen parent;
    private final BlockPos pos;
    private TableRoomView baseline;
    private TaiwanGameState.Rules original, draft, pending;
    private int category, page, pages, pendingTicks;
    private boolean rejected;
    private final String[] paymentText = new String[4];
    private static final String[] PAYMENT_KEYS = {"base", "per_tai", "dealer", "repeat"};
    private final List<AbstractWidget> editors = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private MahjongButton apply, cancel, reload, preset, reset;
    private SettingsLayout layout;
    private record Label(Component text, int y, int width) {}
    public TaiwanRulesScreen(Screen parent, BlockPos pos) {
        super(Component.translatable("taiwan.mchjong.rules.title"));
        this.parent = parent; this.pos = pos.immutable(); loadConfirmed();
    }
    public BlockPos tablePos() { return pos; }
    @Override public Screen parent() { return parent; }
    private MahjongTableBlockEntity table() {
        var client = net.minecraft.client.Minecraft.getInstance();
        return client.level != null && client.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table ? table : null;
    }
    private TableRoomView room() { return table() == null ? null : table().clientTableRoom(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g) {}
    private void loadConfirmed() {
        if (room() == null || table().clientTaiwanSettings() == null) return;
        baseline = room(); original = draft = table().clientTaiwanSettings().rules();
        Arrays.fill(paymentText, null); rejected = false;
    }
    private TaiwanPreset currentPreset() {
        return Arrays.stream(TaiwanPreset.values()).filter(p -> TaiwanGameState.Rules.of(p.rules()).name().equals(draft.name())).findFirst().orElse(null);
    }
    private static Component presetName(TaiwanPreset preset) {
        return Component.translatable("taiwan.mchjong.preset." + preset.name().toLowerCase(java.util.Locale.ROOT));
    }
    private boolean sameIdentity() {
        return room() != null && room().tableId().equals(baseline.tableId()) && room().incarnation().equals(baseline.incarnation());
    }
    private boolean stale() { return !sameIdentity() || room().decision() != baseline.decision(); }
    private Component editReason(boolean custom) {
        if (minecraft.getConnection() == null) return Component.translatable("rules.mchjong.disconnected");
        if (pending != null) return Component.translatable("rules.mchjong.pending");
        if (stale()) return Component.translatable("rules.mchjong.stale");
        if (!room().lobby() || room().exitVote() != null) return Component.translatable("rules.mchjong.phase_locked");
        if (!RoomLobby.host(room())) return Component.translatable("rules.mchjong.host_only");
        return custom && !table().clientTaiwanSettings().rulesEditable() ? Component.translatable("rules.mchjong.custom_locked") : null;
    }
    public void receivedView(boolean reply) {
        if (pending != null && reply) {
            boolean accepted = sameIdentity() && pending.equals(table().clientTaiwanSettings().rules());
            pending = null;
            if (accepted) { onClose(); return; }
            rejected = true;
        }
        if (apply != null) updateControls();
    }
    @Override protected void init() {
        clearWidgets(); editors.clear(); labels.clear(); preset = reset = null;
        int count = category == 0 ? 3 : 5;
        layout = SettingsLayout.of(width, height, 44, count * 24 + 14);
        int left = layout.bodyLeft(), span = layout.bodyWidth();
        for (int i = 0; i < 2; i++) {
            int selected = i;
            var name = Component.translatable(i == 0 ? "rules.mchjong.group.basic" : "taiwan.mchjong.payment_rules");
            addRenderableWidget(MahjongButton.create(name, ignored -> { category = selected; page = 0; init(); })
                .bounds(layout.left(), layout.contentTop() + i * 22, layout.rail(), 20).tooltip(Tooltip.create(name)).build().navigation().selected(category == i));
        }
        int rows = Math.max(1, (layout.paging() - layout.contentTop() - 14) / 24);
        pages = Math.max(1, (count + rows - 1) / rows); page = net.minecraft.util.Mth.clamp(page, 0, pages - 1);
        for (int i = page * rows; i < Math.min(count, (page + 1) * rows); i++) {
            int y = layout.contentTop() + i % rows * 24;
            if (category == 0) basicRow(i, y);
            else if (i == 0) addRenderableWidget(MahjongButton.create(ClientYakuSets.selectionLabel(draft.yakuSelection()), ignored ->
                minecraft.setScreen(new YakuSelectionScreen(this, draft.yakuSelection(), set -> { draft = draft.withYakuSelection(set); rejected = false; },
                    () -> editReason(true) == null, true))).bounds(left, y, span, 20).build());
            else paymentRow(i - 1, y);
        }
        var previous = addRenderableWidget(MahjongButton.create(Component.literal("‹"), ignored -> { page--; init(); }).bounds(left, layout.paging(), 28, 20).build()); previous.active = page > 0;
        var next = addRenderableWidget(MahjongButton.create(Component.literal("›"), ignored -> { page++; init(); }).bounds(left + span - 28, layout.paging(), 28, 20).build()); next.active = page + 1 < pages;
        reload = addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.reload"), ignored -> { if (pending == null) { loadConfirmed(); init(); } })
            .bounds(left + 32, layout.paging(), span - 64, 20).build());
        int half = (layout.span() - 4) / 2;
        apply = addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.apply"), ignored -> submit())
            .bounds(layout.left(), layout.footer(), half, 20).build().primary());
        cancel = addRenderableWidget(MahjongButton.create(Component.translatable("gui.cancel"), ignored -> onClose())
            .bounds(layout.left() + half + 4, layout.footer(), half, 20).build());
        updateControls();
    }
    private void basicRow(int row, int y) {
        int left = layout.bodyLeft(), span = layout.bodyWidth();
        var current = currentPreset();
        if (row == 0) {
            Component name = current == null ? Component.translatable("rules.mchjong.custom") : presetName(current);
            if (current != null && !draft.equals(TaiwanGameState.Rules.of(current.rules()))) name = Component.translatable("rules.mchjong.custom_preset", name);
            preset = addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.preset", name), ignored ->
                minecraft.setScreen(new TableChoiceScreen(this, Component.translatable("rules.mchjong.choose_preset"),
                    Arrays.stream(TaiwanPreset.values()).map(TaiwanRulesScreen::presetName).toList(), current == null ? -1 : current.ordinal(), index -> {
                        var next = TaiwanPreset.values()[index];
                        if (next != currentPreset()) { draft = TaiwanGameState.Rules.of(next.rules()); Arrays.fill(paymentText, null); rejected = false; }
                    }, index -> editReason(false))))
                .bounds(left, y, span, 20).build());
        } else if (row == 1) {
            var name = Component.translatable("taiwan.mchjong.reserve." + draft.reserve().name().toLowerCase(java.util.Locale.ROOT));
            editors.add(addRenderableWidget(MahjongButton.create(name, ignored -> {
                var reserves = TaiwanRules.Reserve.values();
                minecraft.setScreen(new TableChoiceScreen(this, Component.translatable("taiwan.mchjong.reserve.title"),
                    Arrays.stream(reserves).map(r -> (Component) Component.translatable("taiwan.mchjong.reserve." + r.name().toLowerCase(java.util.Locale.ROOT))).toList(),
                    draft.reserve().ordinal(), index -> { replace(draft.payment(), reserves[index]); rejected = false; }, index -> editReason(true)));
            }).bounds(left, y, span - 24, 20).build()));
            addRenderableWidget(RuleHelp.setting(this, name, RuleHelp.pageDescription("manual.mchjong.entry.taiwan_tiles.reserve"), "manual.mchjong.entry.taiwan_tiles.reserve", left + span - 20, y));
        } else {
            reset = addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.restore_defaults"), ignored -> {
                if (current != null) { draft = TaiwanGameState.Rules.of(current.rules()); Arrays.fill(paymentText, null); rejected = false; init(); }
            }).bounds(left, y, span - 24, 20).build());
            addRenderableWidget(RuleHelp.setting(this, Component.translatable("rules.mchjong.choose_preset"), RuleHelp.pageDescription("manual.mchjong.entry.taiwan_payments.presets"),
                "manual.mchjong.entry.taiwan_payments.presets", left + span - 20, y));
        }
    }
    private long paymentValue(int index) {
        return switch (index) { case 0 -> draft.payment().base(); case 1 -> draft.payment().perTai(); case 2 -> draft.payment().dealerTai(); default -> draft.payment().repeatTai(); };
    }
    private void paymentRow(int index, int y) {
        int left = layout.bodyLeft(), span = layout.bodyWidth(), fieldWidth = Math.min(84, (span - 24) / 2);
        var name = Component.translatable("taiwan.mchjong.payment." + PAYMENT_KEYS[index]);
        labels.add(new Label(name, y + 6, span - fieldWidth - 28));
        var field = new MahjongEditBox(font, left + span - 24 - fieldWidth, y, fieldWidth, 20, name);
        field.setMaxLength(7); field.setFilter(text -> text.matches("[0-9]{0,7}"));
        field.setValue(paymentText[index] == null ? Long.toString(paymentValue(index)) : paymentText[index]);
        field.setResponder(text -> { paymentText[index] = text; var payment = readPayment(); if (payment != null) replace(payment, draft.reserve()); rejected = false; updateControls(); });
        editors.add(addRenderableWidget(field));
        addRenderableWidget(RuleHelp.setting(this, name, Component.translatable("taiwan.mchjong.payment." + PAYMENT_KEYS[index] + ".description"),
            index < 2 ? "manual.mchjong.entry.taiwan_payments.amount" : "manual.mchjong.entry.taiwan_payments.dealer", left + span - 20, y));
    }
    private TaiwanRules.Payment readPayment() {
        try {
            long[] values = new long[4];
            for (int i = 0; i < 4; i++) values[i] = paymentText[i] == null ? paymentValue(i) : Long.parseLong(paymentText[i]);
            return new TaiwanRules.Payment(values[0], values[1], Math.toIntExact(values[2]), Math.toIntExact(values[3]));
        } catch (IllegalArgumentException | ArithmeticException invalid) { return null; }
    }
    private void replace(TaiwanRules.Payment payment, TaiwanRules.Reserve reserve) {
        draft = new TaiwanGameState.Rules(draft.name(), draft.values(), draft.exclusions(), draft.sources(), draft.flowers(), draft.flowerSets(), draft.pinfu(),
            draft.replacements(), draft.taiLimit(), reserve, payment, draft.yakuSelection());
    }
    private void updateControls() {
        var reason = editReason(true);
        editors.forEach(w -> { w.active = reason == null; if (w instanceof MahjongEditBox field) field.setEditable(reason == null); if (reason != null) w.setTooltip(Tooltip.create(reason)); });
        if (preset != null) preset.active = true;
        if (reset != null) reset.active = editReason(false) == null && currentPreset() != null;
        boolean allowed = table() != null && (table().clientTaiwanSettings().rulesEditable()
            || Arrays.stream(TaiwanPreset.values()).anyMatch(p -> draft.equals(TaiwanGameState.Rules.of(p.rules()))));
        apply.active = editReason(false) == null && allowed && readPayment() != null && !draft.equals(original);
        reload.visible = stale() || rejected; reload.active = pending == null;
        cancel.setMessage(Component.translatable(pending != null || rejected || room() == null || !RoomLobby.host(room()) || !room().lobby() ? "gui.back" : "gui.cancel"));
    }
    private void submit() {
        updateControls(); if (!apply.active) return;
        pending = draft; pendingTicks = 0;
        minecraft.getConnection().send(PayloadPackets.serverbound(new TaiwanRulesPayload(pos, baseline.tableId(), baseline.incarnation(), baseline.decision(), pending)));
        updateControls();
    }
    @Override public void tick() {
        if (room() == null) { onClose(); return; }
        if (pending != null && ++pendingTicks > 200) { pending = null; rejected = true; }
        updateControls();
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        updateControls(); layout.paint(g, font, title);
        for (var label : labels) MahjongUi.text(g, font, label.text(), layout.bodyLeft(), label.y(), label.width(), MahjongUi.TEXT, false);
        if (!reload.visible) g.drawCenteredString(font, (page + 1) + " / " + pages, layout.bodyLeft() + layout.bodyWidth() / 2, layout.paging() + 6, MahjongUi.MUTED);
        Component notice = rejected ? Component.translatable("rules.mchjong.rejected") : editReason(false);
        if (notice == null && readPayment() == null) notice = Component.translatable("rules.mchjong.invalid");
        if (notice != null) MahjongUi.text(g, font, notice, layout.bodyLeft(), layout.paging() - 14, layout.bodyWidth(), MahjongUi.MUTED, false);
        super.render(g, x, y, partial);
    }
    @Override public void onClose() {
        if (minecraft.level == null || room() == null) { minecraft.setScreen(null); return; }
        var root = TableChildScreen.root(parent);
        if (!room().lobby() && root instanceof TaiwanLobbyScreen) {
            var view = table().clientTaiwanView();
            minecraft.setScreen(view != null && view.game().result() != null ? new TaiwanResultsScreen(pos, false) : new TaiwanTableScreen(pos));
        } else if (room().lobby() && !(root instanceof TaiwanLobbyScreen)) minecraft.setScreen(new TaiwanLobbyScreen(pos));
        else minecraft.setScreen(parent);
    }
}
