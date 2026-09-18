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
    private MahjongButton print;
    private TileFacePreset preset = TileFacePreset.KANSAI;
    private final java.util.List<MahjongButton> presets = new java.util.ArrayList<>();
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
        presets.clear();
        for (var choice : TileFacePreset.values()) {
            presets.add(addRenderableWidget(MahjongButton.create(Component.translatable(choice.translationKey()), ignored -> {
                preset = choice;
                for (var candidate : TileFacePreset.values()) presets.get(candidate.ordinal()).selected(candidate == preset);
                print.active = menu.canEngrave(preset);
            }).bounds(leftPos + 196, topPos + 122 + choice.ordinal() * 24, 94, 20).build().selected(choice == preset)));
        }
        print = addRenderableWidget(MahjongButton.create(Component.translatable("box.mchjong.print"), ignored ->
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, preset.ordinal()))
            .bounds(leftPos + 196, topPos + 184, 94, 20).build().primary());
        print.active = menu.canEngrave(preset);
    }

    @Override protected void containerTick() {
        super.containerTick();
        print.active = menu.canEngrave(preset);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, MahjongUi.BACKDROP);
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MahjongUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3, MahjongUi.ACCENT);
        graphics.fill(leftPos + 184, topPos + 16, leftPos + 185, topPos + imageHeight - 4, MahjongUi.EDGE);
        for (Slot slot : menu.slots) MahjongUi.slot(graphics, leftPos + slot.x, topPos + slot.y, carrier(slot));
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MahjongUi.text(graphics, font, title, 14, 5, 276, MahjongUi.TEXT, false);
        MahjongUi.text(graphics, font, Component.translatable("box.mchjong.stick_storage"), 14, 104, 160, MahjongUi.MUTED, false);
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
        paragraph(graphics, Component.translatable("box.mchjong.dye_storage"), 70, MahjongUi.TEXT);
        MahjongUi.text(graphics, font, Component.translatable(items.get(MahjongSupplies.DYE_SLOT).is(MahjongContent.CREATIVE_MAHJONG_DYE)
            ? "box.mchjong.unlimited" : "box.mchjong.dye_cost"), 218, 87, 72, MahjongUi.MUTED, false);
        paragraph(graphics, Component.translatable("box.mchjong.preset"), 110, MahjongUi.TEXT);
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
