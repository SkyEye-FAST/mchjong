package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.*;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.RiichiRulesPayload;

/** One draft across categories and child screens; only a server reply confirms an application. */
public final class RiichiRulesScreen extends Screen implements TableChildScreen {
    private enum Category {
        BASIC, POINTS, SCORING, FLOW, CALLS, PAYMENTS;
        String key() { return "rules.mchjong.group." + name().toLowerCase(java.util.Locale.ROOT); }
        boolean contains(RiichiRuleOption option) {
            boolean basic = option == RiichiRuleOption.KUITAN || option == RiichiRuleOption.RED_FIVES
                || option == RiichiRuleOption.MIN_HAN || option == RiichiRuleOption.MATCH_LENGTH;
            if (basic) return this == BASIC;
            return switch (option.group()) {
                case POINTS, UMA -> this == POINTS;
                case SCORING -> this == SCORING;
                case FLOW -> this == FLOW;
                case CALLS -> this == CALLS;
                case PAYMENTS -> this == PAYMENTS;
            };
        }
    }
    private final Screen parent;
    private final RiichiTableScreen table;
    private TableRoomView baseline;
    private RiichiRoomSettings baselineSettings;
    private RiichiRules draft, pending;
    private final Map<RiichiRuleOption, String> numbers = new EnumMap<>(RiichiRuleOption.class);
    private final List<Binding> editors = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private Category group = Category.BASIC;
    private int page, pages, pendingTicks;
    private Button apply, reload, cancel;
    private boolean rejected;
    private SettingsLayout layout;
    private record Label(Component text, Component tooltip, int x, int y, int width) {}
    private record Binding(AbstractWidget widget, Supplier<Component> reason, Component help) {}

