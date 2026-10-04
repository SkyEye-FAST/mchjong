package top.skyeyefast.mchjong.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TaiwanHandAnalyzerTest {
    private fun physical(kinds: List<Int>): List<Int> {
        val counts = IntArray(34)
        return kinds.map { Tile.id(it, counts[it]++, false) }
    }
    private val complete = physical(listOf(0, 0, 0, 10, 10, 10, 20, 20, 20, 30, 30, 30, 31, 31, 31, 32, 32))
    private val context = TaiwanWinContext(TaiwanWinContext.Method.SELF_DRAW, Tile.EAST, Tile.EAST, 3,
        setOf(FlowerTile.CHRYSANTHEMUM), TaiwanWinContext.DrawOrigin.ORDINARY, false,
        TaiwanWinContext.Opening.NONE, TaiwanWinContext.Ready.NONE)

    @Test fun boundaryConvertsPhysicalTilesAndFlowerNamesWithoutOrdinalAssumptions() {
        val hand = complete.dropLast(1)
        assertEquals(0, TaiwanHandAnalyzer.shanten(hand, emptyList(), 0))
        assertEquals(setOf(32), TaiwanHandAnalyzer.waits(hand, emptyList(), 0))
        val scored = TaiwanHandAnalyzer.score(hand, emptyList(), 0, complete.last(), context, TaiwanPreset.POCKET_COMMON.rules())!!
        assertEquals(1, scored.awards.single { it.pattern == TaiwanRules.Pattern.SEAT_FLOWER }.tai)
        assertTrue(scored.awards.any { it.pattern == TaiwanRules.Pattern.FIVE_CONCEALED_TRIPLETS })
        assertEquals(5, scored.shape.groups.size)
        val visible = listOf(Tile.id(32, 1, false), Tile.id(32, 1, false), hand.last(), FlowerTile.SPRING.id())
        assertEquals(2, TaiwanHandAnalyzer.analyze(hand, emptyList(), 0, visible).effectiveTiles.single().remaining)
        assertTrue(TaiwanHandAnalyzer.discards(hand, emptyList(), 0, complete.last(), emptyList()).isNotEmpty())
    }

    @Test fun boundaryRejectsDuplicateIdentitiesFlowersAndInvalidMeldProvenance() {
        assertThrows(IllegalArgumentException::class.java) {
            TaiwanWinContext(TaiwanWinContext.Method.DISCARD, 0, Tile.EAST, 1, emptySet(),
                TaiwanWinContext.DrawOrigin.ORDINARY, false, TaiwanWinContext.Opening.NONE, TaiwanWinContext.Ready.NONE)
        }
        val hand = complete.dropLast(1)
        assertThrows(IllegalArgumentException::class.java) {
            TaiwanHandAnalyzer.winningShapes(hand, emptyList(), 0, hand.last())
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaiwanHandAnalyzer.waits(hand.dropLast(1) + FlowerTile.SPRING.id(), emptyList(), 0)
        }
        val invalid = Meld(Meld.Type.TRIPLET, complete.take(3), 0, complete.first())
        assertThrows(IllegalArgumentException::class.java) { TaiwanHandAnalyzer.waits(hand.drop(3), listOf(invalid), 0) }
        val empty = Meld(Meld.Type.SEQUENCE, emptyList(), 3, Tile.ABSENT)
        assertThrows(IllegalArgumentException::class.java) { TaiwanHandAnalyzer.waits(hand.drop(3), listOf(empty), 0) }
        val alias = physical(listOf(4, 1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)) + Tile.id(4, 0, true)
        assertThrows(IllegalArgumentException::class.java) { TaiwanHandAnalyzer.waits(alias, emptyList(), 0) }
    }

    @Test fun adapterContractsNeverLeakLibraryTypes() {
        val types = listOf(TaiwanHandAnalyzer::class.java, TaiwanHandAnalyzer.Score::class.java,
            TaiwanHandAnalyzer.Analysis::class.java, TaiwanRules::class.java, TaiwanWinContext::class.java)
        for (type in types) for (method in type.methods) {
            assertFalse(method.toGenericString().contains("top.skyeyefast.taiwan"), method.toGenericString())
        }
    }
}
