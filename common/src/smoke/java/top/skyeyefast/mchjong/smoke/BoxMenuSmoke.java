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
import net.minecraft.world.inventory.ContainerInput;
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
    private static final int HOTBAR = MahjongSupplies.BOX_SLOTS + 27;
    private BoxMenuSmoke() {}

    static void verify(ServerPlayer player) {
        var inventory = player.getInventory();
        var saved = new ArrayList<ItemStack>();
        for (int i = 0; i < inventory.getContainerSize(); i++) saved.add(inventory.getItem(i).copy());
        int selected = inventory.getSelectedSlot();
        GameType mode = player.gameMode.getGameModeForPlayer();
        float health = player.getHealth();
        var existingDrops = player.level().getEntitiesOfClass(ItemEntity.class, bounds(player));
        try {
            player.setGameMode(GameType.SURVIVAL);
            for (int owner : new int[]{0, 4, 40}) verifyClicks(player, owner);
            verifyPrinting(player);
            verifyLifecycle(player);
        } finally {
            player.setHealth(health);
            player.closeContainer();
            for (var drop : player.level().getEntitiesOfClass(ItemEntity.class, bounds(player)))
                if (!existingDrops.contains(drop)) drop.discard();
            for (int i = 0; i < saved.size(); i++) inventory.setItem(i, saved.get(i));
            inventory.setSelectedSlot(selected);
            player.setGameMode(mode);
        }
    }

    private static MahjongBoxMenu open(ServerPlayer player, int owner) {
        player.closeContainer();
        player.getInventory().setSelectedSlot(owner == 40 ? 0 : owner);
        var hand = owner == 40 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        var stack = player.getItemInHand(hand);
        stack.getItem().use(player.level(), player, hand);
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
        check(menu.slots.size() == MahjongSupplies.BOX_SLOTS + 36 && menu.stillValid(player), "Invalid compartment layout");

        // Use clicked, not only quickMoveStack: vanilla repeats a shift transfer until exhausted.
        menu.clicked(HOTBAR + 1, 0, ContainerInput.QUICK_MOVE, player);
        check(inventory.getItem(1).isEmpty() && menu.getSlot(0).getItem().getCount() == 64, "Shift insertion failed");
        conserved(player, menu, expected);
        menu.clicked(HOTBAR + 2, 0, ContainerInput.QUICK_MOVE, player);
        menu.clicked(HOTBAR + 3, 0, ContainerInput.QUICK_MOVE, player);
        check(inventory.getItem(2).is(Items.DIAMOND) && inventory.getItem(3).is(MahjongContent.BOX_ITEM), "Forbidden insertion");

        if (owner != 40) {
            int carrier = HOTBAR + owner;
            for (var type : new ContainerInput[]{ContainerInput.PICKUP, ContainerInput.QUICK_MOVE, ContainerInput.THROW, ContainerInput.CLONE})
                for (int button : new int[]{0, 1}) menu.clicked(carrier, button, type, player);
            for (int button = 0; button < 9; button++) menu.clicked(carrier, button, ContainerInput.SWAP, player);
            menu.clicked(carrier, 40, ContainerInput.SWAP, player);
            player.setGameMode(GameType.CREATIVE);
            menu.clicked(carrier, 2, ContainerInput.CLONE, player);
            player.setGameMode(GameType.SURVIVAL);
        }
        for (int slot : new int[]{0, 1, MahjongSupplies.BOX_SLOTS, HOTBAR + 1}) menu.clicked(slot, owner, ContainerInput.SWAP, player);
        check(menu.getCarried().isEmpty() && inventory.getItem(owner) == box, "Owner-slot lock was bypassed");
        conserved(player, menu, expected);

        // Ordinary right/left clicks, then both drag modes, with the owner included in the drag.
        menu.clicked(0, 1, ContainerInput.PICKUP, player);
        check(menu.getCarried().getCount() == 32 && menu.getSlot(0).getItem().getCount() == 32, "Right click did not split");
        menu.clicked(1, 0, ContainerInput.PICKUP, player);
        menu.clicked(1, 0, ContainerInput.PICKUP, player);
        drag(menu, player, 0, owner == 40 ? new int[]{1, 2} : new int[]{1, HOTBAR + owner, 2});
        check(menu.getSlot(1).getItem().getCount() == 16 && menu.getSlot(2).getItem().getCount() == 16, "Even drag failed");
        conserved(player, menu, expected);
        menu.clicked(1, 0, ContainerInput.PICKUP, player);
        drag(menu, player, 1, new int[]{1, 3, 4});
        check(menu.getCarried().getCount() == 13 && menu.getSlot(3).getItem().getCount() == 1, "Single-item drag failed");
        menu.clicked(5, 0, ContainerInput.PICKUP, player);
        menu.clicked(5, 0, ContainerInput.PICKUP, player);
        menu.clicked(5, 0, ContainerInput.PICKUP_ALL, player);
        check(menu.getCarried().getCount() == 64, "Double click did not gather a full stack");
        conserved(player, menu, expected);
        menu.clicked(6, 0, ContainerInput.PICKUP, player);

        // Another box on the cursor or hotbar cannot nest in the open carrier.
        menu.clicked(HOTBAR + 3, 0, ContainerInput.PICKUP, player);
        menu.clicked(7, 0, ContainerInput.PICKUP, player);
        check(menu.getCarried().is(MahjongContent.BOX_ITEM) && !menu.getSlot(7).hasItem(), "Nested box accepted");
        menu.clicked(HOTBAR + 3, 0, ContainerInput.PICKUP, player);
        menu.clicked(7, 3, ContainerInput.SWAP, player);
        check(!menu.getSlot(7).hasItem(), "Hotbar swap nested a box");

        // Unlocked hotbar and offhand swaps remain useful for actual supplies.
        menu.clicked(6, 1, ContainerInput.SWAP, player);
        check(inventory.getItem(1).getCount() == 64, "Valid hotbar extraction blocked");
        menu.clicked(6, 1, ContainerInput.SWAP, player);
        if (owner != 40) {
            menu.clicked(6, 40, ContainerInput.SWAP, player);
            check(inventory.getItem(40).getCount() == 64, "Valid offhand extraction blocked");
            menu.clicked(6, 40, ContainerInput.SWAP, player);
        }
        menu.clicked(MahjongSupplies.BOX_SLOTS + 1, 0, ContainerInput.QUICK_MOVE, player);
        check(inventory.getItem(10).isEmpty(), "Point-stick shift insertion failed");
        conserved(player, menu, expected);

        for (int slot = 0; slot < MahjongSupplies.BOX_SLOTS; slot++) menu.clicked(slot, 0, ContainerInput.QUICK_MOVE, player);
        check(MahjongSupplies.contents(box).stream().allMatch(ItemStack::isEmpty), "Extraction did not persist immediately");
        check(inventory.getItem(owner) == box, "Extraction replaced the owner");
        conserved(player, menu, expected);
        player.closeContainer();
        check(!menu.stillValid(player), "Closed menu remained valid");
        conserved(player, menu, expected);
    }

    private static void verifyPrinting(ServerPlayer player) {
        for (int total : new int[]{136, 144}) for (boolean creative : new boolean[]{false, true}) {
            player.closeContainer();
            var inventory = player.getInventory();
            inventory.clearContent();
            var box = top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExamples.blanks(
                top.skyeyefast.mchjong.item.TileMaterial.GLASS, DyeColor.PURPLE, total == 144);
            inventory.setItem(0, box);
            var dye = new ItemStack(creative ? MahjongContent.CREATIVE_MAHJONG_DYE : MahjongContent.MAHJONG_DYE, creative ? 1 : 2);
            inventory.setItem(1, dye);
            var menu = open(player, 0);
            check(!menu.print(player, top.skyeyefast.mchjong.item.TileFacePreset.KANSAI), "Printing without dye was accepted");
            menu.clicked(HOTBAR + 1, 0, ContainerInput.QUICK_MOVE, player);
            check(inventory.getItem(1).isEmpty() && menu.getSlot(MahjongSupplies.DYE_SLOT).hasItem(), "Dye shift transfer missed its compartment");
            check(!menu.getSlot(0).mayPlace(dye) && !menu.getSlot(MahjongSupplies.TILE_SLOTS).mayPlace(dye)
                && !menu.getSlot(MahjongSupplies.DYE_SLOT).mayPlace(new ItemStack(MahjongContent.POINT_STICK)), "Compartment accepts the wrong supply");
            var before = box.copy();
            check(!menu.clickMenuButton(player, -1) && !menu.clickMenuButton(player, 2), "Invalid print action accepted");
            check(ItemStack.matches(before, box), "Rejected printing changed the carrier");
            menu.getSlot(2).getItem().shrink(1);
            check(!menu.print(player, top.skyeyefast.mchjong.item.TileFacePreset.KANSAI), "Incomplete tile count was printed");
            menu.getSlot(2).getItem().grow(1);
            check(menu.print(player, top.skyeyefast.mchjong.item.TileFacePreset.KANSAI), "Valid printing rejected");
            var items = MahjongSupplies.contents(box);
            check(ItemStack.matches(MahjongSupplies.contents(before).get(MahjongSupplies.TILE_SLOTS), items.get(MahjongSupplies.TILE_SLOTS)),
                "Printing changed point sticks");
            check(MahjongSupplies.tileCount(items) == total && MahjongSupplies.deck(box) != null, "Printing changed the set size or composition");
            check(items.stream().filter(stack -> stack.is(MahjongContent.TILE_ITEM) && MahjongSupplies.tile(stack).flower()).count() == total - 136,
                "Printing produced the wrong flowers");
            check(items.get(MahjongSupplies.DYE_SLOT).getCount() == 1, "Printing consumed the wrong dye quantity");
            check(items.subList(0, MahjongSupplies.TILE_SLOTS).stream().filter(stack -> !stack.isEmpty())
                .allMatch(stack -> MahjongSupplies.color(stack) == DyeColor.PURPLE && !MahjongSupplies.tile(stack).blank()), "Printing altered backs or left blanks");
            var printed = box.copy();
            check(!menu.print(player, top.skyeyefast.mchjong.item.TileFacePreset.KANSAI) && ItemStack.matches(printed, box), "No-op printing consumed dye");
            check(menu.print(player, top.skyeyefast.mchjong.item.TileFacePreset.KANTO), "Preset change rejected");
            var changed = MahjongSupplies.contents(box);
            for (int slot = 0; slot < MahjongSupplies.TILE_SLOTS; slot++) {
                var expected = items.get(slot).copy();
                if (!expected.isEmpty()) expected.set(top.skyeyefast.mchjong.item.MahjongComponents.FACE_PRESET,
                    top.skyeyefast.mchjong.item.TileFacePreset.KANTO);
                check(ItemStack.matches(expected, changed.get(slot)), "Preset change altered tile identity, material or back");
            }
            check(changed.get(MahjongSupplies.DYE_SLOT).getCount() == (creative ? 1 : 0), "Preset change consumed the wrong dye quantity");
            check(MahjongSupplies.deck(box).preset().equals(top.skyeyefast.mchjong.item.TileFacePreset.KANTO), "Table ignored the printed preset");
            printed = box.copy();
            player.closeContainer();
            check(!menu.print(player, top.skyeyefast.mchjong.item.TileFacePreset.KANSAI) && ItemStack.matches(printed, box), "Closed menu printed stale contents");
        }

        player.closeContainer();
        var inventory = player.getInventory();
        inventory.clearContent();
        var box = MahjongSupplies.completeBox(top.skyeyefast.mchjong.item.TileMaterial.BONE);
        inventory.setItem(0, box);
        inventory.setItem(1, new ItemStack(Items.CYAN_DYE, 2));
        var menu = open(player, 0);
        menu.clicked(HOTBAR + 1, 0, ContainerInput.QUICK_MOVE, player);
        check(menu.getSlot(MahjongSupplies.DYE_SLOT).getItem().is(Items.CYAN_DYE), "Vanilla dye missed the dye compartment");
        check(menu.canDyeBack() && menu.clickMenuButton(player, MahjongBoxMenu.DYE_BACK_BUTTON), "Back dye action was rejected");
        var dyed = MahjongSupplies.contents(box);
        check(dyed.get(MahjongSupplies.DYE_SLOT).getCount() == 1, "Back dye consumed the wrong quantity");
        check(dyed.subList(0, MahjongSupplies.TILE_SLOTS).stream().filter(stack -> !stack.isEmpty())
            .allMatch(stack -> MahjongSupplies.back(stack) == DyeColor.CYAN), "Back dye did not recolor the whole set");
        check(!menu.canDyeBack() && !menu.clickMenuButton(player, MahjongBoxMenu.DYE_BACK_BUTTON), "No-op back dye consumed reagent");
        menu.clicked(MahjongSupplies.DYE_SLOT, 0, ContainerInput.QUICK_MOVE, player);
        check(menu.getSlot(MahjongSupplies.DYE_SLOT).getItem().isEmpty(), "Previous dye stayed in the compartment");
        player.closeContainer();

        inventory.setItem(1, new ItemStack(MahjongContent.UNDO_DYE, 2));
        menu = open(player, 0);
        menu.clicked(HOTBAR + 1, 0, ContainerInput.QUICK_MOVE, player);
        check(menu.getSlot(MahjongSupplies.DYE_SLOT).getItem().is(MahjongContent.UNDO_DYE), "Undo dye missed the dye compartment");
        check(menu.canDyeBack() && menu.clickMenuButton(player, MahjongBoxMenu.DYE_BACK_BUTTON), "Undo dye action was rejected");
        var undyed = MahjongSupplies.contents(box);
        check(undyed.get(MahjongSupplies.DYE_SLOT).getCount() == 1, "Undo dye consumed the wrong quantity");
        check(undyed.subList(0, MahjongSupplies.TILE_SLOTS).stream().filter(stack -> !stack.isEmpty())
            .allMatch(stack -> MahjongSupplies.back(stack) == null), "Undo dye did not clear the tile backs");
        check(!menu.canDyeBack() && !menu.clickMenuButton(player, MahjongBoxMenu.DYE_BACK_BUTTON), "No-op undo dye consumed reagent");
        player.closeContainer();
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
        menu.clicked(0, 0, ContainerInput.PICKUP, player);
        check(menu.quickMoveStack(player, 0).isEmpty(), "Detached menu extracted cached supplies");
        inventory.setItem(40, ItemStack.EMPTY);
        inventory.setItem(0, box);
        check(!menu.stillValid(player), "Returning the carrier revived an obsolete menu");
        conserved(player, menu, expected);

        menu = open(player, 0);
        menu.clicked(0, 1, ContainerInput.PICKUP, player);
        player.closeContainer();
        check(menu.getCarried().isEmpty(), "Closing lost the cursor return transaction");
        var before = box.copy();
        menu.getSlot(0).setByPlayer(ItemStack.EMPTY);
        check(ItemStack.matches(before, box), "Closed menu wrote back stale contents");
        conserved(player, menu, expected);

        menu = open(player, 0);
        menu.clicked(0, 1, ContainerInput.PICKUP, player);
        float health = player.getHealth();
        player.setHealth(0);
        check(!menu.stillValid(player), "Dead player retained a live menu");
        menu.clicked(0, 0, ContainerInput.PICKUP, player);
        player.closeContainer();
        player.setHealth(health);
        check(!menu.stillValid(player), "Restoring health revived an obsolete menu");
        conserved(player, menu, expected);
    }

    private static void drag(AbstractContainerMenu menu, ServerPlayer player, int mode, int[] slots) {
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, mode), ContainerInput.QUICK_CRAFT, player);
        for (int slot : slots) menu.clicked(slot, AbstractContainerMenu.getQuickcraftMask(1, mode), ContainerInput.QUICK_CRAFT, player);
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, mode), ContainerInput.QUICK_CRAFT, player);
    }

    private static AABB bounds(ServerPlayer player) { return new AABB(player.blockPosition()).inflate(8); }

    private static Map<CompoundTag, Integer> snapshot(ServerPlayer player, AbstractContainerMenu menu) {
        Map<CompoundTag, Integer> result = new HashMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) count(player, result, player.getInventory().getItem(i));
        count(player, result, menu.getCarried());
        for (var drop : player.level().getEntitiesOfClass(ItemEntity.class, bounds(player))) count(player, result, drop.getItem());
        return result;
    }

    private static void count(ServerPlayer player, Map<CompoundTag, Integer> result, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack identity = stack.copyWithCount(1);
        if (stack.is(MahjongContent.BOX_ITEM)) {
            identity.remove(DataComponents.CONTAINER);
            stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream()
                .forEach(item -> count(player, result, item));
        }
        result.merge((CompoundTag) ItemStack.CODEC.encodeStart(
            net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, player.registryAccess()),
            identity).getOrThrow(), stack.getCount(), Integer::sum);
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
