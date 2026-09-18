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
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RedFives;
import top.skyeyefast.mchjong.engine.RuleConfig;
import top.skyeyefast.mchjong.engine.RuleOption;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.network.TableRulesPayload;

/** Local draft with explicit apply/cancel; only an acknowledged server snapshot becomes active rules. */
public final class TableRulesScreen extends Screen {
    private final TableScreen parent;
    private TableView baseline;
    private RuleConfig draft;
    private RuleConfig pending;
    private final Map<RuleOption, String> numbers = new EnumMap<>(RuleOption.class);
    private final List<AbstractWidget> editors = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private RuleOption.Group group = RuleOption.Group.POINTS;
    private int page, pages, pendingTicks;
    private Button apply;
    private boolean rejected;

    private record Label(Component text, int x, int y, int width) {}

    public TableRulesScreen(TableScreen parent, TableView initial) {
        super(Component.translatable("rules.mchjong.title"));
        this.parent = parent;
        baseline = initial;
        draft = initial.rules();
    }
    public TableScreen tableScreen() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    private boolean host() {
        var view = parent.view();
        return view != null && view.tableId().equals(baseline.tableId()) && view.phase() == Game.Phase.LOBBY
            && view.actions().stream().anyMatch(action -> action.type() == Action.Type.CHANGE_RULE);
    }
    private boolean stale() {
        var view = parent.view();
        return view == null || !view.tableId().equals(baseline.tableId()) || view.decision() != baseline.decision();
    }
    private boolean invalid() {
        for (var entry : numbers.entrySet()) {
            if (!entry.getKey().visible(draft)) continue;
            try { if (!entry.getKey().valid(Integer.parseInt(entry.getValue()))) return true; }
            catch (NumberFormatException failure) { return true; }
        }
        return false;
    }

