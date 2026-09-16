package top.skyeyefast.mchjong.smoke;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.FurnitureBlockEntity;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Checks requiring a real level/player rather than the datapack-only JUnit server. */
final class SurvivalSmoke {
    private SurvivalSmoke() {}

    static boolean ready(ServerPlayer player) {
        var level = player.serverLevel();
        // A FULL chunk alone is insufficient: hidden entity sections cannot be queried for drops.
        level.setChunkForced(0, 0, true);
        level.setChunkForced(1, 0, true);
        return level.isPositionEntityTicking(new BlockPos(8, 64, 8))
            && level.isPositionEntityTicking(new BlockPos(24, 64, 8))
            && level.isPositionEntityTicking(player.blockPosition());
    }

    static void verify(ServerPlayer player) {
        var level = player.serverLevel();
        for (var block : List.of(MahjongContent.TABLE, MahjongContent.AUTO_TABLE)) {
            var table = new MahjongTableBlockEntity(BlockPos.ZERO, block.defaultBlockState());
            table.setLevel(level);
            var furniture = new ItemStack(block);
            furniture.set(MahjongComponents.WOOD, FurnitureWood.WARPED);
            table.applyComponentsFromItemStack(furniture);
            table.equipment().boxes().setItem(0, MahjongSupplies.completeBox(TileMaterial.GLASS, DyeColor.CYAN));
            table.equipment().installCloth(new ItemStack(MahjongContent.CLOTH_ITEM));
            var drops = Block.getDrops(block.defaultBlockState(), level, BlockPos.ZERO, table);
            check(drops.size() == 1 && drops.getFirst().get(MahjongComponents.WOOD) == FurnitureWood.WARPED, "Table loot lost its wood");
            check(!drops.getFirst().has(DataComponents.CONTAINER) && !drops.getFirst().has(DataComponents.BLOCK_ENTITY_DATA), "Furniture loot duplicated equipment");
            var packet = table.getUpdatePacket();
            check(packet != null && packet.getTag().equals(table.getUpdateTag(level.registryAccess())), "Wrong appearance packet");
            check(!packet.getTag().contains("game") && !packet.getTag().contains("boxes") && !packet.getTag().contains("cloth"), "Private inventory in appearance packet");
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
        BoxMenuSmoke.verify(player);
        TableStorageSmoke.verify(player);
        StonecutterSmoke.verify(player);
        EquipmentSmoke.verify(player);
        EquipmentLifecycleSmoke.verify(player);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
