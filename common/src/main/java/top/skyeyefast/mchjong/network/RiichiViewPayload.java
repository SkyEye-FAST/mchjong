package top.skyeyefast.mchjong.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Carries the common room, Riichi settings and an optional redacted active-match view. */
public record RiichiViewPayload(BlockPos pos, String view, boolean open, boolean controlReply, boolean leaveDecision, int redOptions,
                               top.skyeyefast.mchjong.engine.TableRoomView room,
                               top.skyeyefast.mchjong.engine.RiichiRoomSettings settings,
                               top.skyeyefast.mchjong.world.BotServiceState botService,
                               top.skyeyefast.mchjong.world.WorldSettings.Policy world, MahjongVariant variant) implements MahjongPayload {
    public RiichiViewPayload {
        if ((redOptions & ~63) != 0) throw new IllegalArgumentException("Invalid red-five capabilities");
    }
    public static final ResourceLocation TYPE = MahjongContent.id("riichi_view");
    public static RiichiViewPayload decode(FriendlyByteBuf buffer) {
        return new RiichiViewPayload(buffer.readBlockPos(), buffer.readUtf(32767), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readUnsignedByte(),
            TableNetworking.JSON.fromJson(buffer.readUtf(8192), top.skyeyefast.mchjong.engine.TableRoomView.class),
            TableNetworking.JSON.fromJson(buffer.readUtf(8192), top.skyeyefast.mchjong.engine.RiichiRoomSettings.class),
            TableNetworking.JSON.fromJson(buffer.readUtf(2048), top.skyeyefast.mchjong.world.BotServiceState.class),
            TableNetworking.JSON.fromJson(buffer.readUtf(8192), top.skyeyefast.mchjong.world.WorldSettings.Policy.class),
            buffer.readEnum(MahjongVariant.class));
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUtf(this.view(), 32767); buffer.writeBoolean(this.open());
        buffer.writeBoolean(this.controlReply());
        buffer.writeBoolean(this.leaveDecision());
        buffer.writeByte(this.redOptions());
        buffer.writeUtf(TableNetworking.JSON.toJson(this.room()), 8192);
        buffer.writeUtf(TableNetworking.JSON.toJson(this.settings()), 8192);
        buffer.writeUtf(TableNetworking.JSON.toJson(this.botService()), 2048);
        buffer.writeUtf(TableNetworking.JSON.toJson(this.world()), 8192);
        buffer.writeEnum(this.variant());
    }
    @Override public ResourceLocation id() { return TYPE; }
}
