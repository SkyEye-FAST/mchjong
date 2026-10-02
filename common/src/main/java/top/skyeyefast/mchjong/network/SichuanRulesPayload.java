package top.skyeyefast.mchjong.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.engine.SichuanRules;
import top.skyeyefast.mchjong.world.MahjongContent;

public record SichuanRulesPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, SichuanRules rules)
    implements CustomPacketPayload {
    public SichuanRulesPayload {
        pos = Objects.requireNonNull(pos).immutable();
        Objects.requireNonNull(tableId);
        Objects.requireNonNull(incarnation);
        Objects.requireNonNull(rules);
        if (decision < 1) throw new IllegalArgumentException("Invalid Sichuan room decision");
    }
    public static final Type<SichuanRulesPayload> TYPE = new Type<>(MahjongContent.id("sichuan_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SichuanRulesPayload> CODEC = new StreamCodec<>() {
        @Override public SichuanRulesPayload decode(RegistryFriendlyByteBuf buffer) {
            return new SichuanRulesPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong(), readRules(buffer));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, SichuanRulesPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeUUID(value.incarnation());
            buffer.writeVarLong(value.decision()); writeRules(buffer, value.rules());
        }
    };
    static SichuanRules readRules(RegistryFriendlyByteBuf buffer) {
        return new SichuanRules(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(),
            buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
    }
    static void writeRules(RegistryFriendlyByteBuf buffer, SichuanRules rules) {
        buffer.writeVarInt(rules.fanCap()); buffer.writeVarInt(rules.selfDrawBonus());
        buffer.writeVarInt(rules.concealedKongPayment()); buffer.writeVarInt(rules.discardKongPayment());
        buffer.writeVarInt(rules.addedKongPayment()); buffer.writeVarInt(rules.activeFlowerPigPenalty());
        buffer.writeBoolean(rules.transferKongOnShoot()); buffer.writeBoolean(rules.refundKongWhenNotReady());
        buffer.writeVarInt(rules.matchHands());
        buffer.writeBoolean(rules.separateKongFan()); buffer.writeBoolean(rules.selectFirstDiscard());
        buffer.writeBoolean(rules.addedKongAfterKongIsShoot());
        buffer.writeBoolean(rules.eastWestLongWall());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
