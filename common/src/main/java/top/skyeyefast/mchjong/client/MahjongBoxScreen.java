package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import top.skyeyefast.mchjong.item.MahjongBoxMenu;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Dedicated compartments and server-authorized face printing over native inventory synchronization. */
public final class MahjongBoxScreen extends AbstractContainerScreen<MahjongBoxMenu> {
    private boolean openingPreset;
    private MahjongButton dyeBack;
    private MahjongButton presetChoice;
    private MahjongButton backChoice;
    private MahjongButton apply;
    private TileFacePreset selectedFace;
    private net.minecraft.resources.ResourceLocation selectedBack;
    public net.minecraft.client.gui.navigation.ScreenRectangle browserBounds() {
        return new net.minecraft.client.gui.navigation.ScreenRectangle(leftPos, topPos, imageWidth, imageHeight);
    }
    public MahjongBoxScreen(MahjongBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 304;
        imageHeight = 216;
    }

    @Override protected void init() {
        super.init();
        topPos = Math.min(topPos, height - imageHeight - 24);
        presetChoice = addRenderableWidget(MahjongButton.create(Component.empty(), ignored ->
            openPreset(new MahjongBoxFaceScreen(this)))
            .bounds(leftPos + 196, topPos + 126, 94, 20).build());
        dyeBack = addRenderableWidget(MahjongButton.create(Component.translatable("box.mchjong.dye_back"), ignored ->
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MahjongBoxMenu.DYE_BACK_BUTTON))
            .bounds(leftPos + 196, topPos + 136, 94, 20).build().primary());
        backChoice = addRenderableWidget(MahjongButton.create(Component.empty(), ignored ->
            openPreset(new MahjongBoxBackScreen(this)))
            .bounds(leftPos + 196, topPos + 160, 94, 20).build());
        apply = addRenderableWidget(MahjongButton.create(Component.translatable("box.mchjong.apply"), ignored ->
            minecraft.getConnection().send(top.skyeyefast.mchjong.network.PayloadPackets.serverbound(
                new top.skyeyefast.mchjong.network.BoxPrintPayload(menu.containerId, facePreset(), backPreset()))))
            .bounds(leftPos + 196, topPos + 190, 94, 20).build().primary());
        updateActions();
    }

    private void openPreset(net.minecraft.client.gui.screens.Screen screen) {
        openingPreset = true;
        try { minecraft.setScreen(screen); }
        finally { openingPreset = false; }
    }

    @Override public void removed() {
        if (!openingPreset) super.removed();
    }

    @Override protected void containerTick() {
        super.containerTick();
        updateActions();
    }

    private void updateActions() {
        presetChoice.setMessage(Component.translatable("box.mchjong.preset_choice", TileFacePresets.label(facePreset())));
        var reagent = menu.getSlot(MahjongSupplies.DYE_SLOT).getItem();
        boolean printing = MahjongSupplies.mahjongDye(reagent);
        presetChoice.visible = printing;
        presetChoice.active = !TileFacePresets.choices().isEmpty();
        boolean undo = reagent.is(MahjongContent.UNDO_DYE);
        dyeBack.visible = undo || reagent.getItem() instanceof net.minecraft.world.item.DyeItem;
        dyeBack.active = dyeBack.visible && menu.canDyeBack();
        dyeBack.setMessage(Component.translatable(undo ? "box.mchjong.undo_dye_back" : "box.mchjong.dye_back"));
        backChoice.setMessage(Component.translatable("box.mchjong.back_choice", TileBackPresets.label(backPreset())));
        backChoice.visible = printing;
        backChoice.active = MahjongSupplies.tileCount(menu.items()) > 0 && printing;
        apply.visible = printing;
        apply.active = printing && menu.canApplyAppearance(facePreset(), backPreset());
        if (getFocused() instanceof net.minecraft.client.gui.components.AbstractWidget widget && !widget.visible)
            setFocused(null);
    }

    net.minecraft.resources.ResourceLocation backPreset() {
        if (selectedBack != null) return selectedBack;
        return menu.items().stream().filter(stack -> stack.is(MahjongContent.TILE_ITEM))
            .findFirst().map(MahjongSupplies::backPreset).orElse(TileBackPresets.DEFAULT);
    }

    TileFacePreset facePreset() {
        if (selectedFace != null) return selectedFace;
        return menu.items().stream().filter(stack -> stack.is(MahjongContent.TILE_ITEM))
            .findFirst().map(MahjongSupplies::facePreset).orElse(TileFacePreset.KANSAI);
    }

    MahjongBoxMenu boxMenu() { return menu; }
    void selectFace(TileFacePreset preset) { selectedFace = preset; }
    void selectBack(net.minecraft.resources.ResourceLocation preset) { selectedBack = preset; }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, MahjongUi.BACKDROP);
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MahjongUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3, MahjongUi.ACCENT);
        graphics.fill(leftPos + 189, topPos + 16, leftPos + 190, topPos + 104, MahjongUi.EDGE);
        graphics.fill(leftPos + 189, topPos + 134, leftPos + 190, topPos + imageHeight - 8, MahjongUi.EDGE);
        graphics.fill(leftPos + 196, topPos + 112, leftPos + 290, topPos + 113, MahjongUi.EDGE);
        for (Slot slot : menu.slots) MahjongUi.slot(graphics, leftPos + slot.x, topPos + slot.y, carrier(slot));
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MahjongUi.text(graphics, font, title, 14, 5, 276, MahjongUi.TEXT, false);
        MahjongUi.text(graphics, font, Component.translatable("box.mchjong.stick_storage"), 14, 104, 136, MahjongUi.MUTED, false);
        MahjongUi.text(graphics, font, Component.translatable("item.mchjong.dice"), 154, 104, 40, MahjongUi.MUTED, true);
        MahjongUi.text(graphics, font, playerInventoryTitle, 14, 132, 160, MahjongUi.MUTED, false);
        var items = menu.items();
        int tiles = MahjongSupplies.tileCount(items);
        int sticks = items.stream().filter(stack -> stack.is(MahjongContent.POINT_STICK)).mapToInt(stack -> stack.getCount()).sum();
        var deck = MahjongSupplies.deck(items);
        boolean ready = deck != null;
        MahjongUi.text(graphics, font, Component.translatable("box.mchjong.tiles", tiles), 196, 18, 94, MahjongUi.TEXT, false);
        MahjongUi.text(graphics, font, Component.translatable("box.mchjong.sticks", sticks), 196, 34, 94, MahjongUi.MUTED, false);
        MahjongUi.text(graphics, font, ready ? Component.translatable(deck.sanma() ? "box.mchjong.sanma_set" : "box.mchjong.set", Component.translatable(deck.redFives().translationKey()))
            : Component.translatable("box.mchjong.incomplete"), 196, 50, 94,
            ready ? MahjongUi.POSITIVE : MahjongUi.ACCENT, false);
        MahjongUi.text(graphics, font, Component.translatable("box.mchjong.dye_storage"), 196, 70, 94, MahjongUi.TEXT, false);
        var reagent = items.get(MahjongSupplies.DYE_SLOT);
        if (!reagent.isEmpty()) MahjongUi.text(graphics, font, Component.translatable(reagent.is(MahjongContent.CREATIVE_MAHJONG_DYE)
            ? "box.mchjong.unlimited" : "box.mchjong.dye_cost"), 218, 87, 72, MahjongUi.MUTED, false);
        if (presetChoice.visible) {
            MahjongUi.text(graphics, font, Component.translatable("box.mchjong.preset"), 196, 115, 94, MahjongUi.TEXT, false);
            MahjongUi.text(graphics, font, Component.translatable("box.mchjong.back_title"), 196, 149, 94, MahjongUi.TEXT, false);
        } else if (dyeBack.visible) {
            MahjongUi.text(graphics, font, reagent.getHoverName(), 196, 122, 94, MahjongUi.TEXT, false);
        } else paragraph(graphics, Component.translatable("box.mchjong.insert_dye"), 124, MahjongUi.MUTED);
    }

    private int paragraph(GuiGraphics graphics, Component text, int y, int color) {
        for (var line : font.split(text, 94)) {
            graphics.drawString(font, line, 196, y, color, false);
            y += 10;
        }
        return y;
    }

    private boolean carrier(Slot slot) {
        return slot.container == minecraft.player.getInventory() && slot.getContainerSlot() == menu.ownerSlot();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoveredSlot != null && carrier(hoveredSlot) && menu.getCarried().isEmpty()) {
            var lines = new java.util.ArrayList<>(getTooltipFromItem(minecraft, hoveredSlot.getItem()));
            lines.add(Component.translatable("box.mchjong.locked").withStyle(net.minecraft.ChatFormatting.GOLD));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else renderTooltip(graphics, mouseX, mouseY);
    }
}
