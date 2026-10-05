package top.skyeyefast.mchjong.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.TaiwanGameState;
import top.skyeyefast.mchjong.engine.TaiwanCodec;
import top.skyeyefast.mchjong.world.MahjongContent;

public record TaiwanRulesPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, TaiwanGameState.Rules rules)
    implements MahjongPayload {
    public TaiwanRulesPayload {
        pos = Objects.requireNonNull(pos).immutable();
        Objects.requireNonNull(tableId);
        Objects.requireNonNull(incarnation);
        Objects.requireNonNull(rules);
        if (decision < 1) throw new IllegalArgumentException("Invalid Taiwan room decision");
    }
    public static final ResourceLocation TYPE = MahjongContent.id("taiwan_rules");
    public static TaiwanRulesPayload decode(FriendlyByteBuf buffer) {
        return new TaiwanRulesPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong(), readRules(buffer));
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUUID(this.tableId()); buffer.writeUUID(this.incarnation());
        buffer.writeVarLong(this.decision()); writeRules(buffer, this.rules());
    }
    static TaiwanGameState.Rules readRules(FriendlyByteBuf buffer) {
        return TaiwanCodec.decodeRules(buffer.readUtf(65536));
    }
    static void writeRules(FriendlyByteBuf buffer, TaiwanGameState.Rules rules) {
        buffer.writeUtf(TaiwanCodec.encodeRules(rules), 65536);
    }
    @Override public ResourceLocation id() { return TYPE; }
}
