package top.skyeyefast.mchjong.smoke;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.item.MahjongTableMenu;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Native server menu transactions shared by equipment lifecycle fixtures. */
final class TableStorageSmoke {
    private TableStorageSmoke() {}

    static void verify(ServerPlayer player) {
        var inventory = player.getInventory();
        var saved = java.util.stream.IntStream.range(0, inventory.getContainerSize()).mapToObj(i -> inventory.getItem(i).copy()).toList();
        var position = player.position();
        var mode = player.gameMode.getGameModeForPlayer();
        int selected = inventory.getSelectedSlot();
        var level = player.level();
        var pos = new net.minecraft.core.BlockPos(12, 64, 8);
        var bounds = new net.minecraft.world.phys.AABB(pos).inflate(8);
        var priorDrops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds);
        try {
            player.closeContainer();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            inventory.clearContent();
            level.setBlock(pos, top.skyeyefast.mchjong.world.MahjongContent.TABLE.defaultBlockState(), 3);
            var table = (MahjongTableBlockEntity) level.getBlockEntity(pos);
            player.teleportTo(level, pos.getX() + .5, pos.getY(), pos.getZ() + 3.5, java.util.Set.of(), 180, 30, false);
            for (int slot = 0; slot < 3; slot++) inventory.setItem(slot,
                top.skyeyefast.mchjong.item.MahjongSupplies.completeBox(top.skyeyefast.mchjong.item.TileMaterial.values()[slot],
                    net.minecraft.world.item.DyeColor.BLUE));
            inventory.setItem(3, new ItemStack(net.minecraft.world.item.Items.STONE));
            var nested = new ItemStack(top.skyeyefast.mchjong.world.MahjongContent.BOX_ITEM);
            nested.set(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.of(
                    new ItemStack(top.skyeyefast.mchjong.world.MahjongContent.BOX_ITEM))));
            inventory.setItem(4, nested);
            var menu = open(player, table);
            var expected = snapshot(player, table, menu, bounds);
            check(menu.quickMoveStack(player, 32).isEmpty() && menu.quickMoveStack(player, 33).isEmpty(),
                "Table accepted a non-case or nested case");
            menu.quickMoveStack(player, 29);
            menu.quickMoveStack(player, 30);
            check(table.equipment().boxes().getContainerSize() == 2 && menu.quickMoveStack(player, 31).isEmpty(),
                "Table storage accepted a third case");
            check(menu.getSlot(0).getItem().getCount() == 1 && menu.getSlot(1).getItem().getCount() == 1,
                "Case slots stacked cases");
            menu.clicked(0, 2, ContainerInput.SWAP, player);
            menu.clicked(0, 2, ContainerInput.SWAP, player);
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            menu.clicked(-999, net.minecraft.world.inventory.AbstractContainerMenu.getQuickcraftMask(0, 0), ContainerInput.QUICK_CRAFT, player);
            for (int slot : new int[]{0, 1})
                menu.clicked(slot, net.minecraft.world.inventory.AbstractContainerMenu.getQuickcraftMask(1, 0), ContainerInput.QUICK_CRAFT, player);
            menu.clicked(-999, net.minecraft.world.inventory.AbstractContainerMenu.getQuickcraftMask(2, 0), ContainerInput.QUICK_CRAFT, player);
            menu.clicked(1, 0, ContainerInput.THROW, player);
            check(expected.equals(snapshot(player, table, menu, bounds)), "Table transfers changed the item/component multiset");
            player.closeContainer();
            check(!menu.stillValid(player) && menu.quickMoveStack(player, 0).isEmpty(), "Closed storage remained usable");
            menu = open(player, table);
            player.teleportTo(level, pos.getX() + 10, pos.getY(), pos.getZ(), java.util.Set.of(), 0, 0, false);
            check(!menu.stillValid(player), "Remote player retained access to table storage");
            player.teleportTo(level, pos.getX() + .5, pos.getY(), pos.getZ() + 3.5, java.util.Set.of(), 180, 30, false);
            check(!menu.stillValid(player), "Returning to range revived an old storage menu");
            player.closeContainer();
            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            table.openStorage(player);
            check(!(player.containerMenu instanceof MahjongTableMenu), "Spectator obtained an editable table menu");
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            menu = open(player, table);
            level.removeBlockEntity(pos);
            check(!menu.stillValid(player) && menu.quickMoveStack(player, 0).isEmpty(), "Detached table retained inventory authority");
            level.setBlockEntity(table);
            check(!menu.stillValid(player), "Reattaching a table revived its invalidated menu");
        } finally {
            player.closeContainer();
            level.removeBlock(pos, false);
            for (var drop : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds))
                if (!priorDrops.contains(drop)) drop.discard();
            for (int slot = 0; slot < saved.size(); slot++) inventory.setItem(slot, saved.get(slot));
            inventory.setSelectedSlot(selected);
            player.setGameMode(mode);
            player.teleportTo(level, position.x, position.y, position.z, java.util.Set.of(), 0, 0, false);
        }
    }

    private static java.util.Map<net.minecraft.nbt.CompoundTag, Integer> snapshot(ServerPlayer player, MahjongTableBlockEntity table,
            MahjongTableMenu menu, net.minecraft.world.phys.AABB bounds) {
        var result = new java.util.HashMap<net.minecraft.nbt.CompoundTag, Integer>();
        java.util.function.Consumer<ItemStack> count = stack -> {
            if (!stack.isEmpty()) result.merge((net.minecraft.nbt.CompoundTag) ItemStack.CODEC.encodeStart(
                net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, player.registryAccess()),
                stack.copyWithCount(1)).getOrThrow(), stack.getCount(), Integer::sum);
        };
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) count.accept(player.getInventory().getItem(slot));
        for (int slot = 0; slot < 2; slot++) count.accept(table.equipment().boxes().getItem(slot));
        count.accept(menu.getCarried());
        for (var drop : player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds)) count.accept(drop.getItem());
        return result;
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    static MahjongTableMenu open(ServerPlayer player, MahjongTableBlockEntity table) {
        player.closeContainer();
        table.openStorage(player);
        if (!(player.containerMenu instanceof MahjongTableMenu menu)) throw new IllegalStateException("Table storage did not open");
        return menu;
    }

    static void put(ServerPlayer player, MahjongTableBlockEntity table, int slot, ItemStack source) {
        var menu = open(player, table);
        if (menu.slots.get(slot).hasItem()) throw new IllegalStateException("Fixture storage slot is occupied");
        menu.setCarried(source);
        menu.clicked(slot, 0, ContainerInput.PICKUP, player);
        if (!menu.getCarried().isEmpty()) throw new IllegalStateException("Table did not accept the physical box");
        player.closeContainer();
        player.getInventory().setChanged();
    }

    static void take(ServerPlayer player, MahjongTableBlockEntity table, int slot) {
        var menu = open(player, table);
        if (menu.quickMoveStack(player, slot).isEmpty()) throw new IllegalStateException("Cannot retrieve table box");
        player.closeContainer();
    }

    static void emptyHand(ServerPlayer player) {
        for (int slot = 0; slot < 9; slot++) if (player.getInventory().getItem(slot).isEmpty()) {
            player.getInventory().setSelectedSlot(slot);
            return;
        }
        for (int slot = 9; slot < 36; slot++) if (player.getInventory().getItem(slot).isEmpty()) {
            player.getInventory().setItem(slot, player.getInventory().removeItemNoUpdate(8));
            player.getInventory().setSelectedSlot(8);
            return;
        }
        throw new IllegalStateException("Fixture inventory is full");
    }
}
