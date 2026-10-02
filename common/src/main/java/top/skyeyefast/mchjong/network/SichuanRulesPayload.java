package top.skyeyefast.mchjong.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.SichuanRules;
import top.skyeyefast.mchjong.world.MahjongContent;

public record SichuanRulesPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, SichuanRules rules)
    implements MahjongPayload {
    public SichuanRulesPayload {
        pos = Objects.requireNonNull(pos).immutable();
        Objects.requireNonNull(tableId);
        Objects.requireNonNull(incarnation);
        Objects.requireNonNull(rules);
        if (decision < 1) throw new IllegalArgumentException("Invalid Sichuan room decision");
    }
    public static final ResourceLocation TYPE = MahjongContent.id("sichuan_rules");
    public static SichuanRulesPayload decode(FriendlyByteBuf buffer) {
        return new SichuanRulesPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong(), readRules(buffer));
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUUID(this.tableId()); buffer.writeUUID(this.incarnation());
        buffer.writeVarLong(this.decision()); writeRules(buffer, this.rules());
    }
    static SichuanRules readRules(FriendlyByteBuf buffer) {
        return new SichuanRules(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(),
            buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
    }
    static void writeRules(FriendlyByteBuf buffer, SichuanRules rules) {
        buffer.writeVarInt(rules.fanCap()); buffer.writeVarInt(rules.selfDrawBonus());
        buffer.writeVarInt(rules.concealedKongPayment()); buffer.writeVarInt(rules.discardKongPayment());
        buffer.writeVarInt(rules.addedKongPayment()); buffer.writeVarInt(rules.activeFlowerPigPenalty());
        buffer.writeBoolean(rules.transferKongOnShoot()); buffer.writeBoolean(rules.refundKongWhenNotReady());
        buffer.writeVarInt(rules.matchHands());
        buffer.writeBoolean(rules.separateKongFan()); buffer.writeBoolean(rules.selectFirstDiscard());
        buffer.writeBoolean(rules.addedKongAfterKongIsShoot());
        buffer.writeBoolean(rules.eastWestLongWall());
    }
    @Override public ResourceLocation id() { return TYPE; }
}
