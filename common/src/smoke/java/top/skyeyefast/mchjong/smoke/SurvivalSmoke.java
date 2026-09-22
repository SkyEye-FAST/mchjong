package top.skyeyefast.mchjong.smoke;

import java.util.List;
import net.minecraft.core.BlockPos;
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
            top.skyeyefast.mchjong.item.MahjongComponents.wood(furniture, FurnitureWood.WARPED);
            table.applyItem(furniture);
            table.equipment().boxes().setItem(0, MahjongSupplies.completeBox(TileMaterial.GLASS, DyeColor.CYAN));
            table.equipment().installCloth(new ItemStack(MahjongContent.CLOTH_ITEM));
            var drops = Block.getDrops(block.defaultBlockState(), level, BlockPos.ZERO, table);
            check(drops.size() == 1 && top.skyeyefast.mchjong.item.MahjongComponents.wood(drops.getFirst()) == FurnitureWood.WARPED, "Table loot lost its wood");
            check(!MahjongSupplies.hasStorage(drops.getFirst()) && drops.getFirst().getTagElement("BlockEntityTag") == null, "Furniture loot duplicated equipment");
            var packet = table.getUpdatePacket();
            check(packet != null && packet.getTag().equals(table.getUpdateTag()), "Wrong appearance packet");
            check(!packet.getTag().contains("game") && !packet.getTag().contains("boxes") && !packet.getTag().contains("cloth"), "Private inventory in appearance packet");
            check("glass".equals(packet.getTag().getString("tile_material")), "Missing glass appearance");
        }
        var stool = new FurnitureBlockEntity(BlockPos.ZERO, MahjongContent.STOOL.defaultBlockState());
        stool.setLevel(level);
        var stack = new ItemStack(MahjongContent.STOOL_ITEM);
        top.skyeyefast.mchjong.item.MahjongComponents.wood(stack, FurnitureWood.CHERRY);
        top.skyeyefast.mchjong.item.MahjongComponents.color(stack, DyeColor.MAGENTA);
        stool.applyItem(stack);
        var drops = Block.getDrops(MahjongContent.STOOL.defaultBlockState(), level, BlockPos.ZERO, stool);
        check(drops.size() == 1 && top.skyeyefast.mchjong.item.MahjongComponents.wood(drops.getFirst()) == FurnitureWood.CHERRY
            && top.skyeyefast.mchjong.item.MahjongComponents.color(drops.getFirst()) == DyeColor.MAGENTA, "Stool loot lost appearance");
        BoxMenuSmoke.verify(player);
        TableStorageSmoke.verify(player);
        PointStickMenuSmoke.verify(player);
        EquipmentSmoke.verify(player);
        EquipmentLifecycleSmoke.verify(player);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
