package top.skyeyefast.mchjong.engine;

public record SichuanRules(int fanCap, int selfDrawBonus, int concealedKongPayment,
                           int discardKongPayment, int addedKongPayment, int activeFlowerPigPenalty,
                           boolean refundKongOnShoot, boolean refundKongWhenNotReady) {
    public SichuanRules {
        if (fanCap < 0 || fanCap > 8 || selfDrawBonus < 0 || selfDrawBonus > 8
            || concealedKongPayment < 0 || concealedKongPayment > 8
            || discardKongPayment < 0 || discardKongPayment > 8
            || addedKongPayment < 0 || addedKongPayment > 8
            || activeFlowerPigPenalty < 0 || activeFlowerPigPenalty > 256)
            throw new IllegalArgumentException("Invalid Sichuan rules");
    }

    public int value(int fan) { return 1 << Math.min(fanCap, fan); }
}
