package top.skyeyefast.mchjong.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.engine.TimeControl;
import top.skyeyefast.mchjong.world.MahjongContent;

public record TaiwanClockPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, TimeControl control)
    implements CustomPacketPayload {
    public TaiwanClockPayload {
        pos = Objects.requireNonNull(pos).immutable();
        Objects.requireNonNull(tableId);
        Objects.requireNonNull(incarnation);
        Objects.requireNonNull(control);
        if (decision < 1) throw new IllegalArgumentException("Invalid Taiwan room decision");
    }
    public static final Type<TaiwanClockPayload> TYPE = new Type<>(MahjongContent.id("taiwan_clock"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TaiwanClockPayload> CODEC = new StreamCodec<>() {
        @Override public TaiwanClockPayload decode(RegistryFriendlyByteBuf buffer) {
            return new TaiwanClockPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong(), new TimeControl(buffer.readVarInt(), buffer.readVarInt()));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TaiwanClockPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeUUID(value.incarnation());
            buffer.writeVarLong(value.decision()); buffer.writeVarInt(value.control().reserveSeconds()); buffer.writeVarInt(value.control().moveSeconds());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
