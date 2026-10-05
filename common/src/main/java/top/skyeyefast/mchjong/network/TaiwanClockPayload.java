package top.skyeyefast.mchjong.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.TimeControl;
import top.skyeyefast.mchjong.world.MahjongContent;

public record TaiwanClockPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, TimeControl control)
    implements MahjongPayload {
    public TaiwanClockPayload {
        pos = Objects.requireNonNull(pos).immutable();
        Objects.requireNonNull(tableId);
        Objects.requireNonNull(incarnation);
        Objects.requireNonNull(control);
        if (decision < 1) throw new IllegalArgumentException("Invalid Taiwan room decision");
    }
    public static final ResourceLocation TYPE = MahjongContent.id("taiwan_clock");
    public static TaiwanClockPayload decode(FriendlyByteBuf buffer) {
        return new TaiwanClockPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong(), new TimeControl(buffer.readVarInt(), buffer.readVarInt()));
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUUID(this.tableId()); buffer.writeUUID(this.incarnation());
        buffer.writeVarLong(this.decision()); buffer.writeVarInt(this.control().reserveSeconds()); buffer.writeVarInt(this.control().moveSeconds());
    }
    @Override public ResourceLocation id() { return TYPE; }
}
