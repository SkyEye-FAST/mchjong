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
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.RedFives;
import top.skyeyefast.mchjong.engine.RiichiRules;
import top.skyeyefast.mchjong.engine.RiichiRuleOption;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.engine.RiichiRoomSettings;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.network.RiichiRulesPayload;

/** Local draft with explicit apply/cancel; only an acknowledged server snapshot becomes active rules. */
public final class RiichiRulesScreen extends Screen implements TableChildScreen {
    private enum Mode {
        PRESET, DETAILS, CUSTOM;
        String key() { return "rules.mchjong.mode." + name().toLowerCase(java.util.Locale.ROOT); }
    }
    private static final List<RiichiRuleOption> OVERVIEW = List.of(RiichiRuleOption.KUITAN, RiichiRuleOption.RED_FIVES,
        RiichiRuleOption.MIN_HAN, RiichiRuleOption.MATCH_LENGTH, RiichiRuleOption.BANKRUPTCY,
        RiichiRuleOption.STARTING_POINTS, RiichiRuleOption.RETURN_POINTS, RiichiRuleOption.IPPATSU, RiichiRuleOption.URA_DORA,
        RiichiRuleOption.KAN_DORA, RiichiRuleOption.KAZOE_YAKUMAN, RiichiRuleOption.KIRIAGE_MANGAN, RiichiRuleOption.DOUBLE_YAKUMAN,
        RiichiRuleOption.HEAD_BUMP, RiichiRuleOption.UMA_1, RiichiRuleOption.UMA_2, RiichiRuleOption.UMA_3, RiichiRuleOption.UMA_4);
    private final Screen parent;
    private final RiichiTableScreen table;
    private TableRoomView baseline;
    private RiichiRoomSettings baselineSettings;
    private RiichiRules draft;
    private RiichiRules pending;
    private final Map<RiichiRuleOption, String> numbers = new EnumMap<>(RiichiRuleOption.class);
    private final List<AbstractWidget> editors = new ArrayList<>();
    private final Map<RedFives, Button> redButtons = new EnumMap<>(RedFives.class);
    private final Map<RedFives, Boolean> redAvailability = new EnumMap<>(RedFives.class);
    private final Map<RiichiPreset, Button> presetButtons = new EnumMap<>(RiichiPreset.class);
    private final Map<RiichiPreset, Boolean> presetAvailability = new EnumMap<>(RiichiPreset.class);
    private final List<Label> labels = new ArrayList<>();
    private RiichiRuleOption.Group group = RiichiRuleOption.Group.POINTS;
    private Mode mode;
    private int page, pages, pendingTicks;
    private Button apply;
    private boolean rejected;
    private boolean presetExpanded;
    private SettingsLayout layout;

    private record Label(Component text, Component tooltip, int x, int y, int width) {
        Label(Component text, int x, int y, int width) { this(text, text, x, y, width); }
    }

