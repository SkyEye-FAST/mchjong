package top.skyeyefast.mchjong.network;

import java.util.EnumMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.RiichiRules;
import top.skyeyefast.mchjong.engine.RiichiRuleOption;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A bounded, complete proposal, authorized by the table identity and current lobby decision. */
public record RiichiRulesPayload(BlockPos pos, UUID tableId, long decision, RiichiRules rules) implements MahjongPayload {
    public static final ResourceLocation TYPE = MahjongContent.id("riichi_rules");
    public static RiichiRulesPayload decode(FriendlyByteBuf buffer) {
        var pos = buffer.readBlockPos();
        var id = buffer.readUUID();
        long decision = buffer.readVarLong();
        var preset = buffer.readEnum(RiichiPreset.class);
        var values = new EnumMap<RiichiRuleOption, Integer>(RiichiRuleOption.class);
        for (var option : RiichiRuleOption.values()) values.put(option, buffer.readVarInt());
        return new RiichiRulesPayload(pos, id, decision, new RiichiRules(preset, values));
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUUID(this.tableId()); buffer.writeVarLong(this.decision());
        buffer.writeEnum(this.rules().preset());
        for (var option : RiichiRuleOption.values()) buffer.writeVarInt(this.rules().get(option));
    }
    @Override public ResourceLocation id() { return TYPE; }
}
