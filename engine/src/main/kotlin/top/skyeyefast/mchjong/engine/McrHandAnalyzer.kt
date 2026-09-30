package top.skyeyefast.mchjong.engine

import top.skyeyefast.mcr.ChowPosition
import top.skyeyefast.mcr.Hand as LibraryHand
import top.skyeyefast.mcr.McrMahjong
import top.skyeyefast.mcr.Meld as LibraryMeld
import top.skyeyefast.mcr.RelativePlayer
import top.skyeyefast.mcr.ScoreResult
import top.skyeyefast.mcr.Tile as LibraryTile
import top.skyeyefast.mcr.WinContext as LibraryContext
import top.skyeyefast.mcr.Wind
import top.skyeyefast.mcr.WinMethod

/** The sole mcr-mahjong boundary; public inputs and results contain only engine/JDK types. */
object McrHandAnalyzer {
    // Engine kinds are m/p/s and white/green/red; the library uses m/s/p and red/green/white.
    private val byKind = mapOf(
        0 to LibraryTile.M1, 1 to LibraryTile.M2, 2 to LibraryTile.M3,
        3 to LibraryTile.M4, 4 to LibraryTile.M5, 5 to LibraryTile.M6,
        6 to LibraryTile.M7, 7 to LibraryTile.M8, 8 to LibraryTile.M9,
        9 to LibraryTile.P1, 10 to LibraryTile.P2, 11 to LibraryTile.P3,
        12 to LibraryTile.P4, 13 to LibraryTile.P5, 14 to LibraryTile.P6,
        15 to LibraryTile.P7, 16 to LibraryTile.P8, 17 to LibraryTile.P9,
        18 to LibraryTile.S1, 19 to LibraryTile.S2, 20 to LibraryTile.S3,
        21 to LibraryTile.S4, 22 to LibraryTile.S5, 23 to LibraryTile.S6,
        24 to LibraryTile.S7, 25 to LibraryTile.S8, 26 to LibraryTile.S9,
        Tile.EAST to LibraryTile.EAST, Tile.SOUTH to LibraryTile.SOUTH,
        Tile.WEST to LibraryTile.WEST, Tile.NORTH to LibraryTile.NORTH,
        Tile.WHITE to LibraryTile.WHITE, Tile.GREEN to LibraryTile.GREEN, Tile.RED to LibraryTile.RED,
    )
    private val byTile = byKind.entries.associate { (kind, tile) -> tile to kind }

    @JvmSynthetic
    internal fun libraryTile(id: Int): LibraryTile = byKind.getValue(Tile.kind(id))
    @JvmSynthetic
    internal fun kind(tile: LibraryTile): Int = byTile.getValue(tile)

    private fun unique(ids: List<Int>) {
        val physical = ids.map(Tile::physicalId)
        require(physical.distinct().size == physical.size) { "A physical tile occurs more than once" }
    }

    private fun relative(from: Int, owner: Int): RelativePlayer {
        require(from in 0..3 && owner in 0..3) { "MCR seats must be in 0..3" }
        return when (Math.floorMod(from - owner, 4)) {
            3 -> RelativePlayer.LEFT
            2 -> RelativePlayer.OPPOSITE
            1 -> RelativePlayer.RIGHT
            else -> throw IllegalArgumentException("An exposed meld needs another seat as its supplier")
        }
    }

    @JvmSynthetic
    internal fun libraryMeld(meld: Meld, owner: Int): LibraryMeld {
        require(owner in 0..3) { "MCR seats must be in 0..3" }
        val ids = meld.tiles()
        val quad = when (meld.type()) {
            Meld.Type.SEQUENCE, Meld.Type.TRIPLET -> false
            Meld.Type.OPEN_QUAD, Meld.Type.CONCEALED_QUAD, Meld.Type.ADDED_QUAD -> true
        }
        require(ids.size == if (quad) 4 else 3) { "Incorrect physical meld size" }
        unique(ids)
        val kinds = ids.map(Tile::kind).sorted()
        if (meld.closed()) {
            require(meld.fromSeat() == owner && meld.calledTile() == Tile.ABSENT) { "Invalid concealed kong origin" }
        } else {
            require(meld.calledTile() in ids) { "The called physical tile must belong to the meld" }
        }
        val from = if (meld.closed()) null else relative(meld.fromSeat(), owner)
        if (meld.type() == Meld.Type.SEQUENCE) {
            require(from == RelativePlayer.LEFT) { "A chow must come from the seat on the left" }
            require(kinds[0] < 27 && kinds[0] / 9 == kinds[2] / 9
                && kinds[1] == kinds[0] + 1 && kinds[2] == kinds[0] + 2) { "Invalid chow tiles" }
            val called = when (Tile.kind(meld.calledTile())) {
                kinds[0] -> ChowPosition.LOW
                kinds[1] -> ChowPosition.MIDDLE
                kinds[2] -> ChowPosition.HIGH
                else -> throw IllegalArgumentException("Called tile is not in the chow")
            }
            return LibraryMeld.Chow(byKind.getValue(kinds[1]), called)
        }
        require(kinds.all { it == kinds[0] }) { "A pung or kong needs identical kinds" }
        val tile = byKind.getValue(kinds[0])
        return when (meld.type()) {
            Meld.Type.TRIPLET -> LibraryMeld.Pung(tile, requireNotNull(from))
            Meld.Type.OPEN_QUAD -> LibraryMeld.Kong(tile, requireNotNull(from), false)
            Meld.Type.CONCEALED_QUAD -> LibraryMeld.Kong(tile, null, false)
            Meld.Type.ADDED_QUAD -> LibraryMeld.Kong(tile, requireNotNull(from), true)
            Meld.Type.SEQUENCE -> error("Chow already converted")
        }
    }

