package top.skyeyefast.mchjong.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.skyeyefast.mcr.ChowPosition
import top.skyeyefast.mcr.Meld as LibraryMeld
import top.skyeyefast.mcr.RelativePlayer
import top.skyeyefast.mcr.Tile as LibraryTile
import top.skyeyefast.mcr.Wind
import top.skyeyefast.mcr.WinMethod

class McrHandAnalyzerTest {
    private fun context(method: McrWinContext.Method = McrWinContext.Method.DISCARD, flowers: Int = 0) =
        McrWinContext(method, Tile.EAST, Tile.EAST, false, McrWinContext.KongWin.NONE, false, flowers)

    @Test fun mapsAll34KindsByNamedLibraryTiles() {
        val expected = listOf(
            LibraryTile.M1, LibraryTile.M2, LibraryTile.M3, LibraryTile.M4, LibraryTile.M5,
            LibraryTile.M6, LibraryTile.M7, LibraryTile.M8, LibraryTile.M9,
            LibraryTile.P1, LibraryTile.P2, LibraryTile.P3, LibraryTile.P4, LibraryTile.P5,
            LibraryTile.P6, LibraryTile.P7, LibraryTile.P8, LibraryTile.P9,
            LibraryTile.S1, LibraryTile.S2, LibraryTile.S3, LibraryTile.S4, LibraryTile.S5,
            LibraryTile.S6, LibraryTile.S7, LibraryTile.S8, LibraryTile.S9,
            LibraryTile.EAST, LibraryTile.SOUTH, LibraryTile.WEST, LibraryTile.NORTH,
            LibraryTile.WHITE, LibraryTile.GREEN, LibraryTile.RED,
        )
        expected.forEachIndexed { kind, tile ->
            assertEquals(tile, McrHandAnalyzer.libraryTile(Tile.id(kind, 0, false)))
            assertEquals(kind, McrHandAnalyzer.kind(tile))
        }
        for (kind in listOf(4, 13, 22))
            assertEquals(expected[kind], McrHandAnalyzer.libraryTile(Tile.id(kind, 0, true)))
    }

