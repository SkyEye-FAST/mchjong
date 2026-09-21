package top.skyeyefast.mchjong.smoke;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.network.TableControlPayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlock;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.MahjongTableItem;
import top.skyeyefast.mchjong.world.TableGeometry;
import top.skyeyefast.mchjong.world.TableSpaceBlock;

/** Placement, interaction, persistence, break callbacks and explosions in an actual ServerLevel. */
final class EquipmentLifecycleSmoke {
    private static final BlockPos CENTER = new BlockPos(24, 64, 0);
    private static final AABB AREA = new AABB(CENTER).inflate(8);
    private EquipmentLifecycleSmoke() {}

    static void verify(ServerPlayer player) {
        var inventory = player.getInventory();
        var saved = new ArrayList<ItemStack>();
        for (int i = 0; i < inventory.getContainerSize(); i++) saved.add(inventory.getItem(i).copy());
        var position = player.position();
        var mode = player.gameMode.getGameModeForPlayer();
        int selected = inventory.selected;
        var rule = player.serverLevel().getGameRules().getRule(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY);
        boolean decay = rule.get();
        try {
            player.closeContainer();
            player.setGameMode(GameType.SURVIVAL);
            rule.set(false, player.server);
            for (var block : List.of(MahjongContent.TABLE, MahjongContent.AUTO_TABLE))
                for (int destruction = 0; destruction < 3; destruction++) verifyTable(player, block, destruction);
        } finally {
            player.stopRiding();
            player.closeContainer();
            player.setShiftKeyDown(false);
            for (var entity : player.serverLevel().getEntitiesOfClass(ItemEntity.class, AREA)) entity.discard();
            for (int i = 0; i < saved.size(); i++) inventory.setItem(i, saved.get(i));
            inventory.selected = selected;
            player.setGameMode(mode);
            rule.set(decay, player.server);
            player.teleportTo(player.serverLevel(), position.x, position.y, position.z, 0, 0);
        }
    }

