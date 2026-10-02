package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** An issued MCR game action index; the sender's UUID determines the seat. */
public record McrActionPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, int actionIndex)
    implements MahjongPayload {
    public McrActionPayload {
        if (actionIndex < 0) throw new IllegalArgumentException("Invalid MCR action index");
    }
    public static final ResourceLocation TYPE = MahjongContent.id("mcr_action");
    public static McrActionPayload decode(FriendlyByteBuf buffer) {
        return new McrActionPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong(), buffer.readVarInt());
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUUID(this.tableId()); buffer.writeUUID(this.incarnation());
        buffer.writeVarLong(this.decision()); buffer.writeVarInt(this.actionIndex());
    }
    @Override public ResourceLocation id() { return TYPE; }
}
