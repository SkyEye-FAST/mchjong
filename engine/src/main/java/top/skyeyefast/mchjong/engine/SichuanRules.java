package top.skyeyefast.mchjong.engine;

public record SichuanRules(int fanCap, int selfDrawBonus, int concealedKongPayment,
                           int discardKongPayment, int addedKongPayment, int activeFlowerPigPenalty,
                           boolean transferKongOnShoot, boolean refundKongWhenNotReady, int matchHands,
                           boolean separateKongFan, boolean selectFirstDiscard, boolean addedKongAfterKongIsShoot,
                           boolean eastWestLongWall) {
    public SichuanRules {
        if (!SichuanRuleOption.FAN_CAP.valid(fanCap) || !SichuanRuleOption.SELF_DRAW_BONUS.valid(selfDrawBonus)
            || !SichuanRuleOption.CONCEALED_KONG_PAYMENT.valid(concealedKongPayment)
            || !SichuanRuleOption.DISCARD_KONG_PAYMENT.valid(discardKongPayment)
            || !SichuanRuleOption.ADDED_KONG_PAYMENT.valid(addedKongPayment)
            || !SichuanRuleOption.ACTIVE_FLOWER_PIG_PENALTY.valid(activeFlowerPigPenalty)
            || !SichuanRuleOption.MATCH_HANDS.valid(matchHands))
            throw new IllegalArgumentException("Invalid Sichuan rules");
    }

    public int value(int fan) { return 1 << Math.min(fanCap, fan); }
}
