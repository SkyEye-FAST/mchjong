package top.skyeyefast.mchjong.smoke;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import top.skyeyefast.mchjong.item.MahjongBoxMenu;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Vanilla click protocol on a real survival player, including the carrier's lifecycle. */
final class BoxMenuSmoke {
    private BoxMenuSmoke() {}

    static void verify(ServerPlayer player) {
        var inventory = player.getInventory();
        var saved = new ArrayList<ItemStack>();
        for (int i = 0; i < inventory.getContainerSize(); i++) saved.add(inventory.getItem(i).copy());
        int selected = inventory.selected;
        GameType mode = player.gameMode.getGameModeForPlayer();
        float health = player.getHealth();
        var existingDrops = player.serverLevel().getEntitiesOfClass(ItemEntity.class, bounds(player));
        try {
            player.setGameMode(GameType.SURVIVAL);
            for (int owner : new int[]{0, 4, 40}) verifyClicks(player, owner);
            verifyLifecycle(player);
        } finally {
            player.setHealth(health);
            player.closeContainer();
            for (var drop : player.serverLevel().getEntitiesOfClass(ItemEntity.class, bounds(player)))
                if (!existingDrops.contains(drop)) drop.discard();
            for (int i = 0; i < saved.size(); i++) inventory.setItem(i, saved.get(i));
            inventory.selected = selected;
            player.setGameMode(mode);
        }
    }

    private static MahjongBoxMenu open(ServerPlayer player, int owner) {
        player.closeContainer();
        player.getInventory().selected = owner == 40 ? 0 : owner;
        var hand = owner == 40 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        var stack = player.getItemInHand(hand);
        stack.getItem().use(player.serverLevel(), player, hand);
        check(player.containerMenu instanceof MahjongBoxMenu, "Box did not open the server menu");
        return (MahjongBoxMenu) player.containerMenu;
    }

    private static void verifyClicks(ServerPlayer player, int owner) {
        var inventory = player.getInventory();
        inventory.clearContent();
        var box = new ItemStack(MahjongContent.BOX_ITEM);
        inventory.setItem(owner, box);
        inventory.setItem(1, MahjongSupplies.tile(TileData.BLANK, DyeColor.BLUE, 64));
        inventory.setItem(2, new ItemStack(Items.DIAMOND));
        inventory.setItem(3, new ItemStack(MahjongContent.BOX_ITEM));
        inventory.setItem(9, MahjongSupplies.tile(TileData.BLANK, DyeColor.BLUE, 16));
        var sticks = new ItemStack(MahjongContent.POINT_STICK, 8);
        sticks.set(MahjongComponents.POINTS, 1000);
        inventory.setItem(10, sticks);
        var menu = open(player, owner);
        var expected = snapshot(player, menu);
        check(menu.slots.size() == 90 && menu.stillValid(player), "Invalid six-row menu");

        // Use clicked, not only quickMoveStack: vanilla repeats a shift transfer until exhausted.
        menu.clicked(82, 0, ClickType.QUICK_MOVE, player);
        check(inventory.getItem(1).isEmpty() && menu.getSlot(0).getItem().getCount() == 64, "Shift insertion failed");
        conserved(player, menu, expected);
        menu.clicked(83, 0, ClickType.QUICK_MOVE, player);
        menu.clicked(84, 0, ClickType.QUICK_MOVE, player);
        check(inventory.getItem(2).is(Items.DIAMOND) && inventory.getItem(3).is(MahjongContent.BOX_ITEM), "Forbidden insertion");

        if (owner != 40) {
            int carrier = 81 + owner;
            for (var type : new ClickType[]{ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.THROW, ClickType.CLONE})
                for (int button : new int[]{0, 1}) menu.clicked(carrier, button, type, player);
            for (int button = 0; button < 9; button++) menu.clicked(carrier, button, ClickType.SWAP, player);
            menu.clicked(carrier, 40, ClickType.SWAP, player);
            player.setGameMode(GameType.CREATIVE);
            menu.clicked(carrier, 2, ClickType.CLONE, player);
            player.setGameMode(GameType.SURVIVAL);
        }
        for (int slot : new int[]{0, 1, 54, 82}) menu.clicked(slot, owner, ClickType.SWAP, player);
        check(menu.getCarried().isEmpty() && inventory.getItem(owner) == box, "Owner-slot lock was bypassed");
        conserved(player, menu, expected);

        // Ordinary right/left clicks, then both drag modes, with the owner included in the drag.
        menu.clicked(0, 1, ClickType.PICKUP, player);
        check(menu.getCarried().getCount() == 32 && menu.getSlot(0).getItem().getCount() == 32, "Right click did not split");
        menu.clicked(1, 0, ClickType.PICKUP, player);
        menu.clicked(1, 0, ClickType.PICKUP, player);
        drag(menu, player, 0, owner == 40 ? new int[]{1, 2} : new int[]{1, 81 + owner, 2});
        check(menu.getSlot(1).getItem().getCount() == 16 && menu.getSlot(2).getItem().getCount() == 16, "Even drag failed");
        conserved(player, menu, expected);
        menu.clicked(1, 0, ClickType.PICKUP, player);
        drag(menu, player, 1, new int[]{1, 3, 4});
        check(menu.getCarried().getCount() == 13 && menu.getSlot(3).getItem().getCount() == 1, "Single-item drag failed");
        menu.clicked(5, 0, ClickType.PICKUP, player);
        menu.clicked(5, 0, ClickType.PICKUP, player);
        menu.clicked(5, 0, ClickType.PICKUP_ALL, player);
        check(menu.getCarried().getCount() == 64, "Double click did not gather a full stack");
        conserved(player, menu, expected);
        menu.clicked(6, 0, ClickType.PICKUP, player);

        // Another box on the cursor or hotbar cannot nest in the open carrier.
        menu.clicked(84, 0, ClickType.PICKUP, player);
        menu.clicked(7, 0, ClickType.PICKUP, player);
        check(menu.getCarried().is(MahjongContent.BOX_ITEM) && !menu.getSlot(7).hasItem(), "Nested box accepted");
        menu.clicked(84, 0, ClickType.PICKUP, player);
        menu.clicked(7, 3, ClickType.SWAP, player);
        check(!menu.getSlot(7).hasItem(), "Hotbar swap nested a box");

        // Unlocked hotbar and offhand swaps remain useful for actual supplies.
        menu.clicked(6, 1, ClickType.SWAP, player);
        check(inventory.getItem(1).getCount() == 64, "Valid hotbar extraction blocked");
        menu.clicked(6, 1, ClickType.SWAP, player);
        if (owner != 40) {
            menu.clicked(6, 40, ClickType.SWAP, player);
            check(inventory.getItem(40).getCount() == 64, "Valid offhand extraction blocked");
            menu.clicked(6, 40, ClickType.SWAP, player);
        }
        menu.clicked(55, 0, ClickType.QUICK_MOVE, player);
        check(inventory.getItem(10).isEmpty(), "Point-stick shift insertion failed");
        conserved(player, menu, expected);

        for (int slot = 0; slot < MahjongSupplies.BOX_SLOTS; slot++) menu.clicked(slot, 0, ClickType.QUICK_MOVE, player);
        check(MahjongSupplies.contents(box).stream().allMatch(ItemStack::isEmpty), "Extraction did not persist immediately");
        check(inventory.getItem(owner) == box, "Extraction replaced the owner");
        conserved(player, menu, expected);
        player.closeContainer();
        check(!menu.stillValid(player), "Closed menu remained valid");
        conserved(player, menu, expected);
    }

