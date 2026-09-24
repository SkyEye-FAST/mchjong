package top.skyeyefast.mchjong.smoke;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.skyeyefast.mchjong.compat.create.CreateProcessing;

final class CreateSmokeInventory {
    static boolean insert(BasinBlockEntity basin, ItemStack stack) {
        try (var transaction = Transaction.openOuter()) {
            if (basin.inputInventory.insert(ItemVariant.of(stack), stack.getCount(), transaction) != stack.getCount()) return false;
            transaction.commit();
            return true;
        }
    }
    static List<ItemStack> outputs(BasinBlockEntity basin) {
        var result = new ArrayList<ItemStack>();
        var output = basin.getOutputInventory();
        for (int slot = 0; slot < output.getSlotCount(); slot++) CreateProcessing.addSnapshot(result, output.getStackInSlot(slot));
        return result;
    }
    static void blockOutputs(BasinBlockEntity basin) {
        for (int slot = 0; slot < basin.getOutputInventory().getSlotCount() - 1; slot++)
            basin.getOutputInventory().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
    }
    static void clearOutputs(BasinBlockEntity basin) { basin.getOutputInventory().clearContent(); }
    static void clear(BasinBlockEntity basin) { basin.inputInventory.clearContent(); clearOutputs(basin); }
    static int sawCount(com.simibubi.create.content.processing.recipe.ProcessingInventory inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlotCount(); slot++) count += inventory.getStackInSlot(slot).getCount();
        return count;
    }
}
