package top.skyeyefast.mchjong.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.skyeyefast.mchjong.engine.TaiwanFixtures.act
import top.skyeyefast.mchjong.engine.TaiwanFixtures.fixture
import top.skyeyefast.mchjong.engine.TaiwanFixtures.wait

class TaiwanHintsTest {
    private val sequences = (0..5).toList() + (9..11) + (18..20)
    private val twoWaits = sequences + listOf(Tile.EAST, Tile.EAST, Tile.RED, Tile.RED)
    private fun physical(kinds: List<Int>): List<Int> {
        val counts = IntArray(34)
        return kinds.map { it * 4 + counts[it]++ }
    }
    private fun view(kinds: List<Int>, rules: TaiwanRules = TaiwanFixtures.pocket,
                     river: List<Int> = emptyList(), melds: List<Meld> = emptyList(),
                     flowers: List<FlowerTile> = emptyList(), passed: Boolean = false,
                     ready: TaiwanWinContext.Ready = TaiwanWinContext.Ready.NONE,
                     focus: TaiwanView.Focus? = null, last: Boolean = false): TaiwanView {
        val base = fixture(rules = rules).view(0)
        val hand = physical(kinds)
        val seats = base.seats().toMutableList()
        seats[0] = TaiwanView.Seat(hand.size, hand, if (hand.size + 3 * melds.size == 17) hand.last() else Tile.ABSENT,
            melds, flowers, emptyList(), ready)
        val other = seats[1]
        seats[1] = TaiwanView.Seat(other.concealedCount(), emptyList(), Tile.ABSENT, emptyList(), emptyList(), river, other.ready())
        return TaiwanView(1, 1, base.rules(), base.opening(), Tile.EAST, 0,
            if (focus == null) TaiwanGame.Phase.TURN else TaiwanGame.Phase.REACTION, if (focus == null) 0 else focus.seat(), 0,
            if (last) 0 else base.drawable(), base.reserve(),
            if (last) List(base.wall().size) { if (it < base.reserve()) Tile.HIDDEN else Tile.ABSENT } else base.wall(),
            seats, focus, if (focus == null && hand.size + 3 * melds.size == 17) hand.map {
                TaiwanGameState.Action(TaiwanAction.Type.DISCARD, listOf(it))
            } else emptyList(), false, passed, null)
    }
    private fun hint(view: TaiwanView, discard: Int = Tile.ABSENT) = TaiwanHints().preview(view, discard)!!

    @Test fun shantenEffectiveTilesAndLegalDiscardPreview() {
        val developing = view((0..13).toList() + listOf(30, 33))
        val progress = hint(developing)
        assertEquals(1, progress.shanten)
        assertTrue(progress.tiles.isNotEmpty())
        assertTrue(progress.tiles.all { it.remaining in 1..4 && it.discardTai == -1 && it.drawTai == -1 })
        val drawn = view(wait + 32)
        val after = hint(drawn, 32 * 4)
        assertTrue(after.discard)
        assertEquals(0, after.shanten)
        assertEquals(setOf(33), after.tiles.map { it.kind }.toSet())
        assertEquals(hint(view(wait)).tiles, after.tiles)
        assertFalse(hint(drawn, 135).discard)
        assertEquals(after.tiles, hint(drawn).tiles)
    }

    @Test fun eachWaitUsesItsOwnRonAndDrawScoreIncludingWindsHonorsAndConcealment() {
        val preview = hint(view(twoWaits))
        assertEquals(0, preview.shanten)
        assertEquals(setOf(Tile.EAST, Tile.RED), preview.tiles.map { it.kind }.toSet())
        val east = preview.tiles.single { it.kind == Tile.EAST }
        val red = preview.tiles.single { it.kind == Tile.RED }
        assertEquals(red.discardTai + 1, east.discardTai) // seat + round wind versus one dragon
        assertTrue(preview.tiles.all { it.drawTai > it.discardTai })
        val meld = Meld(Meld.Type.SEQUENCE, listOf(0, 4, 8), 3, 0)
        val opened = hint(view(twoWaits.drop(3), melds = listOf(meld)))
        assertTrue(opened.tiles.all { tile -> tile.discardTai < preview.tiles.single { it.kind == tile.kind }.discardTai })
        assertTrue(opened.tiles.all { tile -> tile.drawTai < preview.tiles.single { it.kind == tile.kind }.drawTai })
        val base = view(twoWaits)
        val southRound = TaiwanView(base.revision(), base.decision(), base.rules(), base.opening(), Tile.SOUTH, 0,
            base.phase(), base.turn(), 0, base.drawable(), base.reserve(), base.wall(), base.seats(), null, emptyList(), false, false, null)
        assertEquals(east.discardTai - 1, hint(southRound).tiles.single { it.kind == Tile.EAST }.discardTai)
    }

