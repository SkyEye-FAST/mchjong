package top.skyeyefast.mchjong.network;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.RiichiRules;
import top.skyeyefast.mchjong.engine.RiichiRuleOption;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A rules proposal references server-owned awards by ID, never by client-supplied definitions. */
public record RiichiRulesPayload(BlockPos pos, UUID tableId, long decision, RiichiPreset preset,
                            Map<RiichiRuleOption, Integer> settings, YakuSelectionRequest selection) implements MahjongPayload {
    public RiichiRulesPayload {
        settings = new RiichiRules(preset, settings, top.skyeyefast.mchjong.engine.YakuSelection.defaults(top.skyeyefast.mchjong.engine.MahjongVariant.RIICHI)).settings();
        java.util.Objects.requireNonNull(selection);
    }
    public RiichiRulesPayload(BlockPos pos, UUID tableId, long decision, RiichiRules rules) {
        this(pos, tableId, decision, rules.preset(), rules.settings(), YakuSelectionRequest.of(rules.yakuSelection()));
    }
    public RiichiRules rules(top.skyeyefast.mchjong.engine.YakuSelection selection) {
        try { return new RiichiRules(preset, settings, selection); }
        catch (IllegalArgumentException invalid) { return null; }
    }
    public static final ResourceLocation TYPE = top.skyeyefast.mchjong.platform.ResourceIds.of(MahjongContent.MOD_ID, "riichi_rules");
    public static RiichiRulesPayload decode(FriendlyByteBuf buffer) {
        var pos = buffer.readBlockPos();
        var id = buffer.readUUID();
        long decision = buffer.readVarLong();
        var preset = buffer.readEnum(RiichiPreset.class);
        var values = new EnumMap<RiichiRuleOption, Integer>(RiichiRuleOption.class);
        for (var option : RiichiRuleOption.values()) values.put(option, buffer.readVarInt());
        return new RiichiRulesPayload(pos, id, decision, preset, values, YakuSelectionRequest.read(buffer));
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUUID(this.tableId()); buffer.writeVarLong(this.decision());
        buffer.writeEnum(this.preset());
        for (var option : RiichiRuleOption.values()) buffer.writeVarInt(this.settings().get(option));
        this.selection().write(buffer);
    }
    @Override public ResourceLocation id() { return TYPE; }
}
