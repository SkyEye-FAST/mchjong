package top.skyeyefast.mchjong.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import top.skyeyefast.mchjong.engine.McrAction.Type.*

class McrBotRoutesTest {
    private fun assess(text: String, melds: List<Meld> = emptyList(), extra: List<Int> = emptyList()): McrBotRoutes.Assessment {
        val hand = TestHands.tiles(text)
        val known = (hand + melds.flatMap { it.tiles() } + extra).toSet()
        val progress = McrHandAnalyzer.analyze(hand, melds, 0, extra)
        return McrBotRoutes(0, Tile.EAST, Tile.EAST).assess(hand, melds, known, progress)
    }

    @Test fun retainsAStraightInsteadOfRushingIntoALowFanWait() {
        val game = McrGameTest.fixed(3, McrGameTest.Fixture().hand(0, "1234567m555p11z89s").build())
        val view = game.view(0)
        val selected = view.actions()[McrBot.choose(view)]
        val lowDiscard = game.hand(0).first { Tile.kind(it) == Tile.parseKind("7m") }
        val fast = McrHandAnalyzer.analyze(game.hand(0) - lowDiscard, emptyList(), 0, listOf(lowDiscard))
        assertEquals(0, fast.shanten)
        val lowRoute = assess("123456m555p11z89s", extra = listOf(lowDiscard))
        assertEquals(0, lowRoute.qualifyingCopies)
        assertFalse(lowRoute.routes.any { it.distance == 0 }, "A retained head must not invent a wait bonus")
        assertTrue(Tile.kind(selected.tiles().single()) in listOf(Tile.parseKind("8s"), Tile.parseKind("9s")))
        val retained = McrHandAnalyzer.analyze(game.hand(0) - selected.tiles().single(), emptyList(), 0, selected.tiles())
        assertEquals(1, retained.shanten)
    }

    @Test fun protectsSpecialFormsBeforeTheyAreComplete() {
        for ((text, junk) in listOf("1122m3344p5566s7z8m" to "8m",
            "19m19p19s1234567z5m" to "5m", "147m258p369s1234z5m" to "5m",
            "147m25p3s1234567z5m" to "5m", "147m258p369s11z45s8p" to "8p")) {
            val game = McrGameTest.fixed(4, McrGameTest.Fixture().hand(0, text).build())
            val action = game.actions(0)[McrBot.choose(game.view(0))]
            assertEquals(DISCARD, action.type())
            assertEquals(Tile.parseKind(junk), Tile.kind(action.tiles().single()), text)
        }
        val distant = assess("1122m33p44s157z68p")
        assertTrue(distant.routes.any { it.name == "SEVEN_PAIRS" && it.distance > 1 && it.feasibility > 0.0 })
    }

    @Test fun refusesAPungThatBreaksSevenPairs() {
        val game = McrGameTest.fixed(3, McrGameTest.Fixture().hand(0, "279m147p258s2345z2m")
            .hand(1, "1122m3344p5566s7z").build())
        McrGameTest.discardKind(game, 0, "2m")
        assertTrue(McrGameTest.has(game, 1, PUNG))
        assertEquals(PASS, game.actions(1)[McrBot.choose(game.view(1))].type())
        val chow = TestHands.meld(Meld.Type.SEQUENCE, "123m")
        val opened = assess("11223344p55z", listOf(chow))
        assertFalse(opened.routes.any { it.name == "SEVEN_PAIRS" || it.name == "ALL_PUNGS" })
    }

    @Test fun publicExhaustionRemovesIndispensableRoutesAndScarcityDiscountsThem() {
        val hand = "1234567m234p11z9s"
        val kind = Tile.parseKind("9m")
        val live = assess(hand).routes.first { it.name == "PURE_STRAIGHT_0" }
        val scarce = assess(hand, extra = (0..2).map { Tile.id(kind, it, false) })
            .routes.first { it.name == "PURE_STRAIGHT_0" }
        assertTrue(scarce.feasibility < live.feasibility)
        val dead = assess(hand, extra = (0..3).map { Tile.id(kind, it, false) })
        assertFalse(dead.routes.any { it.name == "PURE_STRAIGHT_0" })
        val orphan = Tile.parseKind("9s")
        assertFalse(assess("19m19p1s1234567z2m", extra = (0..3).map { Tile.id(orphan, it, false) })
            .routes.any { it.name == "THIRTEEN_ORPHANS" })
    }
}
