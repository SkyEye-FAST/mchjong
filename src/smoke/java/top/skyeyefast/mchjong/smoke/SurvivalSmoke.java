package top.skyeyefast.mchjong.smoke;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.MahjongBoxMenu;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.FurnitureBlockEntity;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Checks requiring a real level/player rather than the datapack-only JUnit server. */
final class SurvivalSmoke {
    private SurvivalSmoke() {}

    static void verify(ServerPlayer player) {
        var level = player.serverLevel();
        for (var block : List.of(MahjongContent.TABLE, MahjongContent.AUTO_TABLE)) {
            var table = new MahjongTableBlockEntity(BlockPos.ZERO, block.defaultBlockState());
            table.setLevel(level);
            var furniture = new ItemStack(block);
            furniture.set(MahjongComponents.WOOD, FurnitureWood.WARPED);
            table.applyComponentsFromItemStack(furniture);
            table.equipment().installBox(MahjongSupplies.completeBox(TileMaterial.GLASS, DyeColor.CYAN));
            table.equipment().installCloth(new ItemStack(MahjongContent.CLOTH_ITEM));
            var drops = Block.getDrops(block.defaultBlockState(), level, BlockPos.ZERO, table);
            check(drops.size() == 1 && drops.getFirst().get(MahjongComponents.WOOD) == FurnitureWood.WARPED, "Table loot lost its wood");
            check(!drops.getFirst().has(DataComponents.CONTAINER) && !drops.getFirst().has(DataComponents.BLOCK_ENTITY_DATA), "Furniture loot duplicated equipment");
            var packet = table.getUpdatePacket();
            check(packet != null && packet.getTag().equals(table.getUpdateTag(level.registryAccess())), "Wrong appearance packet");
            check(!packet.getTag().contains("game") && !packet.getTag().contains("box") && !packet.getTag().contains("cloth"), "Private inventory in appearance packet");
            check("glass".equals(packet.getTag().getString("tile_material")), "Missing glass appearance");
        }
        var stool = new FurnitureBlockEntity(BlockPos.ZERO, MahjongContent.STOOL.defaultBlockState());
        stool.setLevel(level);
        var stack = new ItemStack(MahjongContent.STOOL_ITEM);
        stack.set(MahjongComponents.WOOD, FurnitureWood.CHERRY);
        stack.set(DataComponents.BASE_COLOR, DyeColor.MAGENTA);
        stool.applyComponentsFromItemStack(stack);
        var drops = Block.getDrops(MahjongContent.STOOL.defaultBlockState(), level, BlockPos.ZERO, stool);
        check(drops.size() == 1 && drops.getFirst().get(MahjongComponents.WOOD) == FurnitureWood.CHERRY
            && drops.getFirst().get(DataComponents.BASE_COLOR) == DyeColor.MAGENTA, "Stool loot lost appearance");
        verifyBoxMenu(player);
    }

    private static void verifyBoxMenu(ServerPlayer player) {
        var inventory = player.getInventory();
        var saved = new ArrayList<ItemStack>();
        for (int i = 0; i < inventory.getContainerSize(); i++) saved.add(inventory.getItem(i).copy());
        try {
            inventory.clearContent();
            var box = new ItemStack(MahjongContent.BOX_ITEM);
            inventory.setItem(0, box);
            inventory.setItem(1, MahjongSupplies.tile(TileData.BLANK, DyeColor.BLUE, 64));
            inventory.setItem(2, new ItemStack(Items.DIAMOND));
            var menu = new MahjongBoxMenu(19, inventory, 0);
            check(menu.stillValid(player), "New box menu is not valid");
            check(!menu.quickMoveStack(player, 82).isEmpty(), "Quick insertion failed");
            check(inventory.getItem(1).isEmpty() && MahjongSupplies.tileCount(MahjongSupplies.contents(box)) == 64, "Insertion did not conserve tiles");
            check(menu.quickMoveStack(player, 81).isEmpty() && inventory.getItem(0) == box, "Owner box was moved");
            check(menu.quickMoveStack(player, 83).isEmpty() && inventory.getItem(2).is(Items.DIAMOND), "Unrelated item accepted into box");
            menu.clicked(81, 0, ClickType.PICKUP, player);
            menu.clicked(0, 0, ClickType.SWAP, player);
            check(menu.getCarried().isEmpty() && inventory.getItem(0) == box, "Owner-slot lock was bypassed");
            check(!menu.quickMoveStack(player, 0).isEmpty(), "Quick extraction failed");
            check(MahjongSupplies.tileCount(MahjongSupplies.contents(box)) == 0, "Extraction did not update box contents");
            inventory.setItem(0, ItemStack.EMPTY);
            inventory.setItem(40, box);
            check(!menu.stillValid(player), "Detached box retained a live menu");
            var offhandMenu = new MahjongBoxMenu(20, inventory, 40);
            offhandMenu.clicked(0, 40, ClickType.SWAP, player);
            check(inventory.getItem(40) == box && offhandMenu.stillValid(player), "Offhand carrier swap was accepted");
        } finally {
            for (int i = 0; i < saved.size(); i++) inventory.setItem(i, saved.get(i));
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