    @Test fun convertsMeldShapesAndRelativeSuppliers() {
        val chow = TestHands.tiles("345p")
        for ((index, position) in listOf(ChowPosition.LOW, ChowPosition.MIDDLE, ChowPosition.HIGH).withIndex()) {
            assertEquals(LibraryMeld.Chow(LibraryTile.P4, position),
                McrHandAnalyzer.libraryMeld(Meld(Meld.Type.SEQUENCE, chow.reversed(), 0, chow[index]), 1))
        }
        val pung = TestHands.tiles("555s")
        for ((supplier, relative) in listOf(0 to RelativePlayer.LEFT, 3 to RelativePlayer.OPPOSITE, 2 to RelativePlayer.RIGHT)) {
            assertEquals(LibraryMeld.Pung(LibraryTile.S5, relative),
                McrHandAnalyzer.libraryMeld(Meld(Meld.Type.TRIPLET, pung, supplier, pung[0]), 1))
        }
        val kong = TestHands.tiles("5555s")
        assertEquals(LibraryMeld.Kong(LibraryTile.S5, RelativePlayer.RIGHT, false),
            McrHandAnalyzer.libraryMeld(Meld(Meld.Type.OPEN_QUAD, kong, 2, kong[0]), 1))
        assertEquals(LibraryMeld.Kong(LibraryTile.S5, null, false),
            McrHandAnalyzer.libraryMeld(Meld(Meld.Type.CONCEALED_QUAD, kong, 1, Tile.ABSENT), 1))
        assertEquals(LibraryMeld.Kong(LibraryTile.S5, RelativePlayer.LEFT, true),
            McrHandAnalyzer.libraryMeld(Meld(Meld.Type.ADDED_QUAD, kong, 0, kong[0]), 1))
        assertThrows(IllegalArgumentException::class.java) {
            McrHandAnalyzer.libraryMeld(Meld(Meld.Type.SEQUENCE, chow, 0, Tile.id(0, 0, false)), 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            McrHandAnalyzer.libraryMeld(Meld(Meld.Type.OPEN_QUAD, pung, 0, pung[0]), 1)
        }
    }

    @Test fun mapsWinningFactsAndKeepsFlowerReplacementDistinctFromKongs() {
        val winds = listOf(Tile.EAST to Wind.EAST, Tile.SOUTH to Wind.SOUTH, Tile.WEST to Wind.WEST, Tile.NORTH to Wind.NORTH)
        for ((index, pair) in winds.withIndex()) {
            val round = winds[(index + 1) % 4]
            val facts = McrWinContext(McrWinContext.Method.SELF_DRAW, pair.first, round.first,
                true, McrWinContext.KongWin.REPLACEMENT, false, 3)
            val mapped = McrHandAnalyzer.libraryContext(facts)
            assertEquals(pair.second, mapped.seatWind)
            assertEquals(round.second, mapped.prevalentWind)
            assertEquals(WinMethod.SELF_DRAW, mapped.method)
            assertTrue(mapped.wallLast)
            assertFalse(mapped.lastTile)
            assertTrue(mapped.kongInvolved)
            assertEquals(3, mapped.flowerCount)
        }
        val robbed = McrHandAnalyzer.libraryContext(McrWinContext(McrWinContext.Method.DISCARD,
            Tile.EAST, Tile.SOUTH, false, McrWinContext.KongWin.ROBBED, true, 0))
        assertEquals(WinMethod.DISCARD, robbed.method)
        assertTrue(robbed.kongInvolved)
        assertTrue(robbed.lastTile)
        assertFalse(robbed.wallLast)
        assertFalse(McrHandAnalyzer.libraryContext(context(McrWinContext.Method.SELF_DRAW, 8)).kongInvolved)
        assertThrows(IllegalArgumentException::class.java) {
            McrWinContext(McrWinContext.Method.DISCARD, Tile.EAST, Tile.EAST, false,
                McrWinContext.KongWin.REPLACEMENT, false, 0)
        }
    }

    @Test fun delegatesShantenWaitsAndBasicScoring() {
        val orphans = TestHands.tiles("19m19p19s1234567z")
        assertEquals(0, McrHandAnalyzer.shanten(orphans, emptyList(), 0))
        assertEquals(orphans.map(Tile::kind).toSet(), McrHandAnalyzer.waits(orphans, emptyList(), 0))
        val score = requireNotNull(McrHandAnalyzer.score(orphans, emptyList(), 0, Tile.id(Tile.EAST, 1, false), context()))
        assertEquals(88, score.totalFan())
        assertTrue(score.meetsMinimum())
        assertEquals(listOf(McrHandScore.Fan("THIRTEEN_ORPHANS", 1, 88, false)), score.fans())
        assertNull(McrHandAnalyzer.score(TestHands.tiles("12323m456s789p11z"), emptyList(), 0,
            Tile.id(4, 0, false), context()))
    }

    @Test fun collectedFlowersReachWinContextWithoutQualifyingALowFanHand() {
        val player = PlayerState()
        val wall = McrWall(McrGameTest.physical(Tile.mcrSet()), McrGameTest.OPENING)
        wall.replace(player)
        assertEquals(8, player.flowers.size)
        val facts = context(flowers = player.flowers.size)
        assertEquals(player.flowers.size, McrHandAnalyzer.libraryContext(facts).flowerCount)
        val score = requireNotNull(McrHandAnalyzer.score(TestHands.tiles("445566m2277779s"), emptyList(), 0,
            Tile.id(Tile.parseKind("8s"), 0, false), facts))
        assertEquals(15, score.totalFan())
        assertEquals(7, score.nonFlowerFan())
        assertFalse(score.meetsMinimum())
        assertEquals(McrHandScore.Fan("FLOWER_TILES", 8, 8, false), score.fans().single { it.id() == "FLOWER_TILES" })
    }

    @Test fun preservesAwardedMixedKongSubtotals() {
        val concealed = TestHands.tiles("1111m")
        val exposed = TestHands.tiles("2222s")
        val melds = listOf(Meld(Meld.Type.CONCEALED_QUAD, concealed, 0, Tile.ABSENT),
            Meld(Meld.Type.OPEN_QUAD, exposed, 3, exposed[0]))
        val hand = TestHands.tiles("345p11z67s")
        assertEquals(0, McrHandAnalyzer.shanten(hand, melds, 0))
        assertEquals(setOf(Tile.parseKind("5s"), Tile.parseKind("8s")), McrHandAnalyzer.waits(hand, melds, 0))
        val score = requireNotNull(McrHandAnalyzer.score(hand, melds, 0,
            Tile.id(Tile.parseKind("8s"), 0, false), context(McrWinContext.Method.SELF_DRAW)))
        assertEquals(8, score.totalFan())
        assertTrue(score.meetsMinimum())
        assertEquals(McrHandScore.Fan("TWO_MELDED_KONGS", 1, 6, true), score.fans().single { it.mixedKongPair() })
    }

    @Test fun rejectsFlowersAndDuplicatePhysicalIdentitiesBeforeOrdinaryAnalysis() {
        val hand = TestHands.tiles("19m19p19s1234567z")
        assertThrows(IllegalArgumentException::class.java) {
            McrHandAnalyzer.score(hand, emptyList(), 0, hand[0], context())
        }
        assertThrows(IllegalArgumentException::class.java) {
            McrHandAnalyzer.shanten(hand + Tile.id(0, 1, false), emptyList(), 0)
        }
        val flowerHand = hand.dropLast(1) + FlowerTile.SPRING.id()
        assertThrows(IllegalArgumentException::class.java) { McrHandAnalyzer.shanten(flowerHand, emptyList(), 0) }
        assertThrows(IllegalArgumentException::class.java) { McrHandAnalyzer.waits(flowerHand, emptyList(), 0) }
        assertThrows(IllegalArgumentException::class.java) { RiichiHandAnalyzer.shantenNumber(flowerHand, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            McrHandAnalyzer.score(hand, emptyList(), 0, FlowerTile.SPRING.id(), context())
        }
        assertThrows(IllegalArgumentException::class.java) {
            McrHandAnalyzer.libraryMeld(Meld(Meld.Type.SEQUENCE, listOf(FlowerTile.SPRING.id(),
                FlowerTile.SUMMER.id(), FlowerTile.AUTUMN.id()), 3, FlowerTile.SPRING.id()), 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            McrHandAnalyzer.shanten(hand.dropLast(2) + listOf(Tile.id(4, 0, false), Tile.id(4, 0, true)), emptyList(), 0)
        }
    }
}
