package top.skyeyefast.mchjong.compat.create;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.simibubi.create.content.kinetics.deployer.DeployerRecipeSearchEvent;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.recipe.v1.ingredient.DefaultCustomIngredients;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Fabric registration, exact-NBT ingredients and transactional inventory reads. */
public final class CreatePlatform {
    private CreatePlatform() {}

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, MahjongContent.id("mahjong_printing_plate"), CreateCompat.PRINTING_PLATE);
        Registry.register(BuiltInRegistries.ITEM, MahjongContent.id("incomplete_mahjong_box"), CreateCompat.INCOMPLETE_BOX);
        DeployerRecipeSearchEvent.EVENT.register(event -> event.addRecipe(() -> Optional.ofNullable(
            CreateProcessing.deploying(event.getInventory().getItem(0), event.getInventory().getItem(1))), 100));
        for (var tab : List.of(MahjongContent.TAB_KEY, CreativeModeTabs.FUNCTIONAL_BLOCKS))
            ItemGroupEvents.modifyEntriesEvent(tab).register(entries -> {
                entries.accept(new ItemStack(CreateCompat.PRINTING_PLATE));
            });
    }

    public static Ingredient exact(ItemStack stack) { return DefaultCustomIngredients.nbt(stack, true); }

    public static List<ItemStack> inventory(BasinBlockEntity basin) {
        var inventory = new ArrayList<ItemStack>();
        var storage = basin.getItemStorage(null);
        if (storage == null) return inventory;
        for (var view : storage.nonEmptyViews()) {
            try (var transaction = Transaction.openOuter()) {
                var variant = view.getResource();
                int amount = Math.toIntExact(view.getAmount());
                if (view.extract(variant, 1, transaction) == 1) CreateProcessing.addSnapshot(inventory, variant.toStack(amount));
            }
        }
        return inventory;
    }

    public static void ensureCapacity(ProcessingInventory inventory, int required) {
        if (required <= inventory.getSlotCount()) return;
        var saved = new ArrayList<ItemStack>();
        for (int slot = 0; slot < inventory.getSlotCount(); slot++) saved.add(inventory.getStackInSlot(slot).copy());
        inventory.setSize(required);
        for (int slot = 0; slot < saved.size(); slot++) inventory.setStackInSlot(slot, saved.get(slot));
    }
}
