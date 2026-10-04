package top.skyeyefast.mchjong.engine

/** Engine-owned scoring and single-hand match settings; see TAIWAN_DESIGN.md for sources. */
class TaiwanRules(
    val name: String,
    values: Map<Pattern, Int>,
    exclusions: Map<Pattern, Set<Pattern>>,
    sources: Map<Pattern, Source>,
    val flowers: Flowers,
    val flowerSets: FlowerSets,
    val pinfu: Pinfu,
    val replacements: Replacements,
    val taiLimit: Int?,
    val reserve: Reserve = Reserve.SIXTEEN_PLUS_KONGS,
    val payment: Payment = Payment(),
) {
    enum class Reserve { FIXED_SIXTEEN, SIXTEEN_PLUS_KONGS }
    data class Payment(val base: Long = 1, val perTai: Long = 1, val dealerTai: Int = 1, val repeatTai: Int = 2) {
        init {
            require(base in 0..1_000_000 && perTai in 1..1_000_000)
            require(dealerTai in 0..1000 && repeatTai in 0..1000)
        }
    }
    enum class Pattern {
        HEAVENLY_WIN, EARTHLY_WIN, HUMAN_WIN, HEAVENLY_READY, EARTHLY_READY,
        EIGHT_FLOWERS, SEVEN_ROBS_ONE, BIG_FOUR_WINDS, ALL_HONORS, FULL_FLUSH,
        SMALL_FOUR_WINDS, BIG_THREE_DRAGONS, FIVE_CONCEALED_TRIPLETS, FOUR_CONCEALED_TRIPLETS,
        ALL_TRIPLETS, HALF_FLUSH, SMALL_THREE_DRAGONS, PINFU, THREE_CONCEALED_TRIPLETS,
        CONCEALED, SELF_DRAW, CONCEALED_SELF_DRAW, ALL_FROM_OTHERS, HALF_FROM_OTHERS,
        REPLACEMENT_WIN, LAST_DRAW, LAST_DISCARD, ROBBING_KONG, DECLARED_READY, SINGLE_WAIT,
        RED_DRAGON, GREEN_DRAGON, WHITE_DRAGON, FLOWER_SET, ROUND_WIND, SEAT_WIND, SEAT_FLOWER
    }
    enum class Flowers { NONE, SEAT_NUMBER }
    enum class FlowerSets { ADD_SEAT_FLOWER, REPLACE_SEAT_FLOWER }
    enum class Pinfu { DISCARD_SEQUENCES, MULTIPLE_WAIT_DISCARD_SEQUENCES }
    enum class Replacements { KONG_ONLY, KONG_OR_FLOWER }
    @JvmRecord data class Source(val url: String, val clause: String)
    val values: Map<Pattern, Int> = java.util.Map.copyOf(values)
    val exclusions: Map<Pattern, Set<Pattern>> = java.util.Map.copyOf(exclusions.mapValues { java.util.Set.copyOf(it.value) })
    val sources: Map<Pattern, Source> = java.util.Map.copyOf(sources)
    init {
        require(name.isNotBlank())
        require(values.keys == Pattern.entries.toSet() && sources.keys == values.keys)
        require(values.values.all { it in 0..1000 } && (taiLimit == null || taiLimit in 1..1000))
        require(sources.values.all { it.url.startsWith("https://") && it.clause.isNotBlank() })
        fun visit(pattern: Pattern, path: Set<Pattern>) {
            require(pattern !in path) { "Cyclic scoring exclusions" }
            this.exclusions[pattern].orEmpty().forEach { visit(it, path + pattern) }
        }
        Pattern.entries.forEach { visit(it, emptySet()) }
    }
}

/** Descriptive common presets, not official regional standards. */
enum class TaiwanPreset {
    POCKET_COMMON, SOUTHERN_COMMON;
    fun rules(): TaiwanRules = TaiwanHandAnalyzer.presetRules(this)
}