    public RiichiRulesScreen(Screen parent, TableRoomView room, RiichiRoomSettings settings) {
        super(text("rules.mchjong.title"));
        this.parent = parent;
        table = (RiichiTableScreen) TableChildScreen.root(parent);
        baseline = room; baselineSettings = settings; draft = settings.rules();
    }
    private static Component text(String key, Object... args) { return Component.translatable(key, args); }
    public RiichiTableScreen tableScreen() { return table; }
    @Override public Screen parent() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) {}
    private boolean stale() {
        var room = table.room();
        return room == null || !room.tableId().equals(baseline.tableId()) || !room.incarnation().equals(baseline.incarnation())
            || room.decision() != baseline.decision();
    }
    private Component editReason() {
        var room = table.room();
        if (minecraft.getConnection() == null) return text("rules.mchjong.disconnected");
        if (pending != null) return text("rules.mchjong.pending");
        if (stale()) return text("rules.mchjong.stale");
        if (!room.lobby() || room.exitVote() != null) return text("rules.mchjong.phase_locked");
        if (!RoomLobby.host(room)) return text("rules.mchjong.host_only");
        return null;
    }
    private Component customReason() {
        var reason = editReason();
        return reason != null ? reason : table.worldPolicy() == null || !table.worldPolicy().allowCustomRules()
            ? text("rules.mchjong.custom_locked") : null;
    }
    private Component optionReason(RiichiRuleOption option) {
        return draft.preset().adjustable(option) ? editReason() : customReason();
    }
    private Component countReason(int count) {
        var reason = editReason();
        if (reason != null) return reason;
        if (count == draft.players()) return null;
        var room = table.room();
        if (room.seating() != RoomSeating.Stage.GATHERING) return text("rules.mchjong.count_stage_locked");
        if (count == 3 && room.seats().size() == 4 && room.seats().get(3).participant().id() != null)
            return text("rules.mchjong.count_occupied");
        return null;
    }
    private Component presetReason(RiichiPreset preset) {
        var reason = countReason(preset.players());
        if (reason != null) return reason;
        var world = table.worldPolicy();
        if (world == null || world.forcedPreset() != null && world.forcedPreset() != preset)
            return text("rules.mchjong.forced_preset");
        var proposal = preset == draft.preset() ? draft : draft.withPreset(preset);
        return table.canSupplyReds(proposal.sanma(), proposal.redFives()) ? null : text("rules.mchjong.insufficient_reds");
    }
    private boolean invalid() {
        for (var entry : numbers.entrySet()) {
            if (!entry.getKey().visible(draft)) continue;
            try { if (!entry.getKey().valid(Integer.parseInt(entry.getValue()))) return true; }
            catch (NumberFormatException failure) { return true; }
        }
        return false;
    }
    private void bind(AbstractWidget widget, Supplier<Component> reason, Component help) { editors.add(new Binding(widget, reason, help)); }
    private Component presetName() {
        var name = text(draft.preset().presetKey());
        return draft.custom() ? text("rules.mchjong.custom_preset", name) : name;
    }
    private void choosePreset(int count) {
        var presets = Arrays.stream(RiichiPreset.values()).filter(p -> p.players() == count).toList();
        minecraft.setScreen(new TableChoiceScreen(this, text("rules.mchjong.choose_preset"),
            presets.stream().map(p -> text(p.presetKey())).toList(), presets.indexOf(draft.preset()),
            index -> selectPreset(presets.get(index)), index -> presetReason(presets.get(index))));
    }
    private void selectPreset(RiichiPreset preset) {
        if (preset == draft.preset() || presetReason(preset) != null) return;
        draft = draft.withPreset(preset);
        numbers.keySet().removeIf(option -> !preset.adjustable(option));
        rejected = false;
    }
    private void chooseCount(int count) {
        if (count == draft.players() || countReason(count) != null) return;
        var corresponding = Arrays.stream(RiichiPreset.values()).filter(p -> p.players() == count
            && p.presetKey().equals(draft.preset().presetKey())).findFirst();
        if (corresponding.isPresent()) { selectPreset(corresponding.get()); init(); }
        else choosePreset(count);
    }
    private Component countAvailability(int count) {
        var reason = countReason(count);
        if (reason != null || count == draft.players()) return reason;
        var presets = Arrays.stream(RiichiPreset.values()).filter(p -> p.players() == count).toList();
        var family = presets.stream().filter(p -> p.presetKey().equals(draft.preset().presetKey())).findFirst();
        if (family.isPresent()) return presetReason(family.get());
        return presets.stream().anyMatch(p -> presetReason(p) == null) ? null : presetReason(presets.getFirst());
    }
    @Override protected void init() {
        clearWidgets(); editors.clear(); labels.clear();
        var rows = new ArrayList<java.util.function.IntConsumer>();
        if (group == Category.BASIC) {
            rows.add(this::playerRow);
            rows.add(y -> {
                var label = text("rules.mchjong.preset", presetName());
                var button = addRenderableWidget(MahjongButton.create(label, ignored -> choosePreset(draft.players()))
                    .bounds(layout.bodyLeft(), y, layout.bodyWidth(), 20).build());
                bind(button, () -> null, text("rules.mchjong.preset_help"));
            });
        }
        if (group == Category.SCORING) rows.add(y -> addRenderableWidget(MahjongButton.create(ClientYakuSets.selectionLabel(draft.yakuSelection()), ignored ->
            minecraft.setScreen(new YakuSelectionScreen(this, draft.yakuSelection(), set -> { draft = draft.withYakuSelection(set); rejected = false; },
                () -> customReason() == null, !draft.sanma())))
            .bounds(layout.bodyLeft(), y, layout.bodyWidth(), 20).build()));
        Arrays.stream(RiichiRuleOption.values()).filter(group::contains).filter(o -> o.visible(draft))
            .forEach(option -> rows.add(y -> optionRow(option, y)));
        if (group == Category.BASIC) rows.add(y -> {
            var reset = addRenderableWidget(MahjongButton.create(text("rules.mchjong.restore_defaults"), ignored -> {
                draft = draft.preset().config(); numbers.clear(); rejected = false; init();
            }).bounds(layout.bodyLeft(), y, layout.bodyWidth(), 20).build());
            bind(reset, () -> editReason() != null ? editReason() : !table.canSupplyReds(draft.sanma(), draft.preset().defaultRedFives())
                ? text("rules.mchjong.insufficient_reds") : null, text("rules.mchjong.restore_defaults"));
        });
        layout = SettingsLayout.of(width, height, Category.values().length * 22, rows.size() * 24 + 14);
        for (var category : Category.values()) {
            var label = text(category.key());
            addRenderableWidget(MahjongButton.create(label, ignored -> { group = category; page = 0; init(); })
                .bounds(layout.left(), layout.contentTop() + category.ordinal() * 22, layout.rail(), 20)
                .tooltip(Tooltip.create(label)).build().navigation().selected(group == category));
        }
        int capacity = Math.max(1, (layout.paging() - layout.contentTop() - 14) / 24);
        pages = Math.max(1, (rows.size() + capacity - 1) / capacity); page = Math.clamp(page, 0, pages - 1);
        for (int i = 0; i < capacity && page * capacity + i < rows.size(); i++) rows.get(page * capacity + i).accept(layout.contentTop() + i * 24);
        int left = layout.bodyLeft(), span = layout.bodyWidth();
        var previous = addRenderableWidget(MahjongButton.create(text("rules.mchjong.previous"), ignored -> { page--; init(); })
            .bounds(left, layout.paging(), 30, 20).build().shortCaption(Component.literal("‹"))); previous.active = page > 0;
        var next = addRenderableWidget(MahjongButton.create(text("rules.mchjong.next"), ignored -> { page++; init(); })
            .bounds(left + span - 30, layout.paging(), 30, 20).build().shortCaption(Component.literal("›"))); next.active = page + 1 < pages;
        reload = addRenderableWidget(MahjongButton.create(text("rules.mchjong.reload"), ignored -> {
            if (pending == null && table.room() != null && table.roomSettings() != null) {
                baseline = table.room(); baselineSettings = table.roomSettings(); draft = baselineSettings.rules();
                numbers.clear(); rejected = false; init();
            }
        }).bounds(left + 34, layout.paging(), span - 68, 20).build());
        int half = (layout.span() - 4) / 2;
        apply = addRenderableWidget(MahjongButton.create(text("rules.mchjong.apply"), ignored -> submit())
            .bounds(layout.left(), layout.footer(), half, 20).build().primary());
        cancel = addRenderableWidget(MahjongButton.create(text("gui.cancel"), ignored -> onClose())
            .bounds(layout.left() + half + 4, layout.footer(), half, 20).build());
        updateControls();
    }
    private void playerRow(int y) {
        int left = layout.bodyLeft(), span = layout.bodyWidth(), caption = span / 3, cell = (span - caption - 4) / 2;
        labels.add(new Label(text("rules.mchjong.players"), text("rules.mchjong.players"), left, y + 6, caption - 4));
        for (int i = 0; i < 2; i++) {
            int count = 4 - i;
            var button = addRenderableWidget(MahjongButton.create(text("ui.mchjong.players." + count), ignored -> chooseCount(count))
                .bounds(left + caption + i * (cell + 4), y, cell, 20).build().selected(count == draft.players()));
            bind(button, () -> countAvailability(count), text("ui.mchjong.players." + count));
        }
    }
    private void optionRow(RiichiRuleOption option, int y) {
        int left = layout.bodyLeft(), span = layout.bodyWidth(), optionSpan = span - 24;
        var label = option.floatingPlayers() < 0 ? Component.translatable(option.translationKey(), option.placementRank())
            : Component.translatable(option.translationKey(), option.floatingPlayers(), option.placementRank());
        var description = option.floatingPlayers() < 0 ? Component.translatable(option.descriptionKey(), option.placementRank())
            : Component.translatable(option.descriptionKey(), option.floatingPlayers(), option.placementRank());
        addRenderableWidget(RuleHelp.setting(this, label, description, left + span - 20, y));
        if (option == RiichiRuleOption.RED_FIVES) {
            int caption = Math.min(92, optionSpan / 4), choiceWidth = (optionSpan - caption - 8) / 3;
            labels.add(new Label(label, description, left, y + 6, caption - 4));
            for (var reds : RedFives.values()) {
                var text = Component.translatable(reds.translationKey());
                var choice = addRenderableWidget(MahjongButton.create(text, ignored -> {
                    if (table.canSupplyReds(draft.sanma(), reds)) {
                        draft = draft.with(option, reds.ordinal()); rejected = false; init();
                    }
                }).bounds(left + caption + reds.ordinal() * (choiceWidth + 4), y, choiceWidth, 20)
                    .build().selected(draft.redFives() == reds));
                bind(choice, () -> optionReason(option) != null ? optionReason(option)
                    : table.canSupplyReds(draft.sanma(), reds) ? null : text("rules.mchjong.insufficient_reds"), text("rules.mchjong.red_composition", draft.sanma() ? 0 : reds.count(0), reds.count(1), reds.count(2)));
            }
        } else if (!option.choices().isEmpty()) {
            var choices = option.choices();
            int caption = Math.min(92, optionSpan / 4), choiceWidth = (optionSpan - caption - (choices.size() - 1) * 4) / choices.size();
            labels.add(new Label(label, description, left, y + 6, caption - 4));
            for (int index = 0; index < choices.size(); index++) {
                int value = choices.get(index);
                var text = Component.translatable(option.translationKey() + "." + value);
                bind(addRenderableWidget(MahjongButton.create(text, ignored -> {
                    draft = draft.with(option, value); rejected = false; init();
                }).bounds(left + caption + index * (choiceWidth + 4), y, choiceWidth, 20)
                    .tooltip(Tooltip.create(Component.translatable("settings.mchjong.toggle", label, text)))
                    .build().selected(draft.get(option) == value)), () -> optionReason(option), description);
            }
        } else if (option.toggle()) {
            var value = Component.translatable(draft.enabled(option) ? "rules.mchjong.yes" : "rules.mchjong.no");
            var text = Component.translatable("settings.mchjong.toggle", label, value);
            var toggle = addRenderableWidget(MahjongButton.create(text, ignored -> {
                draft = draft.with(option, (draft.get(option) + 1) % (option.max() + 1));
                rejected = false; init();
            }).bounds(left, y, optionSpan, 20).tooltip(Tooltip.create(description)).build().selected(draft.enabled(option)));
            bind(toggle, () -> optionReason(option), description);
        } else {
            labels.add(new Label(label, description, left, y + 6, Math.max(36, optionSpan / 2) - 4));
            var field = new MahjongEditBox(font, left + Math.max(36, optionSpan / 2), y, optionSpan - Math.max(36, optionSpan / 2), 20, label);
            field.setMaxLength(8);
            field.setFilter(value -> value.matches("-?[0-9]*"));
            field.setValue(numbers.getOrDefault(option, Integer.toString(draft.get(option))));
            field.setTooltip(Tooltip.create(Component.translatable("rules.mchjong.range", label, option.min(), option.max(), option.step())));
            field.setResponder(value -> {
                numbers.put(option, value);
                try {
                    int number = Integer.parseInt(value);
                    if (option.valid(number)) draft = draft.with(option, number);
                } catch (NumberFormatException ignored) { /* Incomplete edits stay in the draft field. */ }
                rejected = false;
            });
            bind(addRenderableWidget(field), () -> optionReason(option), text("rules.mchjong.range", label, option.min(), option.max(), option.step()));
        }
    }
    private boolean allowedByWorld() {
        var world = table.worldPolicy();
        return world != null && (world.forcedPreset() == null || world.forcedPreset() == draft.preset())
            && (world.allowCustomRules() || !draft.custom());
    }
    private void updateControls() {
        for (var binding : editors) {
            var reason = binding.reason().get(); binding.widget().active = reason == null;
            if (binding.widget() instanceof MahjongEditBox field) field.setEditable(reason == null);
            binding.widget().setTooltip(Tooltip.create(reason == null ? binding.help() : reason));
        }
        apply.active = editReason() == null && allowedByWorld() && !invalid() && presetReason(draft.preset()) == null
            && !draft.equals(baselineSettings.rules());
        reload.visible = stale() || rejected; reload.active = pending == null;
        cancel.setMessage(text(pending != null || rejected || table.room() == null || !RoomLobby.host(table.room()) || !table.room().lobby() ? "gui.back" : "gui.cancel"));
    }
    private void submit() {
        updateControls();
        if (!apply.active) return;
        pending = draft; pendingTicks = 0;
        minecraft.getConnection().send(PayloadPackets.serverbound(new RiichiRulesPayload(table.tablePos(), baseline.tableId(), baseline.decision(), pending)));
        updateControls();
    }
    void receivedReply() {
        if (pending == null) return;
        var room = table.room(); var settings = table.roomSettings();
        boolean accepted = room != null && settings != null && room.tableId().equals(baseline.tableId())
            && room.incarnation().equals(baseline.incarnation()) && settings.rules().equals(pending);
        pending = null;
        if (accepted) onClose(); else { rejected = true; updateControls(); }
    }
    @Override public void tick() {
        if (minecraft.level == null || table.room() == null) { minecraft.setScreen(null); return; }
        if (pending != null && ++pendingTicks > 200) { pending = null; rejected = true; }
        updateControls();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateControls(); layout.paint(graphics, font, title);
        for (var label : labels) {
            MahjongUi.text(graphics, font, label.text(), label.x(), label.y(), label.width(), MahjongUi.TEXT, false);
            if (mouseX >= label.x() && mouseX < label.x() + label.width() && mouseY >= label.y() - 4 && mouseY < label.y() + 12)
                setTooltipForNextRenderPass(label.tooltip());
        }
        if (!reload.visible) graphics.drawCenteredString(font, (page + 1) + " / " + pages,
            layout.bodyLeft() + layout.bodyWidth() / 2, layout.paging() + 6, MahjongUi.MUTED);
        Component notice = rejected ? text("rules.mchjong.rejected") : editReason();
        if (notice == null && invalid()) notice = text("rules.mchjong.invalid");
        if (notice == null && !allowedByWorld()) notice = text("rules.mchjong.custom_locked");
        if (notice == null && !table.canSupplyReds(draft.sanma(), draft.redFives())) notice = text("rules.mchjong.insufficient_reds");
        if (notice != null) MahjongUi.text(graphics, font, notice, layout.bodyLeft(), layout.paging() - 14, layout.bodyWidth(), MahjongUi.MUTED, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
