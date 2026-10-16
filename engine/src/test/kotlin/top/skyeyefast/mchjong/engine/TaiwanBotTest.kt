package top.skyeyefast.mchjong.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import top.skyeyefast.mchjong.engine.TaiwanAction.Type
import top.skyeyefast.mchjong.engine.TaiwanFixtures.act
import top.skyeyefast.mchjong.engine.TaiwanFixtures.fixture
import top.skyeyefast.mchjong.engine.TaiwanFixtures.pass
import top.skyeyefast.mchjong.engine.TaiwanFixtures.wait

class TaiwanBotTest {
    private fun choice(view: TaiwanView): TaiwanGameState.Action {
        val index = TaiwanBot.choose(view)
        assertTrue(index in view.actions().indices)
        assertEquals(index, TaiwanBot.choose(TaiwanCodec.decodeView(TaiwanCodec.encodeView(view))))
        return view.actions()[index]
    }
    private val sequences = (0..5).toList() + (9..11) + (18..20) + (24..26) + 33

    @Test fun issuedWinsAlwaysTakePriorityInBothPresets() {
        for (preset in TaiwanPreset.entries) {
            val game = fixture(mapOf(0 to wait + 33), rules = preset.rules())
            assertEquals(Type.WIN, choice(game.view(0)).type())
            val claim = fixture(mapOf(0 to listOf(33), 1 to wait), rules = preset.rules())
            act(claim, 0, Type.DISCARD, 33)
            assertEquals(Type.WIN, choice(claim.view(1)).type())
        }
    }

    @Test fun discardsPrioritizeExactShantenThenPublicEffectiveCopies() {
        for (preset in TaiwanPreset.entries) {
            val game = fixture(mapOf(0 to (0..14).toList() + listOf(30, 33)), rules = preset.rules())
            val view = game.view(0)
            val own = view.seats()[0]
            val analyses = TaiwanHandAnalyzer.discards(own.concealed().dropLast(1), own.melds(), 0, own.concealed().last(), own.concealed())
            val fastest = analyses.minOf { it.analysis.shanten }
            val widest = analyses.filter { it.analysis.shanten == fastest }.maxOf { it.analysis.effectiveTiles.sumOf { tile -> tile.remaining } }
            val selected = choice(view)
            val result = analyses.single { it.kind == Tile.kind(selected.tiles().single()) }.analysis
            assertEquals(fastest, result.shanten)
            assertEquals(widest, result.effectiveTiles.sumOf { it.remaining })
            assertEquals(Type.READY_DISCARD, selected.type())
            assertEquals(selected.tiles().single(), view.actions().filter { it.type() == selected.type() && Tile.kind(it.tiles().single()) == Tile.kind(selected.tiles().single()) }.minOf { it.tiles().single() })
        }
    }

    @Test fun callsAndOpenKongCannotBuyAnEqualWaitByLosingConcealment() {
        for (preset in TaiwanPreset.entries) {
            val chow = fixture(mapOf(0 to listOf(1), 1 to sequences), rules = preset.rules())
            act(chow, 0, Type.DISCARD, 1)
            assertTrue(chow.view(1).actions().any { it.type() == Type.CHOW })
            assertEquals(Type.PASS, choice(chow.view(1)).type())
            val pungs = listOf(0, 0, 0) + (3..5) + (9..11) + (18..20) + (24..26) + 33
            val pong = fixture(mapOf(0 to listOf(0), 1 to pungs), rules = preset.rules())
            act(pong, 0, Type.DISCARD, 0)
            assertTrue(pong.view(1).actions().any { it.type() == Type.PONG })
            assertTrue(pong.view(1).actions().any { it.type() == Type.OPEN_KONG })
            assertEquals(Type.PASS, choice(pong.view(1)).type())
        }
    }

    @Test fun concealedKongMustImproveGuaranteedReplacementContinuation() {
        val hand = listOf(0, 0, 0, 0) + (3..5) + (9..11) + (18..20) + (24..26) + 33
        for (preset in TaiwanPreset.entries) {
            val game = fixture(mapOf(0 to hand), rules = preset.rules())
            assertTrue(game.view(0).actions().any { it.type() == Type.CONCEALED_KONG })
            assertTrue(choice(game.view(0)).type() in listOf(Type.DISCARD, Type.READY_DISCARD))
        }
    }

