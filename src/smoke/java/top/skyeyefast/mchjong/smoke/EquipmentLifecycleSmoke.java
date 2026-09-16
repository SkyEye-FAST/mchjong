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
        check(((MahjongTableItem) furniture.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, furniture, hit)).consumesAction(),
            "Survival table placement failed");
        check(furniture.isEmpty(), "Placement did not consume the table item");
        var table = (MahjongTableBlockEntity) level.getBlockEntity(CENTER);
        check(table.wood() == FurnitureWood.BAMBOO, "Placed table lost its component wood");
        level.setBlock(TableGeometry.stool(CENTER, 0), MahjongContent.STOOL.defaultBlockState(), 3);
        table.sit(player, 0);
        Game game = table.participantGame(player);
        check(game != null && !game.equipped() && game.view(player.getUUID()).actions().stream()
            .noneMatch(action -> action.type() == Action.Type.READY || action.type() == Action.Type.PRACTICE), "Empty table could start a game");

        var complete = MahjongSupplies.completeBox(TileMaterial.GLASS, DyeColor.CYAN);
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
            table.useEquipment(player, invalid);
            check(ItemStack.matches(before, invalid) && !table.equipment().hasBox(), "Invalid physical set was consumed or installed");
        }
        var first = MahjongSupplies.completeBox(TileMaterial.BONE, DyeColor.BLUE);
        var firstExpected = first.copy();
        table.useEquipment(player, first);
        check(first.isEmpty(), "Initial box installation did not consume exactly one box");
        var installed = complete.copy();
        table.useEquipment(player, installed);
        check(installed.isEmpty() && countInventory(player, firstExpected) == 1, "Replacing a box lost or duplicated the previous contents");
        var green = new ItemStack(MahjongContent.CLOTH_ITEM);
        var greenExpected = green.copy();
        table.useEquipment(player, green);
        var red = new ItemStack(MahjongContent.CLOTH_ITEM);
        red.set(DataComponents.BASE_COLOR, DyeColor.RED);
        var redExpected = red.copy();
        table.useEquipment(player, red);
        check(green.isEmpty() && red.isEmpty() && countInventory(player, greenExpected) == 1, "Replacing cloth did not conserve items");

        game = table.participantGame(player);
        var view = game.view(player.getUUID());
        int practice = java.util.stream.IntStream.range(0, view.actions().size())
            .filter(i -> view.actions().get(i).type() == Action.Type.PRACTICE).findFirst().orElseThrow();
        check(game.act(player.getUUID(), view.decision(), practice) && game.phase() != Game.Phase.LOBBY, "Equipped table did not start");
        game.validate();
        var lockedCloth = redExpected.copy();
        var lockedBox = firstExpected.copy();
        table.useEquipment(player, lockedCloth);
        table.useEquipment(player, lockedBox);
        check(ItemStack.matches(lockedCloth, redExpected) && ItemStack.matches(lockedBox, firstExpected)
            && ItemStack.matches(complete, table.equipment().boxCopy()), "Equipment changed during a game");
        inventory.selected = 8;
        player.setShiftKeyDown(true);
        table.removeEquipment(player, Direction.NORTH);
        check(ItemStack.matches(complete, table.equipment().boxCopy()), "A running game's box was removed");
        player.setShiftKeyDown(false);
        var sticks = new ItemStack(MahjongContent.POINT_STICK, 3);
        sticks.set(MahjongComponents.POINTS, 1000);
        var expectedSticks = sticks.copy();
        String scoresBefore = TableNetworking.JSON.toJson(game);
        for (int i = 0; i < 3; i++) table.useEquipment(player, sticks);
        check(sticks.isEmpty() && table.equipment().stickCount(0) == 3, "Physical point-stick placement failed");
        check(scoresBefore.equals(TableNetworking.JSON.toJson(game)), "Physical sticks changed authoritative points/deposits");
        long exitToken = game.view(player.getUUID()).decision();
        table.control(player, new TableControlPayload(CENTER, game.tableId(), TableControlPayload.Operation.REQUEST_EXIT, exitToken - 1, false));
        check(game.phase() != Game.Phase.LOBBY && player.isPassenger(), "A stale exit token changed the game");
        table.control(player, new TableControlPayload(CENTER, game.tableId(), TableControlPayload.Operation.REQUEST_EXIT, exitToken, false));
        check(game.phase() == Game.Phase.LOBBY && !player.isPassenger(), "Exiting did not release the running table");
        // Replacement returns can occupy the selected slot: empty both hands before removal.
        inventory.selected = 8;
        player.setShiftKeyDown(true);
        table.removeEquipment(player, Direction.UP);
        check(countInventory(player, expectedSticks) == 3 && table.equipment().stickCount(0) == 0, "Point tray did not return its physical stack");
        table.removeEquipment(player, Direction.UP);
        check(countInventory(player, redExpected) == 1 && !table.equipment().hasCloth(), "Cloth was not removable after exit");
        table.removeEquipment(player, Direction.NORTH);
        check(countInventory(player, complete) == 1 && !table.equipment().hasBox(), "Box was not removable after exit");
        player.setShiftKeyDown(false);
        table.useEquipment(player, findInventory(player, complete));
        table.useEquipment(player, findInventory(player, redExpected));
        var returnedSticks = findInventory(player, expectedSticks);
        for (int i = 0; i < 3; i++) table.useEquipment(player, returnedSticks);

        var saved = table.saveWithoutMetadata(level.registryAccess());
        var loaded = new MahjongTableBlockEntity(CENTER, block.defaultBlockState());
        loaded.setLevel(level);
        loaded.loadWithComponents(saved, level.registryAccess());
        check(saved.equals(loaded.saveWithoutMetadata(level.registryAccess())), "Equipment did not survive save/load");
        level.setBlockEntity(loaded);
        player.teleportTo(level, CENTER.getX() + 20, 64, .5, 0, 0);
        if (destruction == 0) level.destroyBlock(CENTER, true);
        else if (destruction == 1) level.destroyBlock(CENTER.east(), true);
        else level.explode(null, CENTER.getX() + .5, CENTER.getY() + .5, CENTER.getZ() + .5, 4, Level.ExplosionInteraction.TNT);
        check(!(level.getBlockEntity(CENTER) instanceof MahjongTableBlockEntity), "Destruction did not remove the table");
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            check(!level.getBlockState(CENTER.offset(x, 0, z)).is(MahjongContent.SPACE), "Orphaned table-space block after destruction");
        loaded.dropEquipment();
        var drops = level.getEntitiesOfClass(ItemEntity.class, AREA);
        for (var expected : List.of(expectedFurniture, complete, redExpected, expectedSticks)) {
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
