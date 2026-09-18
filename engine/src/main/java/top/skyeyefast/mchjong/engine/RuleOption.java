package top.skyeyefast.mchjong.engine;

import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** The editable rule vocabulary, including validation and preset defaults. Point values use points, not thousands. */
public enum RuleOption {
    STARTING_POINTS(Group.POINTS, 100, 1_000_000, 100, RuleSet::startingPoints),
    RETURN_POINTS(Group.POINTS, 0, 1_000_000, 100, RuleSet::returnPoints),
    TARGET_POINTS(Group.POINTS, 0, 1_000_000, 100, RuleSet::targetPoints),
    FLOATING_PLACEMENT(Group.POINTS, r -> r == RuleSet.JPML_A),
    SHARED_RANKS(Group.POINTS, RuleSet::sharedRanks),
    ROUND_SHARED_PLACEMENT(Group.POINTS, RuleSet::mLeague),
    AWARD_FINAL_DEPOSITS(Group.POINTS, RuleSet::awardFinalDeposits),
    UMA_1(0), UMA_2(1), UMA_3(2), UMA_4(3),
    FLOAT_0_1(0, 0), FLOAT_0_2(0, 1), FLOAT_0_3(0, 2), FLOAT_0_4(0, 3),
    FLOAT_1_1(1, 0), FLOAT_1_2(1, 1), FLOAT_1_3(1, 2), FLOAT_1_4(1, 3),
    FLOAT_2_1(2, 0), FLOAT_2_2(2, 1), FLOAT_2_3(2, 2), FLOAT_2_4(2, 3),
    FLOAT_3_1(3, 0), FLOAT_3_2(3, 1), FLOAT_3_3(3, 2), FLOAT_3_4(3, 3),
    FLOAT_4_1(4, 0), FLOAT_4_2(4, 1), FLOAT_4_3(4, 2), FLOAT_4_4(4, 3),

    KUITAN(Group.SCORING, r -> true),
    IPPATSU(Group.SCORING, RuleSet::ippatsu),
    URA_DORA(Group.SCORING, RuleSet::uraDora),
    KAN_DORA(Group.SCORING, RuleSet::kanDora),
    KAZOE_YAKUMAN(Group.SCORING, RuleSet::kazoeYakuman),
    KIRIAGE_MANGAN(Group.SCORING, RuleSet::kiriageMangan),
    DOUBLE_YAKUMAN(Group.SCORING, RuleSet::doubleYakuman),
    COMPOUND_YAKUMAN(Group.SCORING, r -> true),
    DOUBLE_WIND_PAIR_FU(Group.SCORING, RuleSet::doubleWindPairFu),
    RENHOU_MANGAN(Group.SCORING, RuleSet::renhouMangan),
    RED_FIVES(Group.SCORING, 0, 2, 1, r -> r.defaultRedFives().ordinal()),

    HEAD_BUMP(Group.FLOW, RuleSet::headBump),
    TRIPLE_RON_DRAW(Group.FLOW, RuleSet::tripleRonDraw),
    BANKRUPTCY(Group.FLOW, RuleSet::bankruptcy),
    ABORTIVE_DRAWS(Group.FLOW, RuleSet::abortiveDraws),
    NAGASHI_MANGAN(Group.FLOW, RuleSet::nagashiMangan),
    NAGASHI_ALLOWS_CALLS(Group.FLOW, RuleSet::nagashiAllowsCalls),
    AGARI_YAME(Group.FLOW, RuleSet::agariYame),
    WEST_EXTENSION(Group.FLOW, RuleSet::westExtension),
    FORMAL_TENPAI_IGNORES_MELDS(Group.FLOW, RuleSet::tenhou),

