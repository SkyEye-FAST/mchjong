package top.skyeyefast.mchjong.smoke;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Exercise the native menu's cached recipe/result when only input components change. */
final class StonecutterSmoke {
    private StonecutterSmoke() {}

    static void verify(ServerPlayer player) {
        var inventory = player.getInventory();
        var saved = new ArrayList<ItemStack>();
        for (int i = 0; i < inventory.getContainerSize(); i++) saved.add(inventory.getItem(i).copy());
        var pos = new BlockPos(14, 64, 8);
        var level = player.serverLevel();
        level.setBlock(pos, Blocks.STONECUTTER.defaultBlockState(), 3);
        var menu = new StonecutterMenu(91, inventory, ContainerLevelAccess.create(level, pos));
        try {
            inventory.clearContent();
            inventory.setItem(0, MahjongSupplies.tile(new TileData(-1, TileMaterial.GLASS, false), DyeColor.LIME, 2));
            inventory.setItem(1, MahjongSupplies.tile(TileData.BLANK, DyeColor.ORANGE, 3));
            inventory.setItem(2, MahjongSupplies.tile(new TileData(0, TileMaterial.QUARTZ, false), DyeColor.BLUE, 1));
            menu.clicked(0, 0, ClickType.SWAP, player);
            selectRedFive(menu, player);
            check(MahjongSupplies.tile(menu.getSlot(1).getItem()).material() == TileMaterial.GLASS
                && MahjongSupplies.color(menu.getSlot(1).getItem()) == DyeColor.LIME, "Stonecutter lost the first blank's appearance");

            menu.clicked(0, 1, ClickType.SWAP, player);
            check(!menu.getSlot(1).hasItem() && menu.getSelectedRecipeIndex() == -1, "Component swap retained a stale output");
            selectRedFive(menu, player);
            check(MahjongSupplies.tile(menu.getSlot(1).getItem()).material() == TileMaterial.BONE
                && MahjongSupplies.color(menu.getSlot(1).getItem()) == DyeColor.ORANGE, "Stonecutter used the previous material or color");
            menu.clicked(1, 0, ClickType.PICKUP, player);
            menu.clicked(33, 0, ClickType.PICKUP, player);

            menu.clicked(0, 2, ClickType.SWAP, player);
            check(menu.getNumRecipes() == 0 && !menu.getSlot(1).hasItem(), "Engraved tile retained cached engraving recipes");
            menu.clickMenuButton(player, 0);
            check(!menu.getSlot(1).hasItem(), "A stale recipe index re-engraved a tile");
            menu.clicked(0, 2, ClickType.SWAP, player);
            selectRedFive(menu, player);
            menu.clicked(1, 0, ClickType.QUICK_MOVE, player);
            check(!menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem(), "Repeated shift engraving left an input or ghost result");
            int glass = 0, bone = 0, quartz = 0;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                var stack = inventory.getItem(slot);
                if (!stack.is(MahjongContent.TILE_ITEM)) continue;
                var data = MahjongSupplies.tile(stack);
                switch (data.material()) {
                    case GLASS -> { check(data.blank() && MahjongSupplies.color(stack) == DyeColor.LIME, "Spare glass blanks changed"); glass += stack.getCount(); }
                    case BONE -> { check(data.face() == 4 && data.red() && MahjongSupplies.color(stack) == DyeColor.ORANGE, "Wrong engraved output"); bone += stack.getCount(); }
                    case QUARTZ -> { check(data.face() == 0 && !data.red(), "An already engraved tile changed"); quartz += stack.getCount(); }
                    default -> throw new IllegalStateException("Unexpected tile material");
                }
            }
            check(glass == 2 && bone == 3 && quartz == 1 && menu.getCarried().isEmpty(), "Stonecutting did not conserve physical tiles");
        } finally {
            menu.removed(player);
            for (int i = 0; i < saved.size(); i++) inventory.setItem(i, saved.get(i));
            level.removeBlock(pos, false);
        }
    }

    private static void selectRedFive(StonecutterMenu menu, ServerPlayer player) {
        check(menu.getNumRecipes() == 37, "Blank tile does not expose 37 engravings");
        for (int i = 0; i < menu.getRecipes().size(); i++) if (menu.getRecipes().get(i).id().equals(MahjongContent.id("engrave_tile_34"))) {
            menu.clickMenuButton(player, i);
            check(menu.getSlot(1).getItem().getCount() == 1, "Missing stonecutter result");
            return;
        }
        throw new IllegalStateException("Red-five engraving recipe missing");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
