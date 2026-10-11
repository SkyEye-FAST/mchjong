package top.skyeyefast.mchjong.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.TaiwanGameState;
import top.skyeyefast.mchjong.engine.TaiwanCodec;
import top.skyeyefast.mchjong.engine.YakuSelection;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.world.MahjongContent;

public record TaiwanRulesPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, TaiwanGameState.Rules baseRules,
                             YakuSelectionRequest selection)
    implements MahjongPayload {
    public TaiwanRulesPayload {
        pos = Objects.requireNonNull(pos).immutable();
        Objects.requireNonNull(tableId);
        Objects.requireNonNull(incarnation);
        Objects.requireNonNull(baseRules);
        if (!baseRules.yakuSelection().isDefault()) throw new IllegalArgumentException("Client-supplied award definitions");
        if (TaiwanCodec.encodeRules(baseRules).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16384)
        throw new IllegalArgumentException("Taiwan base rules exceed proposal limit");
        Objects.requireNonNull(selection);
        if (decision < 1) throw new IllegalArgumentException("Invalid Taiwan room decision");
    }
    public TaiwanRulesPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, TaiwanGameState.Rules rules) {
        this(pos, tableId, incarnation, decision, rules.withYakuSelection(YakuSelection.defaults(MahjongVariant.TAIWAN)),
        YakuSelectionRequest.of(rules.yakuSelection()));
    }
    public TaiwanGameState.Rules rules(YakuSelection selection) { return baseRules.withYakuSelection(selection); }
    public static final ResourceLocation TYPE = top.skyeyefast.mchjong.platform.ResourceIds.of(MahjongContent.MOD_ID, "taiwan_rules");
    public static TaiwanRulesPayload decode(FriendlyByteBuf buffer) {
        return new TaiwanRulesPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong(), readRules(buffer), YakuSelectionRequest.read(buffer));
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUUID(this.tableId()); buffer.writeUUID(this.incarnation());
        buffer.writeVarLong(this.decision()); writeRules(buffer, this.baseRules());
        this.selection().write(buffer);
    }
    static TaiwanGameState.Rules readRules(FriendlyByteBuf buffer) {
        return TaiwanCodec.decodeRules(buffer.readUtf(TaiwanCodec.MAX_RULES_CHARS));
    }
    static void writeRules(FriendlyByteBuf buffer, TaiwanGameState.Rules rules) {
        buffer.writeUtf(TaiwanCodec.encodeRules(rules), TaiwanCodec.MAX_RULES_CHARS);
    }
    @Override public ResourceLocation id() { return TYPE; }
}