    public RiichiRulesScreen(Screen parent, TableRoomView room, RiichiRoomSettings settings) {
        this(parent, room, settings, false);
    }
    public RiichiRulesScreen(Screen parent, TableRoomView room, RiichiRoomSettings settings, boolean presetExpanded) {
        super(Component.translatable("rules.mchjong.title"));
        this.parent = parent;
        this.table = (RiichiTableScreen) TableChildScreen.root(parent);
        baseline = room;
        baselineSettings = settings;
        draft = settings.rules();
        mode = draft.custom() ? Mode.CUSTOM : Mode.PRESET;
        this.presetExpanded = presetExpanded;
    }
    public RiichiTableScreen tableScreen() { return table; }
    @Override public Screen parent() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    private boolean host() {
        var room = table.room();
        return room != null && room.tableId().equals(baseline.tableId()) && room.lobby()
            && room.viewerSeat() >= 0 && room.viewerSeat() == room.host() && room.exitVote() == null;
    }
    private boolean stale() {
        var room = table.room();
        return room == null || !room.tableId().equals(baseline.tableId()) || room.decision() != baseline.decision();
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
        layout = SettingsLayout.of(width, height);
        int span = layout.bodyWidth(), left = layout.bodyLeft();
        Component preset = Component.translatable("rules.mchjong.preset", Component.translatable(draft.preset().presetKey()))
            .append(presetExpanded ? " ▲" : " ▼");
        var presetButton = addRenderableWidget(MahjongButton.create(preset, ignored -> {
            presetExpanded = !presetExpanded; init();
        }).bounds(left, layout.contentTop(), span, 20).tooltip(Tooltip.create(Component.translatable("rules.mchjong.preset_help"))).build());
        editors.add(presetButton);
        if (presetExpanded) {
            var presets = Arrays.stream(RiichiPreset.values()).filter(rule -> rule.players() == draft.players()).toList();
            for (int i = 0; i < presets.size(); i++) {
                var rule = presets.get(i);
                var name = Component.translatable(rule.presetKey());
                var choice = addRenderableWidget(MahjongButton.create(name, ignored -> {
                    draft = draft.withPreset(rule); mode = Mode.PRESET; numbers.clear(); page = 0; rejected = false;
                    presetExpanded = false; init();
                }).bounds(left + 12, layout.top() + 62 + i * 24, span - 12, 20).build().selected(draft.preset() == rule));
                presetButtons.put(rule, choice);
            }
            addRenderableWidget(MahjongButton.create(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(left, layout.footer(), span, 20).build());
            updateControls();
            return;
        }
        int tabWidth = (span - 8) / 3;
        for (var section : Mode.values()) {
            var text = Component.translatable(section.key());
            addRenderableWidget(MahjongButton.create(text, ignored -> { mode = section; page = 0; init(); })
                .bounds(left + section.ordinal() * (tabWidth + 4), layout.top() + 62, tabWidth, 20)
                .tooltip(Tooltip.create(text)).build().selected(mode == section));
        }
        var groups = RiichiRuleOption.Group.values();
        for (int i = 0; mode != Mode.PRESET && i < groups.length; i++) {
            var category = groups[i];
            var text = Component.translatable(category.translationKey());
            addRenderableWidget(MahjongButton.create(text, ignored -> { group = category; page = 0; init(); })
                .bounds(layout.left(), layout.contentTop() + i * 22, layout.rail(), 20)
                .tooltip(Tooltip.create(text)).build().navigation().selected(group == category));
        }
        var options = (mode == Mode.PRESET ? OVERVIEW.stream() : Arrays.stream(RiichiRuleOption.values())
            .filter(option -> option.group() == group))
            .filter(option -> option.visible(draft)).toList();
        int rows = Math.max(1, (layout.height() - 160) / 24);
        pages = Math.max(1, (options.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = 0; i < rows && page * rows + i < options.size(); i++) {
            var option = options.get(page * rows + i);
            int y = layout.top() + 92 + i * 24;
            var label = option.floatingPlayers() < 0 ? Component.translatable(option.translationKey(), option.placementRank())
                : Component.translatable(option.translationKey(), option.floatingPlayers(), option.placementRank());
            var description = option.floatingPlayers() < 0 ? Component.translatable(option.descriptionKey(), option.placementRank())
                : Component.translatable(option.descriptionKey(), option.floatingPlayers(), option.placementRank());
            boolean editable = mode == Mode.CUSTOM || mode == Mode.PRESET && draft.preset().adjustable(option);
            if (!editable) {
                Component value = option.toggle() ? Component.translatable(draft.enabled(option) ? "rules.mchjong.yes" : "rules.mchjong.no")
                    : option == RiichiRuleOption.RED_FIVES ? Component.translatable(draft.redFives().translationKey())
                    : !option.choices().isEmpty() ? Component.translatable(option.translationKey() + "." + draft.get(option))
                    : Component.literal(Integer.toString(draft.get(option)));
                labels.add(new Label(Component.translatable("settings.mchjong.toggle", label, value), description, left, y + 6, span));
            } else if (option == RiichiRuleOption.RED_FIVES) {
                int caption = Math.min(92, span / 4), choiceWidth = (span - caption - 8) / 3;
                labels.add(new Label(label, description, left, y + 6, caption - 4));
                for (var reds : RedFives.values()) {
                    var text = Component.translatable(reds.translationKey());
                    var choice = addRenderableWidget(MahjongButton.create(text, ignored -> {
                        if (table.canSupplyReds(draft.sanma(), reds)) {
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
            .bounds(left, layout.paging(), 30, 20).tooltip(Tooltip.create(Component.translatable("rules.mchjong.previous"))).build());
        previous.active = page > 0;
        var next = addRenderableWidget(MahjongButton.create(Component.literal(">"), ignored -> { page++; init(); })
            .bounds(left + span - 30, layout.paging(), 30, 20).tooltip(Tooltip.create(Component.translatable("rules.mchjong.next"))).build());
        next.active = page + 1 < pages;
        addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.reload"), ignored -> {
            var current = table.room();
            var settings = table.roomSettings();
            if (current != null && settings != null && pending == null) {
                baseline = current; baselineSettings = settings; draft = settings.rules();
                numbers.clear(); rejected = false; page = 0; init();
            }
        }).bounds(left, layout.footer(), tabWidth, 20).build());
        apply = addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.apply"), ignored -> submit())
            .bounds(left + tabWidth + 4, layout.footer(), tabWidth, 20).build().primary());
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.cancel"), ignored -> onClose())
            .bounds(left + 2 * (tabWidth + 4), layout.footer(), tabWidth, 20).build());
        updateControls();
    }

    private void updateControls() {
        boolean editable = host() && pending == null && !stale();
        var world = table.worldPolicy();
        boolean customEditable = world != null && (world.allowCustomRules() || mode != Mode.CUSTOM);
        editors.forEach(widget -> widget.active = editable && customEditable);
        redButtons.forEach((reds, button) -> {
            boolean available = table.canSupplyReds(draft.sanma(), reds);
            button.active = editable && available;
            if (!Boolean.valueOf(available).equals(redAvailability.put(reds, available)))
                button.setTooltip(Tooltip.create(available ? Component.translatable("rules.mchjong.red_composition",
                    draft.sanma() ? 0 : reds.count(0), reds.count(1), reds.count(2))
                    : Component.translatable("rules.mchjong.insufficient_reds")));
        });
        presetButtons.forEach((rule, button) -> {
            boolean available = table.canSupplyReds(rule.sanma(), draft.withPreset(rule).redFives())
                && world != null && (world.forcedPreset() == null || world.forcedPreset() == rule);
            button.active = editable && available;
            if (!Boolean.valueOf(available).equals(presetAvailability.put(rule, available)))
                button.setTooltip(Tooltip.create(available ? Component.translatable(rule.presetKey())
                    : Component.translatable("rules.mchjong.insufficient_reds")));
        });
        if (apply != null) apply.active = editable && allowedByWorld(draft) && !invalid() && !missingReds()
            && !draft.equals(baselineSettings.rules());
    }
    private boolean allowedByWorld(RiichiRules config) {
        var world = table.worldPolicy();
        return world != null && (world.forcedPreset() == null || world.forcedPreset() == config.preset())
            && (world.allowCustomRules() || !config.custom());
    }
    private boolean missingReds() { return !table.canSupplyReds(draft.sanma(), draft.redFives()); }
    private void submit() {
        if (!host() || stale() || !allowedByWorld(draft) || invalid() || missingReds()
            || pending != null || minecraft.getConnection() == null) return;
        pending = draft;
        pendingTicks = 0;
        minecraft.getConnection().send(PayloadPackets.serverbound(
            new RiichiRulesPayload(table.tablePos(), baseline.tableId(), baseline.decision(), pending)));
        updateControls();
    }
    void receivedReply() {
        if (pending == null) return;
        var room = table.room();
        var settings = table.roomSettings();
        boolean accepted = room != null && settings != null
            && room.tableId().equals(baseline.tableId()) && settings.rules().equals(pending);
        pending = null;
        if (accepted) onClose();
        else { rejected = true; updateControls(); }
    }
    @Override public void tick() {
        if (minecraft.level == null) { minecraft.setScreen(null); return; }
        if (pending != null && ++pendingTicks > 200) { pending = null; rejected = true; }
        updateControls();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        updateControls();

        layout.paint(graphics, font, title);
        for (var label : labels) {
            MahjongUi.text(graphics, font, label.text(), label.x(), label.y(), label.width(), MahjongUi.TEXT, false);
            if (mouseX >= label.x() && mouseX < label.x() + label.width() && mouseY >= label.y() - 4 && mouseY < label.y() + 12)
                graphics.setTooltipForNextFrame(font, label.tooltip(), mouseX, mouseY);
        }
        if (!presetExpanded)
            graphics.centeredText(font, (page + 1) + " / " + pages, layout.bodyLeft() + layout.bodyWidth() / 2, layout.paging() + 6, MahjongUi.MUTED);
        String notice = pending != null ? "rules.mchjong.pending" : rejected ? "rules.mchjong.rejected"
            : !host() ? "rules.mchjong.read_only" : stale() ? "rules.mchjong.stale" : invalid() ? "rules.mchjong.invalid"
            : missingReds() ? "rules.mchjong.insufficient_reds" : !allowedByWorld(draft) ? "settings.mchjong.world_locked" : null;
        if (notice != null) MahjongUi.text(graphics, font, Component.translatable(notice), layout.bodyLeft(), layout.paging() - 14, layout.bodyWidth(),
            rejected || stale() || invalid() || missingReds() ? MahjongUi.NEGATIVE : MahjongUi.MUTED, true);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