    @Override protected void init() {
        clearWidgets(); editors.clear(); labels.clear();
        int span = Math.min(540, width - 24), left = (width - span) / 2;
        Component preset = Component.translatable("rules.mchjong.preset", Component.translatable(draft.preset().presetKey()));
        var presetButton = addRenderableWidget(MahjongButton.create(preset, ignored -> {
            var presets = Arrays.stream(RuleSet.values()).filter(rule -> rule.players() == draft.players()).toList();
            draft = presets.get((presets.indexOf(draft.preset()) + 1) % presets.size()).config();
            numbers.clear(); page = 0; rejected = false; init();
        }).bounds(left, 30, span, 20).tooltip(Tooltip.create(Component.translatable("rules.mchjong.preset_help"))).build());
        editors.add(presetButton);
        var groups = RuleOption.Group.values();
        int tabWidth = (span - 8) / 3;
        for (int i = 0; i < groups.length; i++) {
            var category = groups[i];
            var text = Component.translatable(category.translationKey());
            addRenderableWidget(MahjongButton.create(text, ignored -> { group = category; page = 0; init(); })
                .bounds(left + i % 3 * (tabWidth + 4), 56 + i / 3 * 24, tabWidth, 20)
                .tooltip(Tooltip.create(text)).build().selected(group == category));
        }
        var options = Arrays.stream(RuleOption.values()).filter(option -> option.group() == group && option.visible(draft)).toList();
        int rows = Math.max(1, (height - 182) / 24);
        pages = Math.max(1, (options.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = 0; i < rows && page * rows + i < options.size(); i++) {
            var option = options.get(page * rows + i);
            int y = 108 + i * 24;
            var label = option.floatingPlayers() < 0 ? Component.translatable(option.translationKey(), option.placementRank())
                : Component.translatable(option.translationKey(), option.floatingPlayers(), option.placementRank());
            if (option.toggle() || option == RuleOption.RED_FIVES) {
                var value = option.toggle() ? Component.translatable(draft.enabled(option) ? "options.on" : "options.off")
                    : draft.get(option) == 0 ? Component.translatable("rules.mchjong.any_reds")
                    : Component.translatable(RedFives.values()[draft.get(option) - 1].translationKey());
                var text = Component.translatable("settings.mchjong.toggle", label, value);
                var toggle = addRenderableWidget(MahjongButton.create(text, ignored -> {
                    draft = draft.with(option, (draft.get(option) + 1) % (option.max() + 1));
                    rejected = false; init();
                }).bounds(left, y, span, 20).tooltip(Tooltip.create(text)).build().selected(draft.enabled(option)));
                editors.add(toggle);
            } else {
                labels.add(new Label(label, left, y + 6, span - 116));
                var field = new MahjongEditBox(font, left + span - 108, y, 108, 20, label);
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
                editors.add(addRenderableWidget(field));
            }
        }
        var previous = addRenderableWidget(MahjongButton.create(Component.literal("<"), ignored -> { page--; init(); })
            .bounds(width / 2 - 75, height - 59, 30, 20).tooltip(Tooltip.create(Component.translatable("rules.mchjong.previous"))).build());
        previous.active = page > 0;
        var next = addRenderableWidget(MahjongButton.create(Component.literal(">"), ignored -> { page++; init(); })
            .bounds(width / 2 + 45, height - 59, 30, 20).tooltip(Tooltip.create(Component.translatable("rules.mchjong.next"))).build());
        next.active = page + 1 < pages;
        addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.reload"), ignored -> {
            var current = parent.view();
            if (current != null && pending == null) {
                baseline = current; draft = current.rules(); numbers.clear(); rejected = false; page = 0; init();
            }
        }).bounds(left, height - 30, tabWidth, 20).build());
        apply = addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.apply"), ignored -> submit())
            .bounds(left + tabWidth + 4, height - 30, tabWidth, 20).build().primary());
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.cancel"), ignored -> onClose())
            .bounds(left + 2 * (tabWidth + 4), height - 30, tabWidth, 20).build());
        updateControls();
    }

    private void updateControls() {
        boolean editable = host() && pending == null && !stale();
        editors.forEach(widget -> widget.active = editable);
        if (apply != null) apply.active = editable && !invalid() && !draft.equals(baseline.rules());
    }
    private void submit() {
        if (!host() || stale() || invalid() || pending != null || minecraft.getConnection() == null) return;
        pending = draft;
        pendingTicks = 0;
        minecraft.getConnection().send(new ServerboundCustomPayloadPacket(
            new TableRulesPayload(parent.tablePos(), baseline.tableId(), baseline.decision(), pending)));
        updateControls();
    }
    void receivedReply() {
        if (pending == null) return;
        var view = parent.view();
        boolean accepted = view != null && view.tableId().equals(baseline.tableId()) && view.rules().equals(pending);
        pending = null;
        if (accepted) onClose();
        else { rejected = true; updateControls(); }
    }
    @Override public void tick() {
        if (minecraft.level == null) { minecraft.setScreen(null); return; }
        if (pending != null && ++pendingTicks > 200) { pending = null; rejected = true; }
        updateControls();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateControls();
        MahjongUi.backdrop(graphics, width, height, 580);
        MahjongUi.text(graphics, font, draft.custom() ? Component.translatable("rules.mchjong.custom") : title,
            12, 12, width - 24, MahjongUi.TEXT, true);
        for (var label : labels) MahjongUi.text(graphics, font, label.text(), label.x(), label.y(), label.width(), MahjongUi.TEXT, false);
        graphics.drawCenteredString(font, (page + 1) + " / " + pages, width / 2, height - 53, MahjongUi.MUTED);
        String notice = pending != null ? "rules.mchjong.pending" : rejected ? "rules.mchjong.rejected"
            : !host() ? "rules.mchjong.read_only" : stale() ? "rules.mchjong.stale" : invalid() ? "rules.mchjong.invalid"
            : group == RuleOption.Group.POINTS || group == RuleOption.Group.UMA ? "rules.mchjong.points_note" : "rules.mchjong.apply_note";
        MahjongUi.text(graphics, font, Component.translatable(notice), 12, height - 75, width - 24,
            rejected || stale() || invalid() ? MahjongUi.NEGATIVE : MahjongUi.MUTED, true);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
