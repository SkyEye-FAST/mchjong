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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.network.TableActionPayload;
import top.skyeyefast.mchjong.network.TableControlPayload;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlock;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Actual survival inventory transactions, world saves, placeholder destruction and explosions. */
final class EquipmentSmoke {
    private static final BlockPos POS = new BlockPos(20, 64, 0);
    private EquipmentSmoke() {}

    static void verify(ServerPlayer player) {
        var inventory = player.getInventory();
        var saved = new ArrayList<ItemStack>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) saved.add(inventory.getItem(slot).copy());
        int selected = inventory.selected;
        var position = player.position();
        float yaw = player.getYRot(), pitch = player.getXRot();
        var mode = player.gameMode.getGameModeForPlayer();
        boolean decay = player.level().getGameRules().getBoolean(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY);
        try {
            player.closeContainer();
            player.setGameMode(GameType.SURVIVAL);
            player.level().getGameRules().getRule(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY).set(false, player.getServer());
            for (var block : List.of(MahjongContent.TABLE, MahjongContent.AUTO_TABLE))
                for (int destruction = 0; destruction < 3; destruction++) verifyTable(player, block, destruction);
        } finally {
            player.stopRiding();
            player.setShiftKeyDown(false);
            for (int slot = 0; slot < saved.size(); slot++) inventory.setItem(slot, saved.get(slot));
            inventory.selected = selected;
            player.setGameMode(mode);
            player.level().getGameRules().getRule(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY).set(decay, player.getServer());
            player.teleportTo(player.serverLevel(), position.x, position.y, position.z, yaw, pitch);
        }
    }

    private static void verifyTable(ServerPlayer player, MahjongTableBlock block, int destruction) {
        var level = player.serverLevel();
        var bounds = new AABB(POS).inflate(8);
        var existing = level.getEntitiesOfClass(ItemEntity.class, bounds);
        var inventory = player.getInventory();
        inventory.clearContent();
        try {
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
                level.setBlock(POS.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            var furniture = new ItemStack(block);
            furniture.set(MahjongComponents.WOOD, FurnitureWood.WARPED);
            level.setBlock(POS, block.defaultBlockState(), 3);
            block.setPlacedBy(level, POS, block.defaultBlockState(), player, furniture);
            for (int seat = 0; seat < 4; seat++) level.setBlock(TableGeometry.stool(POS, seat), MahjongContent.STOOL.defaultBlockState(), 3);
            player.teleportTo(level, POS.getX() + .5, POS.getY(), POS.getZ() + 3.5, 180, 30);
            var table = (MahjongTableBlockEntity) level.getBlockEntity(POS);
            table.sit(player, 0);
            var game = table.participantGame(player);
            check(game != null && game.view(player.getUUID()).actions().stream().noneMatch(a ->
                a.type() == Action.Type.READY || a.type() == Action.Type.PRACTICE), "Empty table offered a playable game");

            var original = MahjongSupplies.completeBox(TileMaterial.GLASS, DyeColor.BLUE);
            var replacement = MahjongSupplies.completeBox(TileMaterial.QUARTZ, DyeColor.CYAN);
            var cloth = new ItemStack(MahjongContent.CLOTH_ITEM);
            cloth.set(DataComponents.BASE_COLOR, DyeColor.LIME);
            var sticks = new ItemStack(MahjongContent.POINT_STICK, 8);
            sticks.set(MahjongComponents.POINTS, 1000);
            inventory.setItem(0, original.copy());
            inventory.setItem(1, cloth.copy());
            inventory.setItem(2, sticks.copy());
            inventory.setItem(3, replacement.copy());
            inventory.selected = 0;
            var edge = POS.east();
            level.getBlockState(edge).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(edge), Direction.UP, edge, false));
            check(inventory.getItem(0).isEmpty(), "Placeholder interaction did not consume the installed box");
            table.useEquipment(player, inventory.getItem(1));
            table.useEquipment(player, inventory.getItem(3));
            check(find(inventory, original) >= 0 && ItemStack.matches(replacement, table.equipment().boxCopy()), "Box replacement lost the old set");
            inventory.selected = 8;
            player.setShiftKeyDown(true);
            check(table.removeEquipment(player, Direction.UP) && table.removeEquipment(player, Direction.EAST), "Lobby unloading failed");
            check(!table.equipment().hasBox() && !table.equipment().hasCloth(), "Unloading retained installed appearances");
            player.setShiftKeyDown(false);
            table.useEquipment(player, inventory.getItem(find(inventory, replacement)));
            table.useEquipment(player, inventory.getItem(find(inventory, cloth)));

            var saved = table.saveWithoutMetadata(level.registryAccess());
            var appearance = table.getUpdatePacket().getTag();
            check(!appearance.contains("game") && !appearance.contains("box") && !appearance.contains("cloth"), "Private equipment leaked into a block packet");
            table.loadWithComponents(appearance, level.registryAccess());
            check(saved.getString("game").equals(table.saveWithoutMetadata(level.registryAccess()).getString("game"))
                && ItemStack.matches(replacement, table.equipment().boxCopy()), "Public update destroyed private state");
            level.removeBlockEntity(POS);
            table = new MahjongTableBlockEntity(POS, block.defaultBlockState());
            table.setLevel(level);
            table.loadWithComponents(saved, level.registryAccess());
            level.setBlockEntity(table);
            check(table.wood() == FurnitureWood.WARPED && table.equipment().clothColor() == DyeColor.LIME
                && ItemStack.matches(replacement, table.equipment().boxCopy()), "World reload lost equipment or components");
            if (block == MahjongContent.AUTO_TABLE) act(table, player, Action.Type.CHANGE_RULE, RuleSet.MAHJONG_SOUL_3.ordinal());
            act(table, player, Action.Type.PRACTICE, -1);
            game = table.participantGame(player);
            check(game.phase() == (table.automatic() ? Game.Phase.TURN : Game.Phase.SHUFFLE), "Wrong table handling mode");
            if (table.automatic()) check(game.view(null).wall().size() == 108, "Three-player game did not use 108 physical tiles");
            var rejectedBox = original.copy();
            table.useEquipment(player, rejectedBox);
            table.useEquipment(player, cloth.copy());
            player.setShiftKeyDown(true);
            check(!table.removeEquipment(player, Direction.EAST) && !table.removeEquipment(player, Direction.UP), "Active equipment lock was bypassed");
            player.setShiftKeyDown(false);
            check(ItemStack.matches(original, rejectedBox) && ItemStack.matches(replacement, table.equipment().boxCopy()), "Locked replacement consumed equipment");
            var before = game.view(player.getUUID());
            table.useEquipment(player, inventory.getItem(2));
            var after = game.view(player.getUUID());
            check(before.seats().stream().map(s -> s.points()).toList().equals(after.seats().stream().map(s -> s.points()).toList())
                && before.riichiSticks() == after.riichiSticks(), "Physical point stick changed engine scores or deposits");
            player.setShiftKeyDown(true);
            check(table.removeEquipment(player, Direction.UP), "Cannot recover the physical stick during a game");
            player.setShiftKeyDown(false);
            table.control(player, new TableControlPayload(POS, game.tableId(), TableControlPayload.Operation.REQUEST_EXIT, after.decision(), false));
            check(game.phase() == Game.Phase.LOBBY && !player.isPassenger(), "Exit did not release the player");
            check(ItemStack.matches(replacement, table.equipment().boxCopy())
                && MahjongSupplies.tileCount(MahjongSupplies.contents(table.equipment().boxCopy())) == 136, "Exit lost the full set or unused sanma tiles");
            for (int count = 0; count < 3; count++) table.useEquipment(player, inventory.getItem(2));
            player.teleportTo(level, POS.getX() + 12.5, POS.getY(), POS.getZ() + 12.5, 0, 0);
            if (destruction == 0) level.destroyBlock(POS, true);
            else if (destruction == 1) level.destroyBlock(POS.offset(1, 0, 1), true);
            else level.explode(null, POS.getX() + .5, POS.getY() + .8, POS.getZ() + .5, 3, Level.ExplosionInteraction.BLOCK);
            table.dropEquipment();
            var drops = level.getEntitiesOfClass(ItemEntity.class, bounds).stream().filter(e -> !existing.contains(e)).map(ItemEntity::getItem).toList();
            check(count(drops, block.asItem()) == 1 && drops.stream().filter(s -> s.is(block.asItem()))
                .allMatch(s -> s.get(MahjongComponents.WOOD) == FurnitureWood.WARPED), "Furniture drop was duplicated or lost its wood: "
                    + block + " destruction=" + destruction + " drops=" + drops.stream().map(s -> s + " wood=" + s.get(MahjongComponents.WOOD)).toList());
            check(count(drops, MahjongContent.BOX_ITEM) == 1 && drops.stream().anyMatch(s -> ItemStack.matches(s, replacement)), "Destroyed table did not return exactly one complete box");
            check(count(drops, MahjongContent.CLOTH_ITEM) == 1 && drops.stream().anyMatch(s -> ItemStack.matches(s, cloth)), "Destroyed table lost its cloth");
            check(count(drops, MahjongContent.POINT_STICK) == 3 && inventory.getItem(2).getCount() == 5, "Destroyed table duplicated or lost point sticks");
            check(find(inventory, original) >= 0, "Destruction changed previously returned equipment");
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                check(!level.getBlockState(POS.offset(x, 0, z)).is(MahjongContent.SPACE), "Orphaned table space remained");
        } finally {
            player.stopRiding();
            player.setShiftKeyDown(false);
            level.destroyBlock(POS, false);
            for (int seat = 0; seat < 4; seat++) level.removeBlock(TableGeometry.stool(POS, seat), false);
            for (var drop : level.getEntitiesOfClass(ItemEntity.class, bounds)) if (!existing.contains(drop)) drop.discard();
        }
    }

    private static void act(MahjongTableBlockEntity table, ServerPlayer player, Action.Type type, int rule) {
        var view = table.participantGame(player).view(player.getUUID());
        for (int i = 0; i < view.actions().size(); i++) {
            var action = view.actions().get(i);
            if (action.type() == type && (rule < 0 || action.tiles().contains(rule))) {
                table.act(player, new TableActionPayload(table.getBlockPos(), view.tableId(), view.decision(), i));
                return;
            }
        }
        throw new IllegalStateException("Required action missing: " + type);
    }

    private static int find(net.minecraft.world.entity.player.Inventory inventory, ItemStack expected) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (ItemStack.matches(inventory.getItem(slot), expected)) return slot;
        return -1;
    }
    private static int count(List<ItemStack> stacks, Item item) { return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum(); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
