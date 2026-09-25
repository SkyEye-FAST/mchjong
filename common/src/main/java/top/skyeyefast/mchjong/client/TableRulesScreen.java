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
import top.skyeyefast.mchjong.network.PayloadPackets;
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
    private enum Mode {
        PRESET, DETAILS, CUSTOM;
        String key() { return "rules.mchjong.mode." + name().toLowerCase(java.util.Locale.ROOT); }
    }
    private static final List<RuleOption> OVERVIEW = List.of(RuleOption.KUITAN, RuleOption.RED_FIVES,
        RuleOption.MIN_HAN, RuleOption.MATCH_LENGTH, RuleOption.BANKRUPTCY,
        RuleOption.STARTING_POINTS, RuleOption.RETURN_POINTS, RuleOption.IPPATSU, RuleOption.URA_DORA,
        RuleOption.KAN_DORA, RuleOption.KAZOE_YAKUMAN, RuleOption.KIRIAGE_MANGAN, RuleOption.DOUBLE_YAKUMAN,
        RuleOption.HEAD_BUMP, RuleOption.UMA_1, RuleOption.UMA_2, RuleOption.UMA_3, RuleOption.UMA_4);
    private final TableScreen parent;
    private TableView baseline;
    private RuleConfig draft;
    private RuleConfig pending;
    private final Map<RuleOption, String> numbers = new EnumMap<>(RuleOption.class);
    private final List<AbstractWidget> editors = new ArrayList<>();
    private final Map<RedFives, Button> redButtons = new EnumMap<>(RedFives.class);
    private final Map<RedFives, Boolean> redAvailability = new EnumMap<>(RedFives.class);
    private final Map<RuleSet, Button> presetButtons = new EnumMap<>(RuleSet.class);
    private final Map<RuleSet, Boolean> presetAvailability = new EnumMap<>(RuleSet.class);
    private final List<Label> labels = new ArrayList<>();
    private RuleOption.Group group = RuleOption.Group.POINTS;
    private Mode mode;
    private int page, pages, pendingTicks;
    private Button apply;
    private boolean rejected;
    private boolean presetExpanded;

    private record Label(Component text, Component tooltip, int x, int y, int width) {
        Label(Component text, int x, int y, int width) { this(text, text, x, y, width); }
    }

    public TableRulesScreen(TableScreen parent, TableView initial) {
        this(parent, initial, false);
    }
    public TableRulesScreen(TableScreen parent, TableView initial, boolean presetExpanded) {
        super(Component.translatable("rules.mchjong.title"));
        this.parent = parent;
        baseline = initial;
        draft = initial.rules();
        mode = draft.custom() ? Mode.CUSTOM : Mode.PRESET;
        this.presetExpanded = presetExpanded;
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
        clearWidgets(); editors.clear(); labels.clear(); redButtons.clear(); redAvailability.clear();
        presetButtons.clear(); presetAvailability.clear(); apply = null;
        int span = Math.min(540, width - 24), left = (width - span) / 2;
        Component preset = Component.translatable("rules.mchjong.preset", Component.translatable(draft.preset().presetKey()))
            .append(presetExpanded ? " ▲" : " ▼");
        var presetButton = addRenderableWidget(MahjongButton.create(preset, ignored -> {
            presetExpanded = !presetExpanded; init();
        }).bounds(left, 30, span, 20).tooltip(Tooltip.create(Component.translatable("rules.mchjong.preset_help"))).build());
        editors.add(presetButton);
        if (presetExpanded) {
            var presets = Arrays.stream(RuleSet.values()).filter(rule -> rule.players() == draft.players()).toList();
            for (int i = 0; i < presets.size(); i++) {
                var rule = presets.get(i);
                var name = Component.translatable(rule.presetKey());
                var choice = addRenderableWidget(MahjongButton.create(name, ignored -> {
                    draft = draft.withPreset(rule); mode = Mode.PRESET; numbers.clear(); page = 0; rejected = false;
                    presetExpanded = false; init();
                }).bounds(left, 56 + i * 24, span, 20).build().selected(draft.preset() == rule));
                presetButtons.put(rule, choice);
            }
            addRenderableWidget(MahjongButton.create(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(left, height - 30, span, 20).build());
            updateControls();
            return;
        }
        int tabWidth = (span - 8) / 3;
        for (var section : Mode.values()) {
            var text = Component.translatable(section.key());
            addRenderableWidget(MahjongButton.create(text, ignored -> { mode = section; page = 0; init(); })
                .bounds(left + section.ordinal() * (tabWidth + 4), 56, tabWidth, 20)
                .tooltip(Tooltip.create(text)).build().selected(mode == section));
        }
        var groups = RuleOption.Group.values();
        int groupWidth = (span - (groups.length - 1) * 4) / groups.length;
        for (int i = 0; mode != Mode.PRESET && i < groups.length; i++) {
            var category = groups[i];
            var text = Component.translatable(category.translationKey());
            addRenderableWidget(MahjongButton.create(text, ignored -> { group = category; page = 0; init(); })
                .bounds(left + i * (groupWidth + 4), 80, groupWidth, 20)
                .tooltip(Tooltip.create(text)).build().selected(group == category));
        }
        if (mode == Mode.PRESET) labels.add(new Label(Component.translatable(
            draft.custom() ? "rules.mchjong.custom_note" : "rules.mchjong.preset_note"), left, 85, span));
        var options = (mode == Mode.PRESET ? OVERVIEW.stream() : Arrays.stream(RuleOption.values())
            .filter(option -> option.group() == group))
            .filter(option -> option.visible(draft)).toList();
        int rows = Math.max(1, (height - 182) / 24);
        pages = Math.max(1, (options.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = 0; i < rows && page * rows + i < options.size(); i++) {
            var option = options.get(page * rows + i);
            int y = 108 + i * 24;
            var label = option.floatingPlayers() < 0 ? Component.translatable(option.translationKey(), option.placementRank())
                : Component.translatable(option.translationKey(), option.floatingPlayers(), option.placementRank());
            var description = option.floatingPlayers() < 0 ? Component.translatable(option.descriptionKey(), option.placementRank())
                : Component.translatable(option.descriptionKey(), option.floatingPlayers(), option.placementRank());
            boolean editable = mode == Mode.CUSTOM || mode == Mode.PRESET && draft.preset().adjustable(option);
            if (!editable) {
                Component value = option.toggle() ? Component.translatable(draft.enabled(option) ? "rules.mchjong.yes" : "rules.mchjong.no")
                    : option == RuleOption.RED_FIVES ? Component.translatable(draft.redFives().translationKey())
                    : !option.choices().isEmpty() ? Component.translatable(option.translationKey() + "." + draft.get(option))
                    : Component.literal(Integer.toString(draft.get(option)));
                labels.add(new Label(Component.translatable("settings.mchjong.toggle", label, value), description, left, y + 6, span));
            } else if (option == RuleOption.RED_FIVES) {
                int caption = Math.min(92, span / 4), choiceWidth = (span - caption - 8) / 3;
                labels.add(new Label(label, description, left, y + 6, caption - 4));
                for (var reds : RedFives.values()) {
                    var text = Component.translatable(reds.translationKey());
                    var choice = addRenderableWidget(MahjongButton.create(text, ignored -> {
                        if (parent.canSupplyReds(draft.sanma(), reds)) {
                            draft = draft.with(option, reds.ordinal()); rejected = false; init();
                        }
                    }).bounds(left + caption + reds.ordinal() * (choiceWidth + 4), y, choiceWidth, 20)
                        .build().selected(draft.redFives() == reds));
                    redButtons.put(reds, choice);
                }
            } else if (!option.choices().isEmpty()) {
                var choices = option.choices();
                int caption = Math.min(92, span / 4), choiceWidth = (span - caption - (choices.size() - 1) * 4) / choices.size();
                labels.add(new Label(label, description, left, y + 6, caption - 4));
                for (int index = 0; index < choices.size(); index++) {
                    int value = choices.get(index);
                    var text = Component.translatable(option.translationKey() + "." + value);
                    editors.add(addRenderableWidget(MahjongButton.create(text, ignored -> {
                        draft = draft.with(option, value); rejected = false; init();
                    }).bounds(left + caption + index * (choiceWidth + 4), y, choiceWidth, 20)
                        .tooltip(Tooltip.create(Component.translatable("settings.mchjong.toggle", label, text)))
                        .build().selected(draft.get(option) == value)));
                }
            } else if (option.toggle()) {
                var value = Component.translatable(draft.enabled(option) ? "rules.mchjong.yes" : "rules.mchjong.no");
                var text = Component.translatable("settings.mchjong.toggle", label, value);
                var toggle = addRenderableWidget(MahjongButton.create(text, ignored -> {
                    draft = draft.with(option, (draft.get(option) + 1) % (option.max() + 1));
                    rejected = false; init();
                }).bounds(left, y, span, 20).tooltip(Tooltip.create(description)).build().selected(draft.enabled(option)));
                editors.add(toggle);
            } else {
                labels.add(new Label(label, description, left, y + 6, span - 116));
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
        redButtons.forEach((reds, button) -> {
            boolean available = parent.canSupplyReds(draft.sanma(), reds);
            button.active = editable && available;
            if (!Boolean.valueOf(available).equals(redAvailability.put(reds, available)))
                button.setTooltip(Tooltip.create(available ? Component.translatable("rules.mchjong.red_composition",
                    draft.sanma() ? 0 : reds.count(0), reds.count(1), reds.count(2))
                    : Component.translatable("rules.mchjong.insufficient_reds")));
        });
        presetButtons.forEach((rule, button) -> {
            boolean available = parent.canSupplyReds(rule.sanma(), draft.withPreset(rule).redFives());
            button.active = editable && available;
            if (!Boolean.valueOf(available).equals(presetAvailability.put(rule, available)))
                button.setTooltip(Tooltip.create(available ? Component.translatable(rule.presetKey())
                    : Component.translatable("rules.mchjong.insufficient_reds")));
        });
        if (apply != null) apply.active = editable && !invalid() && !missingReds() && !draft.equals(baseline.rules());
    }
    private boolean missingReds() { return !parent.canSupplyReds(draft.sanma(), draft.redFives()); }
    private void submit() {
        if (!host() || stale() || invalid() || missingReds() || pending != null || minecraft.getConnection() == null) return;
        pending = draft;
        pendingTicks = 0;
        minecraft.getConnection().send(PayloadPackets.serverbound(
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
        for (var label : labels) {
            MahjongUi.text(graphics, font, label.text(), label.x(), label.y(), label.width(), MahjongUi.TEXT, false);
            if (mouseX >= label.x() && mouseX < label.x() + label.width() && mouseY >= label.y() - 4 && mouseY < label.y() + 12)
                setTooltipForNextRenderPass(label.tooltip());
        }
        if (!presetExpanded)
            graphics.drawCenteredString(font, (page + 1) + " / " + pages, width / 2, height - 53, MahjongUi.MUTED);
        String notice = pending != null ? "rules.mchjong.pending" : rejected ? "rules.mchjong.rejected"
            : !host() ? "rules.mchjong.read_only" : stale() ? "rules.mchjong.stale" : invalid() ? "rules.mchjong.invalid"
            : missingReds() ? "rules.mchjong.insufficient_reds" : mode == Mode.CUSTOM ? "rules.mchjong.custom_note"
            : mode == Mode.PRESET ? "rules.mchjong.apply_note" : "rules.mchjong.details_note";
        MahjongUi.text(graphics, font, Component.translatable(notice), 12, height - (presetExpanded ? 52 : 75), width - 24,
            rejected || stale() || invalid() || missingReds() ? MahjongUi.NEGATIVE : MahjongUi.MUTED, true);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
