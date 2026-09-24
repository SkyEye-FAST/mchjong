package top.skyeyefast.mchjong.compat.create;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.simibubi.create.content.kinetics.deployer.DeployerRecipeSearchEvent;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Forge registration, exact-NBT ingredients and capability inventory reads. */
public final class CreatePlatform {
    private CreatePlatform() {}

    public static void register(IEventBus bus) {
        DeferredRegister<Item> items = DeferredRegister.create(Registries.ITEM, MahjongContent.MOD_ID);
        items.register("mahjong_printing_plate", () -> CreateCompat.PRINTING_PLATE);
        items.register("incomplete_mahjong_box", () -> CreateCompat.INCOMPLETE_BOX);
        items.register(bus);
        MinecraftForge.EVENT_BUS.addListener((DeployerRecipeSearchEvent event) -> event.addRecipe(() -> Optional.ofNullable(
            CreateProcessing.deploying(event.getInventory().getItem(0), event.getInventory().getItem(1))), 100));
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() != MahjongContent.TAB_KEY && event.getTabKey() != CreativeModeTabs.FUNCTIONAL_BLOCKS) return;
            event.accept(CreateCompat.plate(TileFacePreset.KANSAI));
            event.accept(CreateCompat.plate(TileFacePreset.KANTO));
        });
    }

    public static Ingredient exact(ItemStack stack) { return StrictNBTIngredient.of(stack); }

    public static List<ItemStack> inventory(BasinBlockEntity basin) {
        var inventory = new ArrayList<ItemStack>();
        var handler = basin.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (handler == null) return inventory;
        for (int slot = 0; slot < handler.getSlots(); slot++)
            if (!handler.extractItem(slot, 1, true).isEmpty())
                CreateProcessing.addSnapshot(inventory, handler.getStackInSlot(slot));
        return inventory;
    }

    public static void ensureCapacity(ProcessingInventory inventory, int required) {
        if (required <= inventory.getSlots()) return;
        var saved = new ArrayList<ItemStack>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) saved.add(inventory.getStackInSlot(slot).copy());
        inventory.setSize(required);
        for (int slot = 0; slot < saved.size(); slot++) inventory.setStackInSlot(slot, saved.get(slot));
    }
}
