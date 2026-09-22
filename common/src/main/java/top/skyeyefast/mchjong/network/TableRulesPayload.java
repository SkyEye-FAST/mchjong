package top.skyeyefast.mchjong.network;

import java.util.EnumMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.RuleConfig;
import top.skyeyefast.mchjong.engine.RuleOption;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A bounded, complete proposal, authorized by the table identity and current lobby decision. */
public record TableRulesPayload(BlockPos pos, UUID tableId, long decision, RuleConfig rules) implements MahjongPayload {
    public static final ResourceLocation TYPE = MahjongContent.id("table_rules");
    public static TableRulesPayload decode(FriendlyByteBuf buffer) {
        var pos = buffer.readBlockPos();
        var id = buffer.readUUID();
        long decision = buffer.readVarLong();
        var preset = buffer.readEnum(RuleSet.class);
        var values = new EnumMap<RuleOption, Integer>(RuleOption.class);
        for (var option : RuleOption.values()) values.put(option, buffer.readVarInt());
        return new TableRulesPayload(pos, id, decision, new RuleConfig(preset, values));
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos()); buffer.writeUUID(tableId()); buffer.writeVarLong(decision());
        buffer.writeEnum(rules().preset());
        for (var option : RuleOption.values()) buffer.writeVarInt(rules().get(option));
    }
    @Override public ResourceLocation id() { return TYPE; }
}
