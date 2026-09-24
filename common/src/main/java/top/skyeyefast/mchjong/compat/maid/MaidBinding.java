package top.skyeyefast.mchjong.compat.maid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

/** Private entity attachment: no optional maid API or public table data. */
public record MaidBinding(long pos, Identifier dimension, UUID table, boolean followVehicles) {
    public static final Codec<MaidBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("pos").forGetter(MaidBinding::pos),
        Identifier.CODEC.fieldOf("dimension").forGetter(MaidBinding::dimension),
        UUIDUtil.CODEC.fieldOf("table").forGetter(MaidBinding::table),
        Codec.BOOL.fieldOf("follow_vehicles").forGetter(MaidBinding::followVehicles)
    ).apply(instance, MaidBinding::new));
}