    @Test fun equalLiveWaitsUseCurrentScorerValuesRatherThanPresetNames() {
        for (preset in TaiwanPreset.entries) {
            val base = preset.rules()
            fun selected(white: Int, green: Int): Int {
                val values = base.values + mapOf(TaiwanRules.Pattern.WHITE_DRAGON to white, TaiwanRules.Pattern.GREEN_DRAGON to green)
                val rules = TaiwanRules(base.name, values, base.exclusions, base.sources, base.flowers, base.flowerSets,
                    base.pinfu, base.replacements, base.taiLimit, base.reserve, base.payment)
                val hand = (0..5).toList() + (9..11) + (18..20) + listOf(31, 31, 31, 32, 32)
                val original = fixture(mapOf(0 to hand), rules = rules).view(0)
                val own = original.seats()[0]
                val seats = original.seats().toMutableList()
                val other = seats[1]
                val known = listOf(31 * 4 + 3, 32 * 4 + 2)
                seats[1] = TaiwanView.Seat(other.concealedCount(), other.concealed(), other.drawn(), other.melds(), other.flowers(), known, other.ready())
                val actions = original.actions().filter { it.type() == Type.DISCARD && Tile.kind(it.tiles().single()) in listOf(31, 32) }
                val view = TaiwanView(original.revision(), original.decision(), original.rules(), original.opening(), original.roundWind(),
                    original.continuation(), original.phase(), original.turn(), original.recipient(), original.drawable(), original.reserve(),
                    original.wall(), seats, original.focus(), actions, original.responded(), original.passedWin(), original.result(), original.handling())
                // Both alternatives have one live copy of the same green-dragon wait. Which triplet scores differs.
                for (kind in listOf(31, 32)) {
                    val tile = own.concealed().first { Tile.kind(it) == kind }
                    val analysis = TaiwanHandAnalyzer.analyze(own.concealed() - tile, own.melds(), 0, own.concealed() + known)
                    assertEquals(0, analysis.shanten)
                    assertEquals(1, analysis.effectiveTiles.sumOf { it.remaining })
                }
                return Tile.kind(choice(view).tiles().single())
            }
            assertEquals(32, selected(10, 0))
            assertEquals(31, selected(0, 10))
        }
    }

    @Test fun addedKongCannotSpendAReadyHandForAnUnknownReplacement() {
        val hand = listOf(0, 0) + (3..5) + (9..11) + (18..20) + (24..26) + listOf(30, 33)
        val game = fixture(mapOf(0 to listOf(0), 1 to hand), draws = listOf(32, 32, 32, 0))
        act(game, 0, Type.DISCARD, 0)
        act(game, 1, Type.PONG)
        pass(game)
        act(game, 1, Type.DISCARD, 30)
        pass(game)
        repeat(3) { act(game, game.turn, Type.DISCARD, 32); pass(game) }
        assertTrue(game.view(1).actions().any { it.type() == Type.ADDED_KONG })
        assertTrue(choice(game.view(1)).type() in listOf(Type.DISCARD, Type.READY_DISCARD))
    }

