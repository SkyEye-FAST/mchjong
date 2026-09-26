package top.skyeyefast.mchjong.engine;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import static top.skyeyefast.mchjong.engine.RuleOption.*;

/** A complete immutable rules snapshot. Presets supply values, never hidden runtime exceptions. */
public record RuleConfig(RuleSet preset, Map<RuleOption, Integer> settings) {
    public RuleConfig {
        Objects.requireNonNull(preset);
        var copy = new EnumMap<RuleOption, Integer>(RuleOption.class);
        copy.putAll(settings);
        settings = java.util.Collections.unmodifiableMap(copy);
        if (settings.size() != RuleOption.values().length) throw new IllegalArgumentException("Incomplete rules");
        for (var option : RuleOption.values()) {
            var value = settings.get(option);
            if (value == null || !option.valid(value)) throw new IllegalArgumentException("Invalid rule: " + option);
        }
    }

    public static RuleConfig from(RuleSet preset) {
        var values = new EnumMap<RuleOption, Integer>(RuleOption.class);
        for (var option : RuleOption.values()) values.put(option, option.defaultValue(preset));
        return new RuleConfig(preset, values);
    }
    public int get(RuleOption option) { return settings.get(option); }
    public boolean enabled(RuleOption option) { return get(option) != 0; }
    public RuleConfig with(RuleOption option, int value) {
        var values = new EnumMap<>(settings);
        values.put(option, value);
        return new RuleConfig(preset, values);
    }
    public boolean custom() {
        return settings.entrySet().stream().anyMatch(entry -> !preset.adjustable(entry.getKey())
            && entry.getValue() != entry.getKey().defaultValue(preset));
    }

    /** Retain supported table variants while replacing all fixed rules with the new preset. */
    public RuleConfig withPreset(RuleSet next) {
        var config = next.config();
        for (var option : RuleOption.values())
            if (next.adjustable(option)) config = config.with(option, get(option));
        return config;
    }
    public String name() { return custom() ? "CUSTOM_" + preset.name() : preset.name(); }
    public String translationKey() { return custom() ? "rules.mchjong.custom" : preset.translationKey(); }
    public int players() { return preset.players(); }
    public boolean sanma() { return players() == 3; }
    public int startingPoints() { return get(STARTING_POINTS); }
    public int returnPoints() { return get(RETURN_POINTS); }
    public int targetPoints() { return get(TARGET_POINTS); }
    public boolean floatingPlacement() { return enabled(FLOATING_PLACEMENT); }
    public boolean sharedRanks() { return enabled(SHARED_RANKS); }
    public boolean roundSharedPlacement() { return enabled(ROUND_SHARED_PLACEMENT); }
    public boolean awardFinalDeposits() { return enabled(AWARD_FINAL_DEPOSITS); }
    public boolean experienceRewards() { return enabled(EXPERIENCE_REWARDS); }
    public boolean deductNegativeExperience() { return enabled(DEDUCT_NEGATIVE_EXPERIENCE); }
    public int[] placementPoints(int floating) {
        int[] points = new int[players()];
        for (int rank = 0; rank < points.length; rank++)
            points[rank] = get(RuleOption.placement(floatingPlacement() ? floating : -1, rank));
        return points;
    }
    public boolean kuitan() { return enabled(KUITAN); }
    public int minHan() { return get(MIN_HAN); }
    public boolean ippatsuCountsTowardMinimum() { return enabled(IPPATSU_COUNTS_TOWARD_MINIMUM); }
    public int matchLength() { return get(MATCH_LENGTH); }
    public int scheduledRounds() { return matchLength() * players(); }
    public boolean ippatsu() { return enabled(IPPATSU); }
    public boolean uraDora() { return enabled(URA_DORA); }
    public boolean kanDora() { return enabled(KAN_DORA); }
    public boolean kazoeYakuman() { return enabled(KAZOE_YAKUMAN); }
    public boolean kiriageMangan() { return enabled(KIRIAGE_MANGAN); }
    public boolean doubleYakuman() { return enabled(DOUBLE_YAKUMAN); }
    public boolean compoundYakuman() { return enabled(COMPOUND_YAKUMAN); }
    public boolean doubleWindPairFu() { return enabled(DOUBLE_WIND_PAIR_FU); }
    public boolean renhouMangan() { return enabled(RENHOU_MANGAN); }
    public boolean allows(RedFives reds) {
        return reds == redFives();
    }
    public RedFives redFives() { return RedFives.values()[get(RED_FIVES)]; }
    public boolean headBump() { return enabled(HEAD_BUMP); }
    public boolean tripleRonDraw() { return enabled(TRIPLE_RON_DRAW); }
    public boolean bankruptcy() { return enabled(BANKRUPTCY); }
    public boolean abortiveDraws() { return enabled(ABORTIVE_DRAWS); }
    public boolean nagashiMangan() { return enabled(NAGASHI_MANGAN); }
    public boolean nagashiAllowsCalls() { return enabled(NAGASHI_ALLOWS_CALLS); }
    public boolean agariYame() { return enabled(AGARI_YAME); }
    public boolean extension() { return enabled(EXTENSION); }
    public boolean formalTenpaiIgnoresMelds() { return enabled(FORMAL_TENPAI_IGNORES_MELDS); }
    public int minRiichiWall() { return get(MIN_RIICHI_WALL); }
    public boolean needsRiichiDeposit() { return enabled(NEEDS_RIICHI_DEPOSIT); }
    public boolean riichiKanKeepsMelds() { return enabled(RIICHI_KAN_KEEPS_MELDS); }
    public boolean riichiKanKeepsYaku() { return enabled(RIICHI_KAN_KEEPS_YAKU); }
    public boolean robConcealedKan() { return enabled(ROB_CONCEALED_KAN); }
    public boolean robNorthWithoutKokushi() { return enabled(ROB_NORTH_WITHOUT_KOKUSHI); }
    public boolean delayedOpenKanDora() { return enabled(DELAYED_OPEN_KAN_DORA); }
    public int replacementCapacity() { return get(REPLACEMENT_CAPACITY); }
    public boolean callsClearFuriten() { return enabled(CALLS_CLEAR_FURITEN); }
    public boolean yakulessFuriten() { return enabled(YAKULESS_FURITEN); }
    public boolean suukantsuPao() { return enabled(SUUKANTSU_PAO); }
    public boolean wholeHandPao() { return enabled(WHOLE_HAND_PAO); }
    public boolean paoRonHonbaByDiscarder() { return enabled(PAO_RON_HONBA_BY_DISCARDER); }
    public boolean paoTsumoHonbaShared() { return enabled(PAO_TSUMO_HONBA_SHARED); }
}
