package top.skyeyefast.mchjong.engine

import top.skyeyefast.taiwan.Hand
import top.skyeyefast.taiwan.TaiwanMahjong
import top.skyeyefast.taiwan.TaiwanProfiles
import top.skyeyefast.taiwan.TaiwanScoring
import top.skyeyefast.taiwan.TaiwanScoringProfile

/** Sole production taiwan-mahjong boundary. Public contracts use engine/JDK types only. */
object TaiwanHandAnalyzer {
    enum class GroupType { SEQUENCE, TRIPLET }

    /** Kind is the lowest kind of a sequence, or the repeated kind of a triplet. */
    @JvmRecord
    data class Group(val type: GroupType, val kind: Int)

    /** Only concealed groups are returned; the caller retains the fixed melds and their provenance. */
    class Shape(val pair: Int, groups: List<Group>) {
        val groups: List<Group> = java.util.List.copyOf(groups)
    }

    /** Concealed tiles exclude the winning tile. Invalid identities, sizes and melds throw. */
    @JvmStatic
    fun winningShapes(concealed: List<Int>, melds: List<Meld>, owner: Int, winningTile: Int): List<Shape> {
        validateWinningTile(concealed, melds, owner, winningTile)
        return java.util.List.copyOf(TaiwanMahjong.winningShapes(libraryHand(concealed + winningTile, melds)).map(::shape))
    }

    private fun validateWinningTile(concealed: List<Int>, melds: List<Meld>, owner: Int, winningTile: Int) {
        validate(concealed, melds, owner)
        Tile.kind(winningTile)
        val physical = Tile.physicalId(winningTile)
        require((concealed + melds.flatMap { it.tiles() }).none { Tile.physicalId(it) == physical }) {
            "Winning tile is already owned"
        }
    }

    /** Structural waits, not scoring/claim permission; excludes fifth copies in concealed and fixed tiles. */
    @JvmStatic
    fun waits(concealed: List<Int>, melds: List<Meld>, owner: Int): Set<Int> {
        validate(concealed, melds, owner)
        return TaiwanMahjong.waitingTiles(libraryHand(concealed, melds))
    }

    private fun validate(concealed: List<Int>, melds: List<Meld>, owner: Int) {
        require(owner in 0..3) { "Taiwanese seats must be in 0..3" }
        require(melds.size <= 5 && concealed.size + 3 * melds.size == 16) {
            "Taiwanese concealed hand must contain 16 minus three tiles per fixed meld"
        }
        val seen = HashSet<Int>()
        for (tile in concealed + melds.flatMap { it.tiles() }) {
            Tile.kind(tile)
            require(seen.add(Tile.physicalId(tile))) { "Duplicate physical tile" }
        }
        for (meld in melds) {
            val kinds = meld.tiles().map(Tile::kind).sorted()
            val quad = meld.type() in setOf(Meld.Type.OPEN_QUAD, Meld.Type.CONCEALED_QUAD, Meld.Type.ADDED_QUAD)
            require(kinds.size == if (quad) 4 else 3) { "Invalid meld size" }
            if (meld.type() == Meld.Type.SEQUENCE) {
                require(kinds[0] < 27 && kinds[0] / 9 == kinds[2] / 9 &&
                    kinds[1] == kinds[0] + 1 && kinds[2] == kinds[0] + 2) { "Invalid sequence" }
            } else require(kinds.all { it == kinds[0] }) { "Invalid triplet or kong" }
            if (meld.closed()) {
                require(meld.fromSeat() == owner && meld.calledTile() == Tile.ABSENT) { "Invalid concealed kong provenance" }
            } else {
                require(meld.fromSeat() in 0..3 && meld.fromSeat() != owner && meld.calledTile() in meld.tiles()) {
                    "Invalid called meld provenance"
                }
                if (meld.type() == Meld.Type.SEQUENCE) {
                    require(meld.fromSeat() == (owner + 3) % 4) { "Chow must come from the preceding seat" }
                }
            }
        }
    }