    @Test fun presetsCurrentRulesCapsAndKnownFlowersReachTheRealScorer() {
        val honors = listOf(27, 28, 29, 30, 31).flatMap { List(3) { _ -> it } } + 32
        val pocket = hint(view(honors))
        val southern = hint(view(honors, TaiwanPreset.SOUTHERN_COMMON.rules()))
        assertNotEquals(pocket.tiles, southern.tiles)
        val rules = TaiwanFixtures.pocket
        val capped = TaiwanRules(rules.name, rules.values, rules.exclusions, rules.sources, rules.flowers,
            rules.flowerSets, rules.pinfu, rules.replacements, 2, rules.reserve, rules.payment)
        assertTrue(hint(view(honors, capped)).tiles.all { it.discardTai == 2 && it.drawTai == 2 })
        val without = hint(view(twoWaits))
        val with = hint(view(twoWaits, flowers = listOf(FlowerTile.SPRING)))
        assertTrue(with.tiles.all { it.discardTai == without.tiles.single { other -> other.kind == it.kind }.discardTai + 1 })
    }

    @Test fun passingKeepsStructuralWaitsAndReadyKeepsAnalysis() {
        val game = fixture(mapOf(0 to listOf(33), 1 to wait), draws = listOf(32))
        act(game, 0, TaiwanAction.Type.DISCARD, 33)
        val before = hint(game.view(1))
        act(game, 1, TaiwanAction.Type.PASS)
        TaiwanFixtures.pass(game)
        val passed = hint(game.view(1))
        assertTrue(passed.passedWin)
        assertEquals(before.tiles.map { it.copy(currentTai = -1) }, passed.tiles)
        assertEquals(0, passed.shanten)
        val ready = fixture(mapOf(0 to wait + 32))
        act(ready, 0, TaiwanAction.Type.READY_DISCARD, 32)
        assertNotEquals(TaiwanWinContext.Ready.NONE, ready.view(0).seats()[0].ready())
        assertEquals(0, hint(ready.view(0)).shanten)
        assertTrue(hint(ready.view(0)).tiles.single().discardTai > hint(view(wait)).tiles.single().discardTai)
    }

    @Test fun publicCopiesDeduplicateRiverMeldFocusAndKeepExhaustedWaits() {
        val copies = listOf(132, 133, 134, 135)
        val exhausted = hint(view(wait, river = copies, focus = TaiwanView.Focus(1, 135, false)))
        assertEquals(0, exhausted.tiles.single().remaining)
        val base = view(wait, river = copies.take(3), focus = TaiwanView.Focus(1, 134, false))
        val seats = base.seats().toMutableList()
        val other = seats[1]
        seats[1] = TaiwanView.Seat(other.concealedCount(), emptyList(), Tile.ABSENT,
            listOf(Meld(Meld.Type.TRIPLET, copies.take(3), 2, 132)), emptyList(), copies.take(3), other.ready())
        val aliases = TaiwanView(1, 1, base.rules(), base.opening(), base.roundWind(), 0, base.phase(), base.turn(), 0,
            base.drawable(), base.reserve(), base.wall(), seats, base.focus(), emptyList(), false, false, null)
        assertEquals(1, hint(aliases).tiles.single().remaining)
        seats[1] = TaiwanView.Seat(other.concealedCount(), emptyList(), Tile.ABSENT,
            listOf(Meld(Meld.Type.CONCEALED_QUAD, List(4) { Tile.HIDDEN }, 1, Tile.ABSENT)), emptyList(), emptyList(), other.ready())
        val hidden = TaiwanView(1, 1, base.rules(), base.opening(), base.roundWind(), 0, TaiwanGame.Phase.TURN, 0, 0,
            base.drawable(), base.reserve(), base.wall(), seats, null, emptyList(), false, false, null)
        assertEquals(3, hint(hidden).tiles.single().remaining)
    }

