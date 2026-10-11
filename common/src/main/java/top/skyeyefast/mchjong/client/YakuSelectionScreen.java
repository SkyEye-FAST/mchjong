package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.*;

/** Individual switches edit the parent's draft; Apply remains the server-authorized boundary. */
final class YakuSelectionScreen extends Screen implements TableChildScreen {
    private record Entry(String id, YakuSet set) {
        Component name() { return set == null ? Component.translatable(id.startsWith("mchjong:riichi/")
            ? "yaku.mchjong." + id.substring("mchjong:riichi/".length())
            : "taiwan.mchjong.pattern." + id.substring("mchjong:taiwan/".length())) : ClientYakuSets.text(set.find(id).name()); }
    }
    private final Screen parent;
    private YakuSelection selected;
    private final Consumer<YakuSelection> choose;
    private final BooleanSupplier editable;
    private final boolean ancientAllowed;
    private List<YakuSet> shownSets = List.of();
    private List<Entry> shown = List.of();
    private final List<MahjongButton> choices = new ArrayList<>();
    private SettingsLayout layout;
    private int page, pages, rows;
    private MahjongButton ancientToggle, reset, clear;

    YakuSelectionScreen(Screen parent, YakuSelection selected, Consumer<YakuSelection> choose, BooleanSupplier editable, boolean ancientAllowed) {
        super(Component.translatable("rules.mchjong.yaku_set.title"));
        this.parent = parent; this.selected = selected; this.choose = choose; this.editable = editable; this.ancientAllowed = ancientAllowed;
    }
    @Override public Screen parent() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) {}
    private List<YakuSet> catalog() {
        var result = new ArrayList<>(ClientYakuSets.available(selected.variant()));
        for (var frozen : selected.sets()) {
            result.removeIf(s -> s.id().equals(frozen.id())); result.add(frozen);
        }
        return result.stream().filter(s -> ancientAllowed || !s.id().equals(BuiltinYakuSets.MAHJONG_SOUL_ANCIENT_ID))
            .sorted(java.util.Comparator.comparing(YakuSet::id)).toList();
    }
    @Override protected void init() {
        clearWidgets(); choices.clear(); shownSets = catalog();
        var entries = new ArrayList<Entry>();
        YakuSelection.nativeIds(selected.variant()).stream().sorted().forEach(id -> entries.add(new Entry(id, null)));
        for (var set : shownSets) for (var award : set.awards()) entries.add(new Entry(award.id(), set));
        shown = List.copyOf(entries);
        layout = SettingsLayout.of(width, height, 0, shown.size() * 24 + 24);
        int half = (layout.span() - 4) / 2;
        reset = addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.yaku_set.defaults"), ignored -> {
            if (editable.getAsBoolean()) update(YakuSelection.defaults(selected.variant()));
        }).bounds(layout.left(), layout.contentTop(), half, 20).build()); reset.active = editable.getAsBoolean();
        clear = addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.yaku_set.clear"), ignored -> {
            if (editable.getAsBoolean()) update(new YakuSelection(selected.variant(), List.of(), YakuSelection.nativeIds(selected.variant()), java.util.Set.of()));
        }).bounds(layout.left() + half + 4, layout.contentTop(), half, 20).build()); clear.active = editable.getAsBoolean();
        ancientToggle = null;
        boolean ancient = ancientAllowed && selected.variant() == MahjongVariant.RIICHI;
        if (ancient) {
            var set = BuiltinYakuSets.MAHJONG_SOUL_ANCIENT;
            long count = set.awards().stream().filter(a -> selected.enabled(a.id())).count();
            boolean all = count == set.awards().size();
            var label = Component.translatable("settings.mchjong.toggle", Component.translatable("rules.mchjong.yaku_set.ancient"),
                Component.translatable(all ? "rules.mchjong.yes" : count > 0 ? "rules.mchjong.yaku_set.partial" : "rules.mchjong.no"));
            ancientToggle = addRenderableWidget(MahjongButton.create(label, ignored -> {
                if (editable.getAsBoolean()) update(selected.withSet(set, !all));
            }).bounds(layout.left(), layout.contentTop() + 24, layout.span(), 20).build().selected(count > 0));
        }
        int header = ancient ? 48 : 24;
        rows = Math.max(1, (layout.paging() - layout.contentTop() - header) / 24);
        pages = Math.max(1, (shown.size() + rows - 1) / rows); page = Math.clamp(page, 0, pages - 1);
        for (int i = 0; i < rows && page * rows + i < shown.size(); i++) {
            var entry = shown.get(page * rows + i);
            boolean on = selected.enabled(entry.id());
            int y = layout.contentTop() + header + i * 24;
            var label = Component.translatable("settings.mchjong.toggle", entry.name(), Component.translatable(on ? "rules.mchjong.yes" : "rules.mchjong.no"));
            var button = addRenderableWidget(MahjongButton.create(label, ignored -> {
                if (editable.getAsBoolean() && available(entry)) update(entry.set() == null
                    ? selected.withNative(entry.id(), !on) : selected.withCustom(entry.set(), entry.id(), !on));
            }).bounds(layout.left(), y, layout.span() - 24, 20).tooltip(Tooltip.create(entry.set() == null
                ? Component.translatable("rules.mchjong.yaku_set.native") : ClientYakuSets.text(entry.set().name()))).build().selected(on));
            choices.add(button);
            if (entry.set() != null) addRenderableWidget(MahjongButton.create(Component.literal("?"), ignored -> minecraft.setScreen(
                new TableHelpScreen(this, entry.name(), () -> details(entry.set().find(entry.id())))))
                .bounds(layout.left() + layout.span() - 20, y, 20, 20).build());
        }
        var previous = addRenderableWidget(MahjongButton.create(Component.literal("‹"), ignored -> { page--; init(); })
            .bounds(layout.left(), layout.paging(), 28, 20).build()); previous.active = page > 0;
        var next = addRenderableWidget(MahjongButton.create(Component.literal("›"), ignored -> { page++; init(); })
            .bounds(layout.left() + layout.span() - 28, layout.paging(), 28, 20).build()); next.active = page + 1 < pages;
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.back"), ignored -> onClose())
            .bounds(layout.left(), layout.footer(), layout.span(), 20).build());
        updateChoices();
    }
    private void update(YakuSelection selection) { selected = selection; choose.accept(selection); init(); }
    private boolean available(Entry entry) {
        return entry.set() == null || selected.enabled(entry.id()) || ClientYakuSets.available(selected.variant()).contains(entry.set());
    }
    private void updateChoices() {
        reset.active = clear.active = editable.getAsBoolean();
        if (ancientToggle != null) ancientToggle.active = editable.getAsBoolean();
        for (int i = 0; i < choices.size(); i++) choices.get(i).active = editable.getAsBoolean() && available(shown.get(page * rows + i));
    }
    private static List<Component> details(YakuSet.Definition award) {
        var reward = award.reward();
        return List.of(reward.type() == YakuSet.RewardType.TAI ? Component.translatable("taiwan.mchjong.tai", reward.closed())
            : Component.translatable("rules.mchjong.yaku_set." + (reward.type() == YakuSet.RewardType.HAN ? "han" : "yakuman"), reward.closed(), reward.open()),
            ClientYakuSets.text(award.description()));
    }
    @Override public void tick() { if (!shownSets.equals(catalog())) init(); else updateChoices(); }
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        layout.paint(graphics, font, title);
        graphics.drawCenteredString(font, (page + 1) + " / " + pages, width / 2, layout.paging() + 6, MahjongUi.MUTED);
        super.render(graphics, x, y, partial);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
