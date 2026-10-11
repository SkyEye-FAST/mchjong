package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.YakuSet;

/** Catalog choices change the parent draft; only the parent's Apply request changes the table. */
final class YakuSetScreen extends Screen implements TableChildScreen {
    private final Screen parent;
    private final YakuSet selected;
    private final Consumer<YakuSet> choose;
    private final BooleanSupplier editable;
    private List<YakuSet> shown = List.of();
    private final List<MahjongButton> choices = new ArrayList<>();
    private SettingsLayout layout;
    private int page, pages;

    YakuSetScreen(Screen parent, YakuSet selected, Consumer<YakuSet> choose, BooleanSupplier editable) {
        super(Component.translatable("rules.mchjong.yaku_set.title"));
        this.parent = parent; this.selected = selected; this.choose = choose; this.editable = editable;
    }
    @Override public Screen parent() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) {}
    private List<YakuSet> catalog() {
        var result = new ArrayList<YakuSet>();
        result.add(YakuSet.empty(selected.variant())); result.addAll(ClientYakuSets.available(selected.variant()));
        if (!result.contains(selected)) result.add(selected);
        return List.copyOf(result);
    }
    @Override protected void init() {
        clearWidgets(); choices.clear(); shown = catalog();
        layout = SettingsLayout.of(width, height, 0, shown.size() * 24);
        int rows = Math.max(1, (layout.paging() - layout.contentTop()) / 24);
        pages = Math.max(1, (shown.size() + rows - 1) / rows); page = Math.clamp(page, 0, pages - 1);
        for (int i = 0; i < rows && page * rows + i < shown.size(); i++) {
            var set = shown.get(page * rows + i);
            var button = addRenderableWidget(MahjongButton.create(ClientYakuSets.text(set.name()), ignored -> {
                if (editable.getAsBoolean() && available(set)) { choose.accept(set); onClose(); }
            }).bounds(layout.left(), layout.contentTop() + i * 24, layout.span() - 24, 20).build().selected(set.equals(selected)));
            choices.add(button);
            addRenderableWidget(MahjongButton.create(Component.literal("?"), ignored -> minecraft.setScreen(
                new TableHelpScreen(this, ClientYakuSets.text(set.name()), () -> details(set))))
                .bounds(layout.left() + layout.span() - 20, layout.contentTop() + i * 24, 20, 20).build());
        }
        var previous = addRenderableWidget(MahjongButton.create(Component.literal("‹"), ignored -> { page--; init(); })
            .bounds(layout.left(), layout.paging(), 28, 20).build()); previous.active = page > 0;
        var next = addRenderableWidget(MahjongButton.create(Component.literal("›"), ignored -> { page++; init(); })
            .bounds(layout.left() + layout.span() - 28, layout.paging(), 28, 20).build()); next.active = page + 1 < pages;
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.back"), ignored -> onClose())
            .bounds(layout.left(), layout.footer(), layout.span(), 20).build());
        updateChoices();
    }
    private boolean available(YakuSet set) { return set.isEmpty() || ClientYakuSets.available(set.variant()).contains(set); }
    private void updateChoices() {
        int rows = Math.max(1, (layout.paging() - layout.contentTop()) / 24);
        for (int i = 0; i < choices.size(); i++) choices.get(i).active = editable.getAsBoolean() && available(shown.get(page * rows + i));
    }
    private static List<Component> details(YakuSet set) {
        var result = new ArrayList<Component>();
        if (set.isEmpty()) result.add(Component.translatable("rules.mchjong.yaku_set.none.description"));
        for (var award : set.awards()) {
            var reward = award.reward();
            result.add(ClientYakuSets.text(award.name()));
            result.add(reward.type() == YakuSet.RewardType.TAI ? Component.translatable("taiwan.mchjong.tai", reward.closed())
                : Component.translatable("rules.mchjong.yaku_set." + (reward.type() == YakuSet.RewardType.HAN ? "han" : "yakuman"), reward.closed(), reward.open()));
            result.add(ClientYakuSets.text(award.description()));
        }
        return List.copyOf(result);
    }
    @Override public void tick() { if (!shown.equals(catalog())) init(); else updateChoices(); }
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        layout.paint(graphics, font, title);
        graphics.drawCenteredString(font, (page + 1) + " / " + pages, width / 2, layout.paging() + 6, MahjongUi.MUTED);
        super.render(graphics, x, y, partial);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