    private static void verifyLifecycle(ServerPlayer player) {
        var inventory = player.getInventory();
        inventory.clearContent();
        var box = new ItemStack(MahjongContent.BOX_ITEM);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(java.util.List.of(
            MahjongSupplies.tile(TileData.BLANK, DyeColor.BLUE, 32))));
        inventory.setItem(0, box);
        var menu = open(player, 0);
        var expected = snapshot(player, menu);
        inventory.setItem(0, ItemStack.EMPTY);
        inventory.setItem(40, box);
        check(!menu.stillValid(player), "Detached carrier retained a live menu");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        check(menu.quickMoveStack(player, 0).isEmpty(), "Detached menu extracted cached supplies");
        inventory.setItem(40, ItemStack.EMPTY);
        inventory.setItem(0, box);
        check(!menu.stillValid(player), "Returning the carrier revived an obsolete menu");
        conserved(player, menu, expected);

        menu = open(player, 0);
        menu.clicked(0, 1, ClickType.PICKUP, player);
        player.closeContainer();
        check(menu.getCarried().isEmpty(), "Closing lost the cursor return transaction");
        var before = box.copy();
        menu.getSlot(0).setByPlayer(ItemStack.EMPTY);
        check(ItemStack.matches(before, box), "Closed menu wrote back stale contents");
        conserved(player, menu, expected);

        menu = open(player, 0);
        menu.clicked(0, 1, ClickType.PICKUP, player);
        float health = player.getHealth();
        player.setHealth(0);
        check(!menu.stillValid(player), "Dead player retained a live menu");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        player.closeContainer();
        player.setHealth(health);
        check(!menu.stillValid(player), "Restoring health revived an obsolete menu");
        conserved(player, menu, expected);
    }

    private static void drag(AbstractContainerMenu menu, ServerPlayer player, int mode, int[] slots) {
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, mode), ClickType.QUICK_CRAFT, player);
        for (int slot : slots) menu.clicked(slot, AbstractContainerMenu.getQuickcraftMask(1, mode), ClickType.QUICK_CRAFT, player);
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, mode), ClickType.QUICK_CRAFT, player);
    }

    private static AABB bounds(ServerPlayer player) { return new AABB(player.blockPosition()).inflate(8); }

    private static Map<CompoundTag, Integer> snapshot(ServerPlayer player, AbstractContainerMenu menu) {
        Map<CompoundTag, Integer> result = new HashMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) count(player, result, player.getInventory().getItem(i));
        count(player, result, menu.getCarried());
        for (var drop : player.serverLevel().getEntitiesOfClass(ItemEntity.class, bounds(player))) count(player, result, drop.getItem());
        return result;
    }

    private static void count(ServerPlayer player, Map<CompoundTag, Integer> result, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack identity = stack.copyWithCount(1);
        if (stack.is(MahjongContent.BOX_ITEM)) {
            identity.remove(DataComponents.CONTAINER);
            stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyStream()
                .forEach(item -> count(player, result, item));
        }
        result.merge((CompoundTag) identity.save(player.registryAccess()), stack.getCount(), Integer::sum);
    }

    private static void conserved(ServerPlayer player, AbstractContainerMenu menu, Map<CompoundTag, Integer> expected) {
        check(expected.equals(snapshot(player, menu)), "Container operation changed the item/component multiset");
        if (menu.stillValid(player)) {
            ItemStack box = player.getItemInHand(player.getOffhandItem().is(MahjongContent.BOX_ITEM)
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            var stored = MahjongSupplies.contents(box);
            for (int slot = 0; slot < MahjongSupplies.BOX_SLOTS; slot++)
                check(ItemStack.matches(stored.get(slot), menu.getSlot(slot).getItem()), "Menu did not persist slot " + slot);
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
