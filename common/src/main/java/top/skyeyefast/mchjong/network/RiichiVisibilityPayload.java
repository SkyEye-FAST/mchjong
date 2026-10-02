package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.PlayerHandVisibility;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A host proposal bound to the current room and preparation decision. */
public record RiichiVisibilityPayload(BlockPos pos, UUID tableId, long decision, PlayerHandVisibility visibility)
        implements MahjongPayload {
    public static final ResourceLocation TYPE = MahjongContent.id("riichi_visibility");
    public static RiichiVisibilityPayload decode(FriendlyByteBuf buffer) {
        return new RiichiVisibilityPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readVarLong(),
            buffer.readEnum(PlayerHandVisibility.class));
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUUID(this.tableId()); buffer.writeVarLong(this.decision());
        buffer.writeEnum(this.visibility());
    }
    @Override public ResourceLocation id() { return TYPE; }
}
