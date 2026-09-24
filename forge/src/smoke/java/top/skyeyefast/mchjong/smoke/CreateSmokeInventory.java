package top.skyeyefast.mchjong.smoke;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemHandlerHelper;
import top.skyeyefast.mchjong.compat.create.CreateProcessing;

final class CreateSmokeInventory {
    static boolean insert(BasinBlockEntity basin, ItemStack stack) {
        return ItemHandlerHelper.insertItemStacked(basin.inputInventory, stack, false).isEmpty();
    }
    static List<ItemStack> outputs(BasinBlockEntity basin) {
        var result = new ArrayList<ItemStack>();
        var output = basin.getOutputInventory();
        for (int slot = 0; slot < output.getSlots(); slot++) CreateProcessing.addSnapshot(result, output.getStackInSlot(slot));
        return result;
    }
    static void blockOutputs(BasinBlockEntity basin) {
        for (int slot = 0; slot < basin.getOutputInventory().getSlots() - 1; slot++)
            basin.getOutputInventory().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
    }
    static void clearOutputs(BasinBlockEntity basin) { basin.getOutputInventory().clearContent(); }
    static void clear(BasinBlockEntity basin) { basin.inputInventory.clearContent(); clearOutputs(basin); }
    static int sawCount(com.simibubi.create.content.processing.recipe.ProcessingInventory inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) count += inventory.getStackInSlot(slot).getCount();
        return count;
    }
}