    @Test fun onlyCurrentPublicReactionAddsLastDiscardOrRobbingTai() {
        val ordinary = hint(view(wait)).tiles.single()
        val future = hint(view(wait + 32, last = true), 128).tiles.single()
        assertEquals(ordinary.discardTai, future.discardTai)
        assertEquals(ordinary.drawTai, future.drawTai)
        assertEquals(-1, future.currentTai)
        val last = hint(view(wait, focus = TaiwanView.Focus(1, 133, false), last = true)).tiles.single()
        assertEquals(ordinary.discardTai, last.discardTai)
        assertEquals(ordinary.drawTai, last.drawTai)
        assertEquals(ordinary.discardTai + TaiwanFixtures.pocket.values.getValue(TaiwanRules.Pattern.LAST_DISCARD), last.currentTai)
        val robbed = hint(view(wait, focus = TaiwanView.Focus(1, 133, true), last = true)).tiles.single()
        assertEquals(ordinary.discardTai + TaiwanFixtures.pocket.values.getValue(TaiwanRules.Pattern.ROBBING_KONG), robbed.currentTai)
    }

    @Test fun opponentsAndFutureWallDoNotChangeHintsAndSpectatorsGetNone() {
        val a = fixture(mapOf(0 to wait + 32, 1 to listOf(18, 19, 20)), draws = listOf(21, 22))
        val b = fixture(mapOf(0 to wait + 32, 1 to listOf(23, 24, 25)), draws = listOf(26, 27))
        assertNotEquals(a.save(), b.save())
        val discard = a.view(0).seats()[0].concealed().single { Tile.kind(it) == 32 }
        assertEquals(hint(a.view(0), discard), hint(b.view(0), discard))
        assertEquals(hint(a.view(0), discard), hint(TaiwanCodec.decodeView(TaiwanCodec.encodeView(a.view(0))), discard))
        assertNull(TaiwanHints().preview(a.view(-1), Tile.ABSENT))
    }

    @Test fun activeSessionRestoresHintsAndWorldPolicyDisablesThem() {
        val actor = java.util.UUID(0, 1)
        var room = TaiwanSession(java.util.UUID.randomUUID(), 4)
        room.join(actor, "Human", 0)
        assertTrue(room.configureConvenienceHints(actor, room.decision(), true))
        room.configureEquipment(false, TaiwanWall.expected(room.rules()))
        fun action(type: RoomAction.Type) {
            val index = room.roomActions(actor).indexOfFirst { it.type() == type }
            assertTrue(index >= 0)
            assertTrue(room.actRoom(actor, room.tableId(), room.incarnation(), room.decision(), index))
        }
        action(RoomAction.Type.FILL_BOTS)
        action(RoomAction.Type.BEGIN_SEATING)
        room.synchronizeSeats(mapOf(actor to room.seatOf(actor)))
        action(RoomAction.Type.READY)
        assertEquals(TableSession.Lifecycle.PLAYING, room.lifecycle())
        assertTrue(room.roomView(actor).convenienceHints())
        val before = room.view(actor).game()
        room = TableSessionCodec.restore(TableSessionCodec.save(room)) as TaiwanSession
        assertTrue(room.convenienceHints())
        assertNull(TaiwanHints().preview(room.view(actor).game(), Tile.ABSENT)) // restored mounts grant spectator only
        room.synchronizeSeats(mapOf(actor to room.seatOf(actor)))
        assertEquals(hint(before), hint(room.view(actor).game()))
        assertFalse(room.configureConvenienceHints(actor, room.decision(), false))
        room.configureWorld(WorldPolicy(false, false, true, 5000, true, true, true, true, null))
        assertFalse(room.convenienceHints())
        assertFalse(room.roomView(actor).allowConvenienceHints())
        assertFalse(room.roomView(actor).convenienceHints())
        assertFalse((TableSessionCodec.restore(TableSessionCodec.save(room)) as TaiwanSession).convenienceHints())
    }
}