    private fun wind(kind: Int): Wind = when (kind) {
        Tile.EAST -> Wind.EAST
        Tile.SOUTH -> Wind.SOUTH
        Tile.WEST -> Wind.WEST
        Tile.NORTH -> Wind.NORTH
        else -> throw IllegalArgumentException("Not an ordinary wind kind: $kind")
    }

    @JvmSynthetic
    internal fun libraryContext(context: McrWinContext): LibraryContext = LibraryContext(
        method = when (context.method()) {
            McrWinContext.Method.SELF_DRAW -> WinMethod.SELF_DRAW
            McrWinContext.Method.DISCARD -> WinMethod.DISCARD
        },
        prevalentWind = wind(context.roundWind()),
        seatWind = wind(context.seatWind()),
        flowerCount = context.flowerCount(),
        lastTile = context.lastCopy(),
        kongInvolved = when (context.kongWin()) {
            McrWinContext.KongWin.NONE -> false
            McrWinContext.KongWin.REPLACEMENT, McrWinContext.KongWin.ROBBED -> true
        },
        wallLast = context.wallLast(),
    )

    private fun hand(concealed: List<Int>, melds: List<Meld>, owner: Int): LibraryHand {
        require(owner in 0..3) { "MCR seats must be in 0..3" }
        unique(concealed + melds.flatMap { it.tiles() })
        return LibraryHand(concealed.map(::libraryTile), melds.map { libraryMeld(it, owner) })
    }

    /** Validate physical identities and fixed melds without running shape or scoring algorithms. */
    @JvmStatic
    fun validateHand(concealed: List<Int>, melds: List<Meld>, owner: Int) {
        hand(concealed, melds, owner)
    }

    /** Concealed tiles exclude the drawn/winning tile: size + 3 * meld count must equal 13. */
    @JvmStatic
    fun shanten(concealed: List<Int>, melds: List<Meld>, owner: Int): Int =
        McrMahjong.shanten(hand(concealed, melds, owner))

    @JvmRecord
    data class Progress(val shanten: Int, val effectiveKinds: Set<Int>, val remainingCount: Int)

    /** Known tiles exclude the supplied hand/melds, including any proposed discard. */
    @JvmStatic
    fun analyze(concealed: List<Int>, melds: List<Meld>, owner: Int, visible: List<Int>): Progress {
        unique(concealed + melds.flatMap { it.tiles() } + visible)
        val analysis = McrMahjong.analyze(hand(concealed, melds, owner), visible.map(::libraryTile))
        return Progress(analysis.shanten, java.util.Collections.unmodifiableSet(analysis.effectiveTiles
            .filter { it.remainingCopies > 0 }.map { kind(it.tile) }.toSortedSet()), analysis.remainingCount)
    }

    /** Structural waits as engine kinds; excludes fifth copies already owned in hand/melds. */
    @JvmStatic
    fun waits(concealed: List<Int>, melds: List<Meld>, owner: Int): Set<Int> {
        val hand = hand(concealed, melds, owner)
        return java.util.Collections.unmodifiableSet(McrMahjong.waitingTiles(hand)
            .filter { hand.remainingCopies(it) > 0 }.map(::kind).toSortedSet())
    }

    /** Invalid inputs throw; a valid non-winning shape returns null. Minimum fan is a separate result field. */
    @JvmStatic
    fun score(concealed: List<Int>, melds: List<Meld>, owner: Int, winningTile: Int,
              context: McrWinContext): McrHandScore? {
        unique(concealed + melds.flatMap { it.tiles() } + winningTile)
        return when (val result = McrMahjong.score(hand(concealed, melds, owner), libraryTile(winningTile), libraryContext(context))) {
            ScoreResult.NotWinning -> null
            is ScoreResult.Winning -> McrHandScore(result.totalFan, result.nonFlowerFan, result.meetsMinimum,
                result.fans.map { McrHandScore.Fan(it.fan.name, it.count, it.points, it.isMixedKongPair) })
        }
    }
}
