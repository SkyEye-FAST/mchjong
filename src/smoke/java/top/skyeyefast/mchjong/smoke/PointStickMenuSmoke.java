package top.skyeyefast.mchjong.smoke;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.PointStickMenu;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableEquipment;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Real-player native transactions, including hand delivery between distinct drawers. */
final class PointStickMenuSmoke {
    private PointStickMenuSmoke() {}

    static void verify(ServerPlayer player) {
        var inventory = player.getInventory();
        var saved = java.util.stream.IntStream.range(0, inventory.getContainerSize()).mapToObj(i -> inventory.getItem(i).copy()).toList();
        var position = player.position();
        var mode = player.gameMode.getGameModeForPlayer();
        var level = player.serverLevel();
        var pos = new BlockPos(14, 64, 12);
        var bounds = new AABB(pos).inflate(5);
        var priorDrops = level.getEntitiesOfClass(ItemEntity.class, bounds);
        int selected = inventory.selected;
        try {
            player.closeContainer();
            player.setGameMode(GameType.SURVIVAL);
            inventory.clearContent();
            player.teleportTo(level, pos.getX() + .5, pos.getY(), pos.getZ() + 3.5, 180, 30);
            level.setBlock(pos, MahjongContent.TABLE.defaultBlockState(), 3);
            MahjongContent.TABLE.setPlacedBy(level, pos, MahjongContent.TABLE.defaultBlockState(), player, new ItemStack(MahjongContent.TABLE_ITEM));
            var table = (MahjongTableBlockEntity) level.getBlockEntity(pos);
            // Use the actual side collider and block dispatch, including occupancy cells.
            for (int side = 0; side < 4; side++) {
                var drawer = TableGeometry.drawerBounds(side);
                var hit = TableGeometry.world(pos, drawer.getCenter());
                var block = BlockPos.containing(hit);
                check(level.getBlockState(block).getShape(level, block).bounds().maxY >= .835, "Drawer collider missing");
                level.getBlockState(block).useWithoutItem(level, player,
                    new BlockHitResult(hit, TableGeometry.SIDES[side], block, false));
                check(player.containerMenu instanceof PointStickMenu, "Side drawer did not open through world interaction");
                player.closeContainer();
            }
            inventory.setItem(0, stick(1000, 12));
            inventory.setItem(1, stick(100, 10));
            inventory.setItem(2, stick(5000, 4));
            inventory.setItem(3, new ItemStack(Items.STONE));
            inventory.setItem(4, new ItemStack(MahjongContent.POINT_STICK));
            var menu = open(player, table, 0);
            var expected = snapshot(player, table, menu, bounds);
            check(menu.quickMoveStack(player, 66).isEmpty() && menu.quickMoveStack(player, 67).isEmpty(), "Drawer accepted stone or unmarked sticks");
            for (int index = 63; index <= 65; index++) menu.quickMoveStack(player, index);
            check(menu.totalPoints(0) == 33000, "Multiple denominations were not counted exactly");
            menu.clicked(0, 1, ClickType.PICKUP, player);
            check(menu.getCarried().getCount() == 6, "Right-click did not split point sticks");
            player.closeContainer();
            check(!menu.stillValid(player) && menu.quickMoveStack(player, 0).isEmpty(), "Closed drawer retained authority");
            menu = open(player, table, 1);
            int carriedSlot = PointStickMenu.DRAWER_SLOTS;
            while (carriedSlot < menu.slots.size() && !PointStickMenu.validStick(menu.getSlot(carriedSlot).getItem())) carriedSlot++;
            check(carriedSlot < menu.slots.size(), "Closing did not return the split sticks to inventory");
            menu.quickMoveStack(player, carriedSlot);
            check(table.equipment().drawer(0).getItem(0).getCount() == 6 && menu.totalPoints(1) == 6000,
                "Hand delivery did not transfer the selected amount to the recipient drawer");
            menu.clicked(9, 0, ClickType.PICKUP, player);
            menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, player);
            for (int slot : new int[]{9, 10, 11}) menu.clicked(slot, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, player);
            menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, player);
            menu.clicked(10, 0, ClickType.SWAP, player);
            menu.clicked(10, 0, ClickType.SWAP, player);
            menu.clicked(9, 0, ClickType.PICKUP, player);
            menu.clicked(11, 0, ClickType.PICKUP_ALL, player);
            menu.clicked(9, 0, ClickType.PICKUP, player);
            menu.clicked(9, 0, ClickType.THROW, player);
            check(expected.equals(snapshot(player, table, menu, bounds)), "Native drawer operations changed the item/component multiset");
            // Closing with a carried stack returns it through the vanilla inventory path.
            menu.clicked(0, 0, ClickType.PICKUP, player);
            player.closeContainer();
            check(expected.equals(snapshot(player, table, menu, bounds)), "Closing lost or duplicated carried sticks");
            menu = open(player, table, 0);
            player.teleportTo(level, pos.getX() + 12, pos.getY(), pos.getZ(), 0, 0);
            check(!menu.stillValid(player), "Remote player retained drawer access");
            player.teleportTo(level, pos.getX() + .5, pos.getY(), pos.getZ() + 3.5, 180, 30);
            check(!menu.stillValid(player), "Returning revived an expired drawer menu");
            player.closeContainer();
            player.setGameMode(GameType.SPECTATOR);
            table.openSticks(player, 0);
            check(!(player.containerMenu instanceof PointStickMenu), "Spectator obtained drawer access");
            player.setGameMode(GameType.SURVIVAL);
            menu = open(player, table, 0);
            level.removeBlockEntity(pos);
            check(!menu.stillValid(player), "Detached drawer retained inventory authority");
            level.setBlockEntity(table);
            check(!menu.stillValid(player), "Reattached table revived a stale drawer menu");
            player.closeContainer();
            var beforeSave = new CompoundTag();
            table.equipment().save(beforeSave, player.registryAccess());
            var appearance = table.getUpdateTag(player.registryAccess());
            check(!appearance.contains("stick_drawers"), "Public chunk updates leaked drawer contents");
            table.loadWithComponents(appearance, player.registryAccess());
            var afterSave = new CompoundTag();
            table.equipment().save(afterSave, player.registryAccess());
            check(beforeSave.equals(afterSave), "Appearance update erased point sticks");
            verifySeatedPayments(player, table, bounds);
        } finally {
            player.stopRiding();
            player.closeContainer();
            level.removeBlock(pos, false);
            level.removeBlock(TableGeometry.stool(pos, 0), false);
            for (var drop : level.getEntitiesOfClass(ItemEntity.class, bounds)) if (!priorDrops.contains(drop)) drop.discard();
            for (int slot = 0; slot < saved.size(); slot++) inventory.setItem(slot, saved.get(slot));
            inventory.selected = selected;
            player.setGameMode(mode);
            player.teleportTo(level, position.x, position.y, position.z, 0, 0);
        }
    }

    private static void verifySeatedPayments(ServerPlayer player, MahjongTableBlockEntity table, AABB bounds) {
        player.closeContainer();
        table.equipment().boxes().setItem(0, top.skyeyefast.mchjong.item.MahjongSupplies.completeBox(
            top.skyeyefast.mchjong.item.TileMaterial.BONE, net.minecraft.world.item.DyeColor.BLUE));
        table.useEquipment(player, new ItemStack(MahjongContent.CLOTH_ITEM));
        player.serverLevel().setBlock(TableGeometry.stool(table.getBlockPos(), 0), MahjongContent.STOOL.defaultBlockState(), 3);
        table.sit(player, 0);
        var game = table.participantGame(player);
        check(game != null, "Payment fixture did not obtain an authenticated seat");
        var recipient = new java.util.UUID(8418, 91);
        check(game.join(recipient, "Recipient", 1), "Recipient could not join the payment fixture");
        SeatingFixtures.startPositioned(game, player.getUUID(), recipient);
        check(game.phase() == top.skyeyefast.mchjong.engine.Game.Phase.SHUFFLE, "Payment fixture did not start");
        check(table.equipment().drawer(0).getItem(8).isEmpty() && table.equipment().drawer(1).getItem(8).isEmpty(), "Payment test slots occupied");
        table.equipment().drawer(0).setItem(8, stick(1000, 3));
        var menu = open(player, table, 0);
        var expected = snapshot(player, table, menu, bounds);
        check(menu.canWithdraw(0) && !menu.canWithdraw(1) && menu.canWithdraw(2), "Owner/recipient/practice withdrawal permissions differ from seats");
        menu.clicked(8, 1, ClickType.PICKUP, player);
        menu.clicked(17, 0, ClickType.PICKUP, player);
        check(menu.getSlot(8).getItem().getCount() == 1 && menu.getSlot(17).getItem().getCount() == 2,
            "Seated hand delivery did not transfer exactly two sticks");
        for (var type : new ClickType[]{ClickType.PICKUP, ClickType.SWAP, ClickType.THROW}) {
            menu.clicked(17, 0, type, player);
            check(menu.getCarried().isEmpty() && menu.getSlot(17).getItem().getCount() == 2, "Recipient withdrawal bypass: " + type);
        }
        check(menu.quickMoveStack(player, 17).isEmpty(), "Quick move took another human's sticks");
        menu.clicked(8, 0, ClickType.PICKUP, player);
        menu.clicked(17, 0, ClickType.PICKUP_ALL, player);
        check(menu.getSlot(17).getItem().getCount() == 2, "Collect-all took another human's sticks");
        check(expected.equals(snapshot(player, table, menu, bounds)), "Seated transfer changed the physical currency multiset");
        menu.broadcastChanges();
        check(menu.score(0) == game.points(0) && menu.score(1) == game.points(1), "Settlement reference did not reach the native menu");
        // Native menu data travels as signed shorts; reconstruct both a large positive and negative score.
        var receiver = new PointStickMenu(211, player.getInventory());
        receiver.setData(2, (short) 100000);
        receiver.setData(3, (short) (100000 >>> 16));
        receiver.setData(4, (short) -10000);
        receiver.setData(5, (short) (-10000 >>> 16));
        check(receiver.score(0) == 100000 && receiver.score(1) == -10000, "Native menu truncated signed scores");
    }

    private static void act(top.skyeyefast.mchjong.engine.Game game, java.util.UUID player,
            top.skyeyefast.mchjong.engine.Action.Type type) {
        var view = game.view(player);
        int index = java.util.stream.IntStream.range(0, view.actions().size())
            .filter(i -> view.actions().get(i).type() == type).findFirst().orElseThrow();
        check(game.act(player, view.decision(), index), "Payment fixture action rejected: " + type);
    }

    static ItemStack stick(int points, int count) {
        var result = new ItemStack(MahjongContent.POINT_STICK, count);
        result.set(MahjongComponents.POINTS, points);
        return result;
    }

    static PointStickMenu open(ServerPlayer player, MahjongTableBlockEntity table, int side) {
        player.closeContainer();
        table.openSticks(player, side);
        if (!(player.containerMenu instanceof PointStickMenu menu)) throw new IllegalStateException("Point-stick drawer did not open");
        return menu;
    }

    static void put(ServerPlayer player, MahjongTableBlockEntity table, int side, ItemStack source) {
        var menu = open(player, table, side);
        menu.setCarried(source);
        for (int slot = side * TableEquipment.STICK_SLOTS; slot < (side + 1) * TableEquipment.STICK_SLOTS && !menu.getCarried().isEmpty(); slot++)
            if (!menu.getSlot(slot).hasItem()) menu.clicked(slot, 0, ClickType.PICKUP, player);
        check(menu.getCarried().isEmpty(), "Point-stick fixture did not fit");
        player.closeContainer();
        player.getInventory().setChanged();
    }

    static void take(ServerPlayer player, MahjongTableBlockEntity table, int side) {
        var menu = open(player, table, side);
        for (int slot = side * TableEquipment.STICK_SLOTS; slot < (side + 1) * TableEquipment.STICK_SLOTS; slot++) if (menu.getSlot(slot).hasItem())
            check(!menu.quickMoveStack(player, slot).isEmpty(), "Point-stick fixture could not be recovered");
        player.closeContainer();
    }

    private static Map<CompoundTag, Integer> snapshot(ServerPlayer player, MahjongTableBlockEntity table,
            PointStickMenu menu, AABB bounds) {
        var result = new HashMap<CompoundTag, Integer>();
        java.util.function.Consumer<ItemStack> count = stack -> {
            if (!stack.isEmpty()) result.merge((CompoundTag) stack.copyWithCount(1).save(player.registryAccess()), stack.getCount(), Integer::sum);
        };
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) count.accept(player.getInventory().getItem(slot));
        for (int side = 0; side < 4; side++) for (int slot = 0; slot < TableEquipment.STICK_SLOTS; slot++)
            count.accept(table.equipment().drawer(side).getItem(slot));
        count.accept(menu.getCarried());
        for (var drop : player.serverLevel().getEntitiesOfClass(ItemEntity.class, bounds)) count.accept(drop.getItem());
        return result;
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