    @JvmRecord data class EffectiveTile(val kind: Int, val remaining: Int)
    class Analysis(val shanten: Int, effectiveTiles: List<EffectiveTile>) {
        val effectiveTiles: List<EffectiveTile> = java.util.List.copyOf(effectiveTiles)
    }
    @JvmRecord data class Discard(val kind: Int, val analysis: Analysis)
    @JvmRecord data class Award(val pattern: TaiwanRules.Pattern, val units: Int, val tai: Int, val source: TaiwanRules.Source)
    class Score(val shape: Shape, val winningGroup: Int, awards: List<Award>, val rawTai: Int, val tai: Int) {
        val awards: List<Award> = java.util.List.copyOf(awards)
    }
    enum class FlowerEvent { EIGHT_AFTER_REPLACEMENT, SEVEN_AFTER_REPLACEMENT, SEVEN_ON_OPPONENT_FLOWER }
    class FlowerScore(val award: Award, val handScore: Score?, val rawTai: Int, val tai: Int)

    /** Event provenance and the remaining flower's owner are established by the future game host. */
    @JvmStatic
    fun flowerWin(concealed: List<Int>, melds: List<Meld>, owner: Int, replacementTile: Int,
                  context: TaiwanWinContext, event: FlowerEvent, rules: TaiwanRules): FlowerScore? {
        validate(concealed, melds, owner)
        val c = libraryContext(context)
        val replacement = if (event == FlowerEvent.SEVEN_ON_OPPONENT_FLOWER) {
            require(replacementTile == Tile.ABSENT)
            null
        } else {
            validateWinningTile(concealed, melds, owner, replacementTile)
            top.skyeyefast.taiwan.ReplacementWin(libraryHand(concealed, melds), Tile.kind(replacementTile), c)
        }
        return TaiwanScoring.flowerWin(c.flowers, top.skyeyefast.taiwan.FlowerEvent.valueOf(event.name), libraryRules(rules), replacement)?.let {
            FlowerScore(Award(TaiwanRules.Pattern.valueOf(it.award.pattern.name), it.award.units, it.award.tai,
                TaiwanRules.Source(it.award.source.url, it.award.source.clause)), it.handScore?.let(::score), it.rawTai, it.tai)
        }
    }

    @JvmStatic
    fun shanten(concealed: List<Int>, melds: List<Meld>, owner: Int): Int {
        validate(concealed, melds, owner)
        return TaiwanMahjong.shanten(libraryHand(concealed, melds))
    }

    @JvmStatic
    fun analyze(concealed: List<Int>, melds: List<Meld>, owner: Int, visible: List<Int>): Analysis {
        validate(concealed, melds, owner)
        return analysis(TaiwanMahjong.analyze(libraryHand(concealed, melds), visibleKinds(concealed, melds, visible)))
    }

    @JvmStatic
    fun discards(concealed: List<Int>, melds: List<Meld>, owner: Int, drawnTile: Int, visible: List<Int>): List<Discard> {
        validateWinningTile(concealed, melds, owner, drawnTile)
        val complete = concealed + drawnTile
        return java.util.List.copyOf(TaiwanMahjong.discards(libraryHand(complete, melds), visibleKinds(complete, melds, visible))
            .map { Discard(it.kind, analysis(it.analysis)) })
    }

    @JvmStatic
    fun score(concealed: List<Int>, melds: List<Meld>, owner: Int, winningTile: Int, context: TaiwanWinContext, rules: TaiwanRules): Score? {
        validateWinningTile(concealed, melds, owner, winningTile)
        return TaiwanScoring.score(libraryHand(concealed, melds), Tile.kind(winningTile), libraryContext(context), libraryRules(rules))?.let(::score)
    }

    private fun visibleKinds(concealed: List<Int>, melds: List<Meld>, visible: List<Int>): List<Int> {
        val owned = (concealed + melds.flatMap { it.tiles() }).map(Tile::physicalId).toSet()
        return visible.filterNot(Tile::isFlower).distinctBy(Tile::physicalId)
            .filter { Tile.physicalId(it) !in owned }.map(Tile::kind)
    }