    MIN_RIICHI_WALL(Group.CALLS, 0, 70, 1, RuleSet::minRiichiWall),
    NEEDS_RIICHI_DEPOSIT(Group.CALLS, RuleSet::needsRiichiDeposit),
    RIICHI_KAN_KEEPS_MELDS(Group.CALLS, RuleSet::riichiKanKeepsMelds),
    RIICHI_KAN_KEEPS_YAKU(Group.CALLS, r -> r == RuleSet.JPML_A),
    ROB_CONCEALED_KAN(Group.CALLS, RuleSet::robConcealedKan),
    ROB_NORTH_WITHOUT_KOKUSHI(Group.CALLS, RuleSet::robNorthWithoutKokushi),
    DELAYED_OPEN_KAN_DORA(Group.CALLS, RuleSet::delayedOpenKanDora),
    REPLACEMENT_CAPACITY(Group.CALLS, 1, 8, 1, RuleSet::replacementCapacity),
    CALLS_CLEAR_FURITEN(Group.CALLS, r -> r == RuleSet.WRC),
    YAKULESS_FURITEN(Group.CALLS, r -> r == RuleSet.WRC),

    SUUKANTSU_PAO(Group.PAYMENTS, RuleSet::suukantsuPao),
    WHOLE_HAND_PAO(Group.PAYMENTS, RuleSet::wholeHandPao),
    PAO_RON_HONBA_BY_DISCARDER(Group.PAYMENTS, RuleSet::paoRonHonbaByDiscarder),
    PAO_TSUMO_HONBA_SHARED(Group.PAYMENTS, r -> r == RuleSet.WRC);

    public enum Group {
        POINTS, UMA, SCORING, FLOW, CALLS, PAYMENTS;
        public String translationKey() { return "rules.mchjong.group." + name().toLowerCase(Locale.ROOT); }
    }

    private final Group group;
    private final int min, max, step;
    private final boolean toggle;
    private final ToIntFunction<RuleSet> defaults;

    RuleOption(Group group, Predicate<RuleSet> defaults) {
        this(group, 0, 1, 1, r -> defaults.test(r) ? 1 : 0, true);
    }
    RuleOption(Group group, int min, int max, int step, ToIntFunction<RuleSet> defaults) {
        this(group, min, max, step, defaults, false);
    }
    RuleOption(Group group, int min, int max, int step, ToIntFunction<RuleSet> defaults, boolean toggle) {
        this.group = group; this.min = min; this.max = max; this.step = step;
        this.defaults = defaults; this.toggle = toggle;
    }
    RuleOption(int rank) {
        this(Group.UMA, -1_000_000, 1_000_000, 100, r -> rank >= r.players() ? 0
            : (r == RuleSet.JPML_A ? new int[]{15, 5, -5, -15} : r.placementBonus())[rank] * 1000);
    }
    RuleOption(int floating, int rank) {
        this(Group.UMA, -1_000_000, 1_000_000, 100, r -> RuleSet.JPML_A.placementBonus(floating)[rank] * 1000);
    }

    public Group group() { return group; }
    public int min() { return min; }
    public int max() { return max; }
    public int step() { return step; }
    public boolean toggle() { return toggle; }
    public int defaultValue(RuleSet preset) { return defaults.applyAsInt(preset); }
    public boolean valid(int value) { return value >= min && value <= max && value % step == 0; }
    public String translationKey() {
        return group == Group.UMA ? floatingPlayers() < 0 ? "rules.mchjong.uma" : "rules.mchjong.floating_uma"
            : "rules.mchjong.option." + name().toLowerCase(Locale.ROOT);
    }
    public int placementRank() { return group == Group.UMA ? Character.digit(name().charAt(name().length() - 1), 10) : 0; }
    public int floatingPlayers() { return name().startsWith("FLOAT_") ? Character.digit(name().charAt(6), 10) : -1; }

    public boolean visible(RuleConfig rules) {
        if (group != Group.UMA) return true;
        if (placementRank() > rules.players()) return false;
        return floatingPlayers() >= 0 ? rules.floatingPlacement()
            && floatingPlayers() <= rules.players() : !rules.floatingPlacement();
    }

    static RuleOption placement(int floating, int rank) {
        return valueOf(floating < 0 ? "UMA_" + (rank + 1) : "FLOAT_" + floating + "_" + (rank + 1));
    }
}
