package top.skyeyefast.mchjong.engine;

import java.util.Locale;

public enum SichuanRuleOption {
    FAN_CAP(Group.BASIC, 0, 8),
    SELF_DRAW_BONUS(Group.BASIC, 0, 8),
    CONCEALED_KONG_PAYMENT(Group.KONG, 0, 8),
    DISCARD_KONG_PAYMENT(Group.KONG, 0, 8),
    ADDED_KONG_PAYMENT(Group.KONG, 0, 8),
    ACTIVE_FLOWER_PIG_PENALTY(Group.SETTLEMENT, 1, 256),
    TRANSFER_KONG_ON_SHOOT(Group.KONG, 0, 1),
    REFUND_KONG_WHEN_NOT_READY(Group.SETTLEMENT, 0, 1),
    MATCH_HANDS(Group.BASIC, 1, 64),
    SEPARATE_KONG_FAN(Group.KONG, 0, 1),
    SELECT_FIRST_DISCARD(Group.BASIC, 0, 1),
    ADDED_KONG_AFTER_KONG_IS_SHOOT(Group.KONG, 0, 1),
    EAST_WEST_LONG_WALL(Group.BASIC, 0, 1);

    public enum Group {
        BASIC, KONG, SETTLEMENT;
        public String translationKey() { return "sichuan.mchjong.rules.group." + name().toLowerCase(Locale.ROOT); }
    }

    private final Group group;
    private final int min, max;

    SichuanRuleOption(Group group, int min, int max) { this.group = group; this.min = min; this.max = max; }
    public Group group() { return group; }
    public int min() { return min; }
    public int max() { return max; }
    public boolean toggle() { return min == 0 && max == 1; }
    public boolean valid(int value) { return value >= min && value <= max; }
    public String translationKey() { return "sichuan.mchjong.rules." + name().toLowerCase(Locale.ROOT); }
    public String descriptionKey() { return translationKey() + ".description"; }
    public int get(SichuanRules rules) {
        return switch (this) {
            case FAN_CAP -> rules.fanCap();
            case SELF_DRAW_BONUS -> rules.selfDrawBonus();
            case CONCEALED_KONG_PAYMENT -> rules.concealedKongPayment();
            case DISCARD_KONG_PAYMENT -> rules.discardKongPayment();
            case ADDED_KONG_PAYMENT -> rules.addedKongPayment();
            case ACTIVE_FLOWER_PIG_PENALTY -> rules.activeFlowerPigPenalty();
            case TRANSFER_KONG_ON_SHOOT -> rules.transferKongOnShoot() ? 1 : 0;
            case REFUND_KONG_WHEN_NOT_READY -> rules.refundKongWhenNotReady() ? 1 : 0;
            case MATCH_HANDS -> rules.matchHands();
            case SEPARATE_KONG_FAN -> rules.separateKongFan() ? 1 : 0;
            case SELECT_FIRST_DISCARD -> rules.selectFirstDiscard() ? 1 : 0;
            case ADDED_KONG_AFTER_KONG_IS_SHOOT -> rules.addedKongAfterKongIsShoot() ? 1 : 0;
            case EAST_WEST_LONG_WALL -> rules.eastWestLongWall() ? 1 : 0;
        };
    }
    public SichuanRules with(SichuanRules rules, int value) {
        if (!valid(value)) throw new IllegalArgumentException("Invalid Sichuan rule " + this);
        return new SichuanRules(this == FAN_CAP ? value : rules.fanCap(),
            this == SELF_DRAW_BONUS ? value : rules.selfDrawBonus(),
            this == CONCEALED_KONG_PAYMENT ? value : rules.concealedKongPayment(),
            this == DISCARD_KONG_PAYMENT ? value : rules.discardKongPayment(),
            this == ADDED_KONG_PAYMENT ? value : rules.addedKongPayment(),
            this == ACTIVE_FLOWER_PIG_PENALTY ? value : rules.activeFlowerPigPenalty(),
            this == TRANSFER_KONG_ON_SHOOT ? value != 0 : rules.transferKongOnShoot(),
            this == REFUND_KONG_WHEN_NOT_READY ? value != 0 : rules.refundKongWhenNotReady(),
            this == MATCH_HANDS ? value : rules.matchHands(),
            this == SEPARATE_KONG_FAN ? value != 0 : rules.separateKongFan(),
            this == SELECT_FIRST_DISCARD ? value != 0 : rules.selectFirstDiscard(),
            this == ADDED_KONG_AFTER_KONG_IS_SHOOT ? value != 0 : rules.addedKongAfterKongIsShoot(),
            this == EAST_WEST_LONG_WALL ? value != 0 : rules.eastWestLongWall());
    }
}