    private static void verifyTable(ServerPlayer player, MahjongTableBlock block, int destruction) {
        var level = player.serverLevel();
        var inventory = player.getInventory();
        inventory.clearContent();
        inventory.selected = 0;
        player.teleportTo(level, CENTER.getX() + .5, 64, 3.5, 180, 30);
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
            level.setBlock(CENTER.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        ItemStack furniture = new ItemStack(block);
        furniture.set(MahjongComponents.WOOD, FurnitureWood.BAMBOO);
        ItemStack expectedFurniture = furniture.copy();
        inventory.setItem(0, furniture);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(CENTER), Direction.UP, CENTER.below(), false);
        int radius = TableGeometry.FOOTPRINT_RADIUS;
        check(radius == 1, "Both tables must retain a 3x3 footprint");
        for (int x : new int[]{-radius, radius}) for (int z : new int[]{-radius, radius}) for (int y = 0; y <= 1; y++) {
            var obstacle = CENTER.offset(x, y, z);
            level.setBlock(obstacle, Blocks.STONE.defaultBlockState(), 3);
            check(((MahjongTableItem) furniture.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, furniture, hit))
                == net.minecraft.world.InteractionResult.FAIL, "An obstructed outer corner allowed placement");
            check(ItemStack.matches(expectedFurniture, furniture) && level.getBlockState(CENTER).isAir(),
                "Rejected placement consumed the item or placed a partial table");
            level.removeBlock(obstacle, false);
        }
        int outside = radius + 1;
        for (int x : new int[]{-outside, outside}) for (int z : new int[]{-outside, outside}) for (int y = 0; y <= 1; y++)
            level.setBlock(CENTER.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
        check(((MahjongTableItem) furniture.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, furniture, hit)).consumesAction(),
            "Survival table placement failed");
        check(furniture.isEmpty(), "Placement did not consume the table item");
        for (int x = -outside; x <= outside; x++) for (int z = -outside; z <= outside; z++) {
            if (Math.abs(x) <= radius && Math.abs(z) <= radius) continue;
            check(!level.getBlockState(CENTER.offset(x, 0, z)).is(MahjongContent.SPACE),
                "Compact placement reserved a cell outside its 3x3 footprint");
            if (Math.abs(x) == outside && Math.abs(z) == outside) for (int y = 0; y <= 1; y++) {
                var obstacle = CENTER.offset(x, y, z);
                check(level.getBlockState(obstacle).is(Blocks.STONE), "Placement overwrote a neighboring block");
                level.removeBlock(obstacle, false);
            }
        }
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) if (x != 0 || z != 0) {
            var pos = CENTER.offset(x, 0, z);
            var state = level.getBlockState(pos);
            check(state.is(MahjongContent.SPACE) && TableSpaceBlock.center(pos, state).equals(CENTER),
                "Table footprint lost a cell or its center mapping");
        }
        var table = (MahjongTableBlockEntity) level.getBlockEntity(CENTER);
        check(table.wood() == FurnitureWood.BAMBOO, "Placed table lost its component wood");
        level.setBlock(TableGeometry.stool(CENTER, 0), MahjongContent.STOOL.defaultBlockState(), 3);
        var stool = TableGeometry.stool(CENTER, 0);
        level.getBlockState(stool).useWithoutItem(level, player,
            new BlockHitResult(Vec3.atCenterOf(stool), Direction.UP, stool, false));
        check(player.isPassenger(), "The stool did not find its table");
        Game game = table.participantGame(player);
        check(game != null && !game.equipped() && game.view(player.getUUID()).actions().stream()
            .noneMatch(action -> action.type() == Action.Type.READY), "Empty table could start a game");

        var complete = PointStickMenuSmoke.stockedBox(TileMaterial.GLASS, DyeColor.CYAN);
        var shortSet = complete.copy();
        var contents = MahjongSupplies.contents(shortSet);
        contents.getFirst().shrink(1);
        shortSet.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        var mixedColor = complete.copy();
        contents = MahjongSupplies.contents(mixedColor);
        contents.getFirst().set(DataComponents.BASE_COLOR, DyeColor.BLUE);
        mixedColor.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        var mixedMaterial = complete.copy();
        contents = MahjongSupplies.contents(mixedMaterial);
        contents.getFirst().set(MahjongComponents.TILE, new TileData(0, TileMaterial.BONE, false));
        mixedMaterial.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        for (var invalid : List.of(shortSet, mixedColor, mixedMaterial)) {
            var before = invalid.copy();
            TableStorageSmoke.put(player, table, 0, invalid);
            check(ItemStack.matches(before, table.equipment().boxes().getItem(0)) && table.equipment().deck() == null,
                "An incomplete case was altered or treated as a playable set");
            TableStorageSmoke.take(player, table, 0);
            check(countInventory(player, before) == 1, "The incomplete case could not be recovered intact");
        }
        var first = MahjongSupplies.completeBox(TileMaterial.BONE, DyeColor.BLUE);
        var firstExpected = first.copy();
        TableStorageSmoke.put(player, table, 0, first);
        check(first.isEmpty(), "Storage transfer did not move exactly one box");
        var installed = complete.copy();
        TableStorageSmoke.put(player, table, 1, installed);
        TableStorageSmoke.take(player, table, 0);
        TableStorageSmoke.take(player, table, 1);
        TableStorageSmoke.put(player, table, 0, findInventory(player, complete));
        check(installed.isEmpty() && countInventory(player, firstExpected) == 1, "Moving the cases lost or duplicated their contents");
        check(!table.participantGame(player).equipped(), "Table started without a cloth");
        var green = new ItemStack(MahjongContent.CLOTH_ITEM);
        var greenExpected = green.copy();
        table.useEquipment(player, green);
        var red = new ItemStack(MahjongContent.CLOTH_ITEM);
        red.set(DataComponents.BASE_COLOR, DyeColor.RED);
        var redExpected = red.copy();
        table.useEquipment(player, red);
        check(green.isEmpty() && red.isEmpty() && countInventory(player, greenExpected) == 1, "Replacing cloth did not conserve items");

        PointStickMenuSmoke.stockDrawers(table);
        game = table.participantGame(player);
        SeatingFixtures.startPositioned(game, player.getUUID());
        check(game.phase() != Game.Phase.LOBBY, "Equipped table did not start");
        game.validate();
        var lockedCloth = redExpected.copy();
        table.useEquipment(player, lockedCloth);
        table.openStorage(player);
        check(!(player.containerMenu instanceof top.skyeyefast.mchjong.item.MahjongTableMenu), "Equipment storage opened during a game");
        check(ItemStack.matches(lockedCloth, redExpected)
            && ItemStack.matches(complete, table.equipment().boxes().getItem(0)), "Equipment changed during a game");
        TableStorageSmoke.emptyHand(player);
        player.setShiftKeyDown(true);
        table.removeEquipment(player, Direction.NORTH);
        check(ItemStack.matches(complete, table.equipment().boxes().getItem(0)), "A running game's box was removed");
        player.setShiftKeyDown(false);
        var sticks = new ItemStack(MahjongContent.POINT_STICK, 3);
        sticks.set(MahjongComponents.POINTS, 1000);
        var expectedSticks = sticks.copy();
        String scoresBefore = TableNetworking.JSON.toJson(game);
        if (!table.automatic()) {
            var menu = PointStickMenuSmoke.open(player, table, 0);
            menu.setCarried(sticks);
            menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
            check(menu.getCarried().getCount() == 3 && table.equipment().drawer(0).getItem(0).isEmpty(), "Running table accepted external sticks");
            menu.setCarried(ItemStack.EMPTY);
            player.closeContainer();
        } else {
            table.openSticks(player, 0);
            check(!(player.containerMenu instanceof top.skyeyefast.mchjong.item.PointStickMenu), "Automatic table opened a manual drawer");
        }
        check(scoresBefore.equals(TableNetworking.JSON.toJson(game)), "Physical sticks changed authoritative points/deposits");
        long exitToken = game.view(player.getUUID()).decision();
        table.control(player, new TableControlPayload(CENTER, game.tableId(), TableControlPayload.Operation.REQUEST_EXIT, exitToken - 1, false));
        check(game.phase() != Game.Phase.LOBBY && player.isPassenger(), "A stale exit token changed the game");
        table.control(player, new TableControlPayload(CENTER, game.tableId(), TableControlPayload.Operation.REQUEST_EXIT, exitToken, false));
        check(game.phase() == Game.Phase.LOBBY && !player.isPassenger(), "Exiting did not release the running table");
        // Native inventory transfers can occupy any hotbar slot.
        if (!table.automatic()) {
            for (int seat = 0; seat < 4; seat++) PointStickMenuSmoke.take(player, table, seat);
            check(table.equipment().drawer(0).isEmpty(), "Drawers did not return their starting stocks");
            player.getInventory().add(expectedSticks.copy());
        }
        TableStorageSmoke.emptyHand(player);
        player.setShiftKeyDown(true);
        table.removeEquipment(player, Direction.UP);
        check(countInventory(player, redExpected) == 1 && !table.equipment().hasCloth(), "Cloth was not removable after exit");
        player.setShiftKeyDown(false);
        TableStorageSmoke.take(player, table, 0);
        check(countInventory(player, complete) == 1 && table.equipment().boxes().isEmpty(), "Box was not removable after exit");
        TableStorageSmoke.put(player, table, 0, findInventory(player, complete));
        TableStorageSmoke.put(player, table, 1, findInventory(player, firstExpected));
        table.useEquipment(player, findInventory(player, redExpected));
        if (!table.automatic()) PointStickMenuSmoke.put(player, table, 0, findInventory(player, expectedSticks).split(3));

        var saved = table.saveWithoutMetadata(level.registryAccess());
        var loaded = new MahjongTableBlockEntity(CENTER, block.defaultBlockState());
        loaded.setLevel(level);
        loaded.loadWithComponents(saved, level.registryAccess());
        check(saved.equals(loaded.saveWithoutMetadata(level.registryAccess())), "Equipment did not survive save/load");
        level.setBlockEntity(loaded);
        player.teleportTo(level, CENTER.getX() + 20, 64, .5, 0, 0);
        if (destruction == 0) level.destroyBlock(CENTER, true);
        else if (destruction == 1) level.destroyBlock(CENTER.offset(radius, 0, radius), true);
        else level.explode(null, CENTER.getX() + .5, CENTER.getY() + .5, CENTER.getZ() + .5, 4, Level.ExplosionInteraction.TNT);
        check(!(level.getBlockEntity(CENTER) instanceof MahjongTableBlockEntity), "Destruction did not remove the table");
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++)
            check(!level.getBlockState(CENTER.offset(x, 0, z)).is(MahjongContent.SPACE), "Orphaned table-space block after destruction");
        loaded.dropEquipment();
        var drops = level.getEntitiesOfClass(ItemEntity.class, AREA);
        var expectedDrops = new ArrayList<>(List.of(expectedFurniture, complete, firstExpected, redExpected));
        if (!loaded.automatic()) expectedDrops.add(expectedSticks);
        for (var expected : expectedDrops) {
            int count = drops.stream().map(ItemEntity::getItem).filter(stack -> ItemStack.isSameItemSameComponents(stack, expected))
                .mapToInt(ItemStack::getCount).sum();
            check(count == expected.getCount(), "Destruction " + destruction + " returned " + count + " rather than " + expected);
        }
        for (var drop : drops) drop.discard();
        level.removeBlock(TableGeometry.stool(CENTER, 0), false);
    }

    private static int countInventory(ServerPlayer player, ItemStack expected) {
        return java.util.stream.IntStream.range(0, player.getInventory().getContainerSize()).mapToObj(player.getInventory()::getItem)
            .filter(stack -> ItemStack.isSameItemSameComponents(stack, expected)).mapToInt(ItemStack::getCount).sum();
    }
    private static ItemStack findInventory(ServerPlayer player, ItemStack expected) {
        return java.util.stream.IntStream.range(0, player.getInventory().getContainerSize()).mapToObj(player.getInventory()::getItem)
            .filter(stack -> ItemStack.isSameItemSameComponents(stack, expected)).findFirst().orElseThrow();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
