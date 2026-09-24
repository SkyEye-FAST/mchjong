package top.skyeyefast.mchjong.compat.maid;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

/** Loaded through each maid mod's optional extension discovery, on both logical sides. */
public class MaidExtension implements ILittleMaid {
    @Override public void addMaidTask(TaskManager manager) { manager.add(new MaidMahjongTask()); }

    @Override public void registerTaskData(TaskDataRegister register) {
        MaidTables.bindingKey = register.register(MaidMahjongTask.ID, CompoundTag.CODEC);
    }

    @Override public void addExtraMaidBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new IExtraMaidBrain() {
            @Override public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> getCoreBehaviors() {
                return List.of(Pair.of(4, new MaidTables()));
            }
        });
    }
}