    private fun libraryHand(concealed: List<Int>, melds: List<Meld>) = Hand(concealed.map(Tile::kind), melds.map {
        top.skyeyefast.taiwan.Group(when (it.type()) {
            Meld.Type.SEQUENCE -> top.skyeyefast.taiwan.GroupType.SEQUENCE
            Meld.Type.TRIPLET -> top.skyeyefast.taiwan.GroupType.TRIPLET
            Meld.Type.OPEN_QUAD -> top.skyeyefast.taiwan.GroupType.OPEN_KONG
            Meld.Type.CONCEALED_QUAD -> top.skyeyefast.taiwan.GroupType.CONCEALED_KONG
            Meld.Type.ADDED_QUAD -> top.skyeyefast.taiwan.GroupType.ADDED_KONG
        }, it.tiles().minOf(Tile::kind))
    })

    private fun shape(value: top.skyeyefast.taiwan.WinningShape) = Shape(value.pair, value.groups.map {
        Group(GroupType.valueOf(it.type.name), it.kind)
    })
    private fun analysis(value: top.skyeyefast.taiwan.HandAnalysis) = Analysis(value.shanten, value.effectiveTiles.map {
        EffectiveTile(it.kind, it.remaining)
    })
    private fun score(value: top.skyeyefast.taiwan.TaiwanScore) = Score(shape(value.shape), value.winningGroup,
        value.awards.map { Award(TaiwanRules.Pattern.valueOf(it.pattern.name), it.units, it.tai, TaiwanRules.Source(it.source.url, it.source.clause)) },
        value.rawTai, value.tai)

    private fun libraryContext(c: TaiwanWinContext) = top.skyeyefast.taiwan.WinContext(
        top.skyeyefast.taiwan.WinMethod.valueOf(c.method.name), c.seatWind, c.roundWind, c.flowerNumber,
        c.flowers.map { top.skyeyefast.taiwan.Flower.valueOf(it.name) }.toSet(),
        top.skyeyefast.taiwan.DrawOrigin.valueOf(c.drawOrigin.name), c.lastTile,
        top.skyeyefast.taiwan.OpeningWin.valueOf(c.opening.name), top.skyeyefast.taiwan.ReadyDeclaration.valueOf(c.ready.name),
    )

    private fun libraryRules(r: TaiwanRules) = TaiwanScoringProfile(r.name,
        r.values.mapKeys { top.skyeyefast.taiwan.Pattern.valueOf(it.key.name) },
        r.exclusions.mapKeys { top.skyeyefast.taiwan.Pattern.valueOf(it.key.name) }.mapValues { entry ->
            entry.value.map { top.skyeyefast.taiwan.Pattern.valueOf(it.name) }.toSet()
        },
        r.sources.mapKeys { top.skyeyefast.taiwan.Pattern.valueOf(it.key.name) }.mapValues {
            top.skyeyefast.taiwan.RuleSource(it.value.url, it.value.clause)
        }, top.skyeyefast.taiwan.FlowerPolicy.valueOf(r.flowers.name),
        top.skyeyefast.taiwan.FlowerSetPolicy.valueOf(r.flowerSets.name),
        top.skyeyefast.taiwan.PinfuPolicy.valueOf(r.pinfu.name),
        top.skyeyefast.taiwan.ReplacementPolicy.valueOf(r.replacements.name), r.taiLimit,
    )

    @JvmStatic
    fun presetRules(preset: TaiwanPreset): TaiwanRules {
        val r = when (preset) {
            TaiwanPreset.POCKET_COMMON -> TaiwanProfiles.POCKET_COMMON
            TaiwanPreset.SOUTHERN_COMMON -> TaiwanProfiles.SOUTHERN_COMMON
        }
        return TaiwanRules(r.name, r.values.mapKeys { TaiwanRules.Pattern.valueOf(it.key.name) },
            r.exclusions.mapKeys { TaiwanRules.Pattern.valueOf(it.key.name) }.mapValues { entry ->
                entry.value.map { TaiwanRules.Pattern.valueOf(it.name) }.toSet()
            }, r.sources.mapKeys { TaiwanRules.Pattern.valueOf(it.key.name) }.mapValues { TaiwanRules.Source(it.value.url, it.value.clause) },
            TaiwanRules.Flowers.valueOf(r.flowers.name), TaiwanRules.FlowerSets.valueOf(r.flowerSets.name),
            TaiwanRules.Pinfu.valueOf(r.pinfu.name), TaiwanRules.Replacements.valueOf(r.replacements.name), r.taiLimit)
    }
}
