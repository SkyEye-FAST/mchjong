package top.skyeyefast.mchjong.world;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.engine.McrSession;
import top.skyeyefast.mchjong.engine.RedFives;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.engine.RiichiRuleOption;
import top.skyeyefast.mchjong.engine.RiichiSession;
import top.skyeyefast.mchjong.engine.TableSession;
import top.skyeyefast.mchjong.engine.TableSessionCodec;
import top.skyeyefast.mchjong.engine.WorldPolicy;

/** Minecraft equipment and bot adapters around a single authoritative table session. */
final class TableHost {
    private TableSession session;
    private final BotServiceClient bots = new BotServiceClient();

    TableHost(UUID tableId, long seed) {
        this(new RiichiSession(tableId, RiichiPreset.MAHJONG_SOUL_4.config()
            .with(RiichiRuleOption.RED_FIVES, RedFives.NONE.ordinal()), seed));
    }

    private TableHost(TableSession session) { this.session = Objects.requireNonNull(session); }

    static TableHost restore(String encoded) { return new TableHost(TableSessionCodec.restore(encoded)); }
    String save() { return TableSessionCodec.save(session); }
    TableSession session() { return session; }
    RiichiSession riichi() { return session instanceof RiichiSession riichi ? riichi : null; }
    RiichiGame riichiGame() { return riichi() == null ? null : riichi().game(); }
    McrSession mcr() { return session instanceof McrSession mcr ? mcr : null; }
    BotServiceState botState() { return riichi() == null ? null : bots.state(riichi()); }

    /** Returns whether equipment selection changed its public appearance. */
    boolean prepare(TableEquipment equipment, boolean automatic, WorldPolicy policy) {
        session.configureWorld(policy);
        if (session instanceof RiichiSession riichi) {
            riichi.configureExternalBots(BotServiceClient.availableBots());
            if (riichi.lobby() && !equipment.canSupplyReds(riichi.rules().sanma(), riichi.rules().redFives()))
                for (var reds : new RedFives[]{RedFives.THREE, RedFives.FOUR, RedFives.NONE})
                    if (riichi.rules().preset().allows(reds) && equipment.canSupplyReds(riichi.rules().sanma(), reds)) {
                        riichi.configureStockRedFives(reds);
                        break;
                    }
            boolean changed = equipment.selectRules(riichi.rules());
            if (riichi.lobby()) riichi.configureEquipment(!automatic,
                !equipment.hasCloth() || equipment.deck() == null || !automatic && !equipment.manualSuppliesReady()
                    ? List.of() : equipment.deck().tiles());
            return changed;
        }
        var mcr = (McrSession) session;
        var stock = equipment.mcrStock();
        if (mcr.lobby()) mcr.configureEquipment(false,
            automatic && equipment.hasCloth() && stock != null ? stock.deck().tiles() : List.of());
        return false;
    }

    boolean available(TableEquipment equipment) {
        if (session.lobby()) return true;
        return equipment.hasCloth() && (session instanceof McrSession ? equipment.mcrStock() != null : equipment.deck() != null);
    }

    void tick() {
        session.tick();
        if (session instanceof RiichiSession riichi) bots.tick(riichi);
    }

    boolean selectVariant(UUID actor, long decision, MahjongVariant variant) {
        // Completed side effects must be acknowledged before releasing their owning runtime.
        if (session instanceof RiichiSession riichi && (!riichi.pendingReplays().isEmpty() || !riichi.pendingExperience().isEmpty()))
            return false;
        if (session instanceof McrSession mcr && !mcr.pendingReplays().isEmpty()) return false;
        TableSession replacement = session.selectVariant(actor, decision, variant);
        if (replacement == null) return false;
        session = replacement;
        return true;
    }
}
