package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.HandVisibility;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A host proposal bound to the current room and preparation decision. */
public record TableVisibilityPayload(BlockPos pos, UUID tableId, long decision, HandVisibility visibility)
        implements MahjongPayload {
    public static final ResourceLocation TYPE = MahjongContent.id("table_visibility");
    public static TableVisibilityPayload decode(FriendlyByteBuf buffer) {
        return new TableVisibilityPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readVarLong(),
            buffer.readEnum(HandVisibility.class));
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos()); buffer.writeUUID(tableId()); buffer.writeVarLong(decision());
        buffer.writeEnum(visibility());
    }
    @Override public ResourceLocation id() { return TYPE; }
}
