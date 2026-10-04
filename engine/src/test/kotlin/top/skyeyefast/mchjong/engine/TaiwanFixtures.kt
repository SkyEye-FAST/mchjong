package top.skyeyefast.mchjong.engine

import org.junit.jupiter.api.Assertions.*
import top.skyeyefast.mchjong.engine.TaiwanAction.Type

object TaiwanFixtures {
    val pocket = TaiwanPreset.POCKET_COMMON.rules()
    /** Build a complete physical permutation, not a privileged game-state mutation. 136..143 are flowers. */
    fun fixture(hands: Map<Int, List<Int>> = emptyMap(), draws: List<Int> = emptyList(), tail: List<Int> = emptyList(),
                        rules: TaiwanRules = pocket, continuation: Int = 0, lastDraw: Int? = null,
                        dealer: Int = 0, roundWind: Int = Tile.EAST): TaiwanGame {
        val unused = (if (rules.flowers == TaiwanRules.Flowers.NONE) Tile.set(false, RedFives.NONE) else Tile.standard144Set()).toMutableList()
        fun allocate(spec: Int): Int {
            val tile = unused.first { if (spec >= 136) it == spec else !Tile.isFlower(it) && Tile.kind(it) == spec }
            unused.remove(tile)
            return tile
        }
        val assigned = List(4) { s -> hands[s].orEmpty().map(::allocate).toMutableList() }
        val next = draws.map(::allocate)
        val replacements = tail.map(::allocate)
        val finalTile = lastDraw?.let(::allocate)
        java.util.Collections.shuffle(unused, java.util.Random(100))
        for (s in 0..3) while (assigned[s].size < if (s == dealer) 17 else 16) {
            val tile = unused.first { !Tile.isFlower(it) }
            assigned[s] += tile
            unused.remove(tile)
        }
        val ordered = mutableListOf<Int>()
        repeat(4) { packet -> repeat(4) { offset -> ordered += assigned[(dealer + offset) % 4].subList(packet * 4, packet * 4 + 4) } }
        ordered += assigned[dealer][16]
        ordered += next
        if (finalTile == null) ordered += unused else {
            ordered += unused.dropLast(16)
            ordered += finalTile
            ordered += unused.takeLast(16)
        }
        ordered += replacements.reversed()
        val opening = TaiwanOpening(dealer, listOf(1, 1, 1))
        val cut = opening.cutIndex(ordered.size)
        return TaiwanGame(rules, opening, ordered.takeLast(cut) + ordered.dropLast(cut), roundWind, continuation)
    }
    fun act(game: TaiwanGame, seat: Int, type: Type, kind: Int? = null) {
        val d = game.decisions().single { it.seat == seat }
        val index = d.actions.indexOfFirst { it.type == type && (kind == null || it.tiles.any { t -> Tile.kind(t) == kind }) }
        assertTrue(index >= 0, "Missing $type/$kind at seat $seat: ${d.actions.map { it.type }}")
        game.submit(seat, d.token, index)
    }
    fun pass(game: TaiwanGame) {
        for (decision in game.decisions().toList()) act(game, decision.seat, Type.PASS)
    }
    val wait = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 33)

}