    @Test @org.junit.jupiter.api.Timeout(5) fun attainableValueCanOutweighMoreEffectiveCopiesButExhaustedRoutesCannot() {
        val base = TaiwanPreset.POCKET_COMMON.rules()
        val values = base.values + (TaiwanRules.Pattern.WHITE_DRAGON to 100)
        val rules = TaiwanRules(base.name, values, base.exclusions, base.sources, base.flowers, base.flowerSets,
            base.pinfu, base.replacements, null, base.reserve, base.payment)
        val kinds = (0..5).toList() + (9..11) + (18..20) + listOf(24, 25, 31, 32, 33)
        fun position(publicWhite: Int): TaiwanView {
            val original = fixture(mapOf(0 to kinds), rules = rules).view(0)
            val seats = original.seats().toMutableList()
            val other = seats[1]
            seats[1] = TaiwanView.Seat(other.concealedCount(), other.concealed(), other.drawn(), other.melds(), other.flowers(),
                (1..publicWhite).map { Tile.WHITE * 4 + it }, other.ready())
            return TaiwanView(original.revision(), original.decision(), original.rules(), original.opening(), original.roundWind(), original.continuation(),
                original.phase(), original.turn(), original.recipient(), original.drawable(), original.reserve(), original.wall(), seats,
                original.focus(), original.actions(), original.responded(), original.passedWin(), original.result(), original.handling())
        }
        val view = position(1)
        val own = view.seats()[0]
        val analyses = TaiwanHandAnalyzer.discards(own.concealed().dropLast(1), own.melds(), 0, own.concealed().last(), own.concealed() + view.seats()[1].river())
        val selected = Tile.kind(choice(view).tiles().single())
        assertNotEquals(31, selected, "Keep the attainable high-value dragon route")
        val kept = analyses.single { it.kind == selected }.analysis
        val widest = analyses.filter { it.analysis.shanten == kept.shanten }.maxOf { it.analysis.effectiveTiles.sumOf { tile -> tile.remaining } }
        assertTrue(kept.effectiveTiles.sumOf { it.remaining } < widest, "Value must affect a real efficiency tradeoff")
        assertEquals(31, Tile.kind(choice(position(3)).tiles().single()), "All missing dragon copies are public; release the dead route")
        // A terminal route really awards these tai under the current scorer, including its exclusions.
        val counts = IntArray(34)
        val target = ((0..5).toList() + (9..11) + (18..20) + listOf(31, 31, 31, 33)).map { it * 4 + counts[it]++ }
        val context = TaiwanWinContext(TaiwanWinContext.Method.DISCARD, Tile.EAST, Tile.EAST, 1, emptySet(),
            TaiwanWinContext.DrawOrigin.ORDINARY, false, TaiwanWinContext.Opening.NONE, TaiwanWinContext.Ready.NONE)
        assertTrue(TaiwanHandAnalyzer.score(target, emptyList(), 0, 33 * 4 + 1, context, rules)!!.tai >= 100)
    }

    @Test fun equallyLiveWaitsPreferMoreKindsBeforeTai() {
        val original = fixture(mapOf(0 to wait + 33)).view(0)
        val seats = original.seats().toMutableList()
        val other = seats[1]
        seats[1] = TaiwanView.Seat(other.concealedCount(), other.concealed(), other.drawn(), other.melds(), other.flowers(),
            listOf(1, 2, 13, 14), other.ready())
        val actions = original.actions().filter { it.type() == Type.DISCARD && Tile.kind(it.tiles().single()) in listOf(0, 33) }
        val view = TaiwanView(original.revision(), original.decision(), original.rules(), original.opening(), original.roundWind(),
            original.continuation(), original.phase(), original.turn(), original.recipient(), original.drawable(), original.reserve(),
            original.wall(), seats, original.focus(), actions, original.responded(), original.passedWin(), original.result(), original.handling())
        assertEquals(0, Tile.kind(choice(view).tiles().single()))
    }

    @Test fun aClearlyAdvancingCallAndReplacementCanBeAccepted() {
        val hand = listOf(0, 0, 3, 3, 3, 9, 9, 9, 18, 18, 18, 24, 24, 31, 32, 33)
        val pong = fixture(mapOf(0 to listOf(0), 1 to hand))
        act(pong, 0, Type.DISCARD, 0)
        assertEquals(Type.PONG, choice(pong.view(1)).type())
        val kong = fixture(mapOf(0 to listOf(31, 31, 31, 31, 0, 1, 3, 4, 6, 7, 9, 10, 12, 13, 15, 16, 18)))
        assertEquals(Type.CONCEALED_KONG, choice(kong.view(0)).type())
    }

    @Test fun decisionsIgnoreOpponentHandsFutureWallAndPrivateSave() {
        val hand = (0..14).toList() + listOf(30, 33)
        val a = fixture(mapOf(0 to hand, 1 to listOf(18, 19, 20)), draws = listOf(21, 22))
        val b = fixture(mapOf(0 to hand, 1 to listOf(23, 24, 25)), draws = listOf(26, 27))
        assertNotEquals(a.save(), b.save())
        assertEquals(a.view(0), b.view(0))
        assertEquals(choice(a.view(0)), choice(b.view(0)))
        val before = TaiwanCodec.encodeView(a.view(-1))
        choice(a.view(0))
        assertEquals(before, TaiwanCodec.encodeView(a.view(-1)))
        assertEquals(-1, TaiwanBot.choose(a.view(-1)))
        assertTrue(a.view(0).seats().drop(1).all { it.concealed().isEmpty() })
        assertTrue(a.view(0).wall().all { it == Tile.HIDDEN || it == Tile.ABSENT })
    }

