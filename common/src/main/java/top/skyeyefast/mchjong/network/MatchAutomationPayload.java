package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.MatchAutomation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Shared private preferences, separately authorized from issued game actions. */
public record MatchAutomationPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision,
                                     MatchAutomation.Option option, boolean enabled) implements MahjongPayload {
    public static final ResourceLocation TYPE = MahjongContent.id("match_automation");
    public static MatchAutomationPayload decode(FriendlyByteBuf buffer) {
        return new MatchAutomationPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(),
            buffer.readVarLong(), buffer.readEnum(MatchAutomation.Option.class), buffer.readBoolean());
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUUID(this.tableId()); buffer.writeUUID(this.incarnation());
        buffer.writeVarLong(this.decision()); buffer.writeEnum(this.option()); buffer.writeBoolean(this.enabled());
    }
    @Override public ResourceLocation id() { return TYPE; }
}
