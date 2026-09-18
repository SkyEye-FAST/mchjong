package top.skyeyefast.mchjong.network;

import java.util.EnumMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.engine.RuleConfig;
import top.skyeyefast.mchjong.engine.RuleOption;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A bounded, complete proposal, authorized by the table identity and current lobby decision. */
public record TableRulesPayload(BlockPos pos, UUID tableId, long decision, RuleConfig rules) implements CustomPacketPayload {
    public static final Type<TableRulesPayload> TYPE = new Type<>(MahjongContent.id("table_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TableRulesPayload> CODEC = new StreamCodec<>() {
        @Override public TableRulesPayload decode(RegistryFriendlyByteBuf buffer) {
            var pos = buffer.readBlockPos();
            var id = buffer.readUUID();
            long decision = buffer.readVarLong();
            var preset = buffer.readEnum(RuleSet.class);
            var values = new EnumMap<RuleOption, Integer>(RuleOption.class);
            for (var option : RuleOption.values()) values.put(option, buffer.readVarInt());
            return new TableRulesPayload(pos, id, decision, new RuleConfig(preset, values));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TableRulesPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeVarLong(value.decision());
            buffer.writeEnum(value.rules().preset());
            for (var option : RuleOption.values()) buffer.writeVarInt(value.rules().get(option));
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
