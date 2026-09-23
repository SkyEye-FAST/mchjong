package top.skyeyefast.mchjong.smoke;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.client.TileRenderTypes;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.MahjongContent;

/** One display-only sheet exercises native item models alongside their resolved materials. */
final class MaterialPaletteSmoke extends Screen {
    MaterialPaletteSmoke() { super(Component.literal("Material review")); }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xff26343b);
        int left = (width - 300) / 2, top = (height - 270) / 2;
        graphics.text(font, "Tile bodies / native items", left, top, 0xfff1eee3, false);
        for (var material : TileMaterial.values()) {
            int x = left + material.ordinal() * 50;
            item(graphics, MahjongSupplies.tile(new TileData(4, material, false), 1), x, top + 16);
            graphics.text(font, material.getSerializedName(), x, top + 52, 0xfff1eee3, false);
            int color = material.color();
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TileRenderTypes.bodyTexture(material),
                x + 4, top + 66, 0, 0, 24, 24, 16, 16, 16, 16, color);
        }
        graphics.text(font, "Dyes / native 16px and 2x", left, top + 100, 0xfff1eee3, false);
        var dyes = new net.minecraft.world.item.Item[]{MahjongContent.MAHJONG_DYE,
            MahjongContent.CREATIVE_MAHJONG_DYE, MahjongContent.RED_DORA_DYE, MahjongContent.UNDO_DYE};
        for (int i = 0; i < dyes.length; i++) {
            var stack = new ItemStack(dyes[i]);
            item(graphics, stack, left + i * 75, top + 116);
            graphics.item(stack, left + i * 75 + 36, top + 132);
        }
        graphics.text(font, "Furniture / native models", left, top + 160, 0xfff1eee3, false);
        var furniture = new net.minecraft.world.item.Item[]{MahjongContent.TABLE_ITEM, MahjongContent.AUTO_TABLE_ITEM,
            MahjongContent.STOOL_ITEM, MahjongContent.CLOTH_ITEM, MahjongContent.DICE};
        for (int i = 0; i < furniture.length; i++) item(graphics, new ItemStack(furniture[i]), left + i * 60, top + 176);
        graphics.text(font, "Dice / complete face textures", left, top + 218, 0xfff1eee3, false);
        for (int face = 1; face <= 6; face++) graphics.blit(
            net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, MahjongContent.id("textures/item/dice_" + face + ".png"),
            left + (face - 1) * 50 + 4, top + 234, 0, 0, 24, 24, 32, 32, 32, 32);
    }

    private static void item(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(2, 2);
        graphics.item(stack, 0, 0);
        graphics.pose().popMatrix();
    }

    @Override public boolean isPauseScreen() { return false; }
}