    private val human = UUID(0, 1)
    private val roster = List(4) { seat -> if (seat == 0) TableParticipant(human, "Human") else
        TableParticipant(UUID(0, seat + 1L), "Bot $seat", true, false, BotDifficulty.EASY, null, true) }
    private fun mount(room: TaiwanSession) = room.synchronizeSeats(mapOf(human to room.seatOf(human)))
    private fun room(game: TaiwanGame): TaiwanSession {
        val start = TaiwanSession.start(UUID.randomUUID(), roster, 4, TaiwanWall.expected(game.rules), game.rules)
        val saved = start.save()
        return TaiwanSession.restore(TaiwanSession.State(saved.format(), saved.room(), saved.rules(), saved.stock(), saved.control(),
            saved.clocks(), 0, 0, saved.futureSeed(), emptyList(), game.save(),null,null,emptyList(),false)).also(::mount)
    }
    private fun restore(room: TaiwanSession): TaiwanSession = (TableSessionCodec.restore(TableSessionCodec.save(room)) as TaiwanSession).also {
        assertEquals(room.scores(), it.scores())
        assertTrue(it.paused() || it.lifecycle() == TableSession.Lifecycle.FINISHED)
        mount(it)
    }

    @Test fun botWinAndNextHandConfirmationSurviveRestore() {
        val game = fixture(mapOf(0 to listOf(33), 1 to wait))
        act(game, 0, Type.DISCARD, 33)
        var room = room(game)
        val clocks = room.save().clocks()
        room = restore(room)
        repeat(12) { room.tick() }
        assertEquals(TaiwanGame.Phase.FINISHED, room.game().phase)
        assertEquals(1, room.game().settlement!!.winner)
        assertEquals(14, room.save().confirmed())
        assertEquals(clocks, room.save().clocks())
        room = restore(room)
        val token = room.game().decision
        assertTrue(room.confirmNextHand(human, room.tableId(), room.incarnation(), token))
        assertEquals(2, room.view(human).handNumber())
        assertEquals(0, room.save().confirmed())
    }

    private fun roomAction(room: TaiwanSession, type: RoomAction.Type, arguments: List<Int> = emptyList()) {
        val index = room.roomActions(human).indexOfFirst { it.type() == type && it.arguments() == arguments }
        assertTrue(index >= 0, "Missing $type $arguments")
        assertTrue(room.actRoom(human, room.tableId(), room.incarnation(), room.decision(), index))
    }
    @Test fun unsupportedBotDifficultyIsRejectedAtStartAndRestore() {
        val room = TaiwanSession(UUID.randomUUID(), 4)
        assertTrue(room.join(human, "Human", 0))
        assertTrue(room.configureEquipment(false, TaiwanWall.expected(room.rules())))
        roomAction(room, RoomAction.Type.SET_BOT, listOf(1, 0))
        assertEquals(BotDifficulty.EASY, room.participants()[1].difficulty())
        assertTrue(room.roomActions(human).none { it.type() == RoomAction.Type.SET_BOT && it.arguments().first() == 1 })
        assertTrue(room.roomActions(human).filter { it.type() == RoomAction.Type.SET_BOT }.all { it.arguments()[1] == 0 })
        val invalidRoster = roster.toMutableList().also {
            it[1] = TableParticipant(it[1].id(), it[1].name(), true, false, BotDifficulty.HARD, null, true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaiwanSession.start(UUID.randomUUID(), invalidRoster, 4, TaiwanWall.expected(room.rules()), room.rules())
        }
        val saved = TaiwanCodec.saveSession(room)
        assertThrows(IllegalArgumentException::class.java) { TaiwanCodec.restoreSession(saved.replace("\"EASY\"", "\"HARD\"")) }
    }

    @Test fun externalAndAllBotRostersAreRejected() {
        val external = roster.toMutableList().also {
            it[1] = TableParticipant(UUID(0, 2), "External", true, false, BotDifficulty.EASY, "external", true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaiwanSession.start(UUID.randomUUID(), external, 4, TaiwanWall.expected(TaiwanFixtures.pocket), TaiwanFixtures.pocket)
        }
        val bots = roster.toMutableList().also { it[0] = TableParticipant(human, "Bot", true, false, BotDifficulty.EASY, null, true) }
        assertThrows(IllegalArgumentException::class.java) {
            TaiwanSession.start(UUID.randomUUID(), bots, 4, TaiwanWall.expected(TaiwanFixtures.pocket), TaiwanFixtures.pocket)
        }
    }

}
