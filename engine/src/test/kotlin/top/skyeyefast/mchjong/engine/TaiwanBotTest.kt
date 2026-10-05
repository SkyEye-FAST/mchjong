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
            fun selected(red: Int, green: Int): Int {
                val values = base.values + mapOf(TaiwanRules.Pattern.RED_DRAGON to red, TaiwanRules.Pattern.GREEN_DRAGON to green)
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
                    original.wall(), seats, original.focus(), actions, original.responded(), original.passedWin(), original.result())
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

    @Test fun equallyLiveWaitsPreferMoreKindsBeforeTai() {
        val original = fixture(mapOf(0 to wait + 33)).view(0)
        val seats = original.seats().toMutableList()
        val other = seats[1]
        seats[1] = TaiwanView.Seat(other.concealedCount(), other.concealed(), other.drawn(), other.melds(), other.flowers(),
            listOf(1, 2, 13, 14), other.ready())
        val actions = original.actions().filter { it.type() == Type.DISCARD && Tile.kind(it.tiles().single()) in listOf(0, 33) }
        val view = TaiwanView(original.revision(), original.decision(), original.rules(), original.opening(), original.roundWind(),
            original.continuation(), original.phase(), original.turn(), original.recipient(), original.drawable(), original.reserve(),
            original.wall(), seats, original.focus(), actions, original.responded(), original.passedWin(), original.result())
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
            saved.clocks(), 0, 0, saved.futureSeed(), emptyList(), game.save(),null,null,emptyList())).also(::mount)
    }
    private fun restore(room: TaiwanSession): TaiwanSession = (TableSessionCodec.restore(TableSessionCodec.save(room)) as TaiwanSession).also {
        assertEquals(room.scores(), it.scores())
        assertTrue(it.paused() || it.lifecycle() == TableSession.Lifecycle.FINISHED)
        mount(it)
    }

    @Test fun botRepliesAreDelayedPausedRestorableAndDoNotSpendClocks() {
        val game = fixture(mapOf(0 to listOf(33), 1 to wait))
        act(game, 0, Type.DISCARD, 33)
        var room = room(game)
        val clocks = room.save().clocks()
        repeat(6) { room.tick() }
        assertEquals(6, room.save().age())
        assertFalse(room.game().view(1).responded())
        assertEquals(clocks, room.save().clocks())
        room.unseat(human)
        repeat(20) { room.tick() }
        assertEquals(6, room.save().age())
        room = restore(room)
        repeat(5) { room.tick() }
        assertEquals(TaiwanGame.Phase.REACTION, room.game().phase)
        room.tick()
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
    @Test fun sharedRoomControlsExposeOneBotFillRemoveReadyAndPolicy() {
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
        roomAction(room, RoomAction.Type.REMOVE_BOT, listOf(1))
        roomAction(room, RoomAction.Type.FILL_BOTS)
        assertTrue(room.participants().drop(1).all { it.bot() && it.ready() && it.externalBotId() == null })
        val disabled = WorldPolicy(true, false, true, 5000, true, false, true, true, null)
        room.configureWorld(disabled)
        assertTrue(room.participants().none { it.bot() })
        assertTrue(room.roomActions(human).none { it.type() == RoomAction.Type.FILL_BOTS || it.type() == RoomAction.Type.SET_BOT })
        room.configureWorld(WorldPolicy.DEFAULT)
        roomAction(room, RoomAction.Type.FILL_BOTS)
        roomAction(room, RoomAction.Type.BEGIN_SEATING)
        mount(room)
        roomAction(room, RoomAction.Type.READY)
        assertEquals(TableSession.Lifecycle.PLAYING, room.lifecycle())
        assertTrue(room.participants().filter { it.bot() }.all { it.ready() })
    }

    @Test fun exitVoteAndPartialRepliesPreserveBotDelayAcrossRestore() {
        val second = UUID(0, 2)
        val players = roster.toMutableList().also { it[1] = TableParticipant(second, "Second human") }
        val game = fixture(mapOf(0 to listOf(33)))
        act(game, 0, Type.DISCARD, 33)
        val initial = TaiwanSession.start(UUID.randomUUID(), players, 4, TaiwanWall.expected(game.rules), game.rules).save()
        var room = TaiwanSession.restore(TaiwanSession.State(initial.format(), initial.room(), initial.rules(), initial.stock(), initial.control(),
            initial.clocks(), 0, 0, initial.futureSeed(), emptyList(), game.save(),null,null,emptyList()))
        fun mountBoth() = room.synchronizeSeats(mapOf(human to 0, second to 1))
        mountBoth()
        repeat(6) { room.tick() }
        assertTrue(room.requestExit(human))
        val saved = room.save()
        repeat(20) { room.tick() }
        assertEquals(saved.age(), room.save().age())
        assertEquals(saved.clocks(), room.save().clocks())
        room = TaiwanCodec.restoreSession(TaiwanCodec.saveSession(room))
        mountBoth()
        assertTrue(room.answerExit(second, room.save().room().exitVote().id(), false))
        repeat(5) { room.tick() }
        assertFalse(room.game().view(2).responded())
        room.tick()
        assertTrue(room.game().view(2).responded())
        assertTrue(room.game().view(3).responded())
        assertEquals(saved.clocks()[2], room.save().clocks()[2])
        assertEquals(saved.clocks()[3], room.save().clocks()[3])
        assertEquals(saved.clocks()[1].moveTicks() - 6, room.save().clocks()[1].moveTicks())
        assertFalse(room.view(second).game().responded())
        assertEquals(12, room.save().age())
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

    @Test fun oneHumanThreeBotsCompleteBothFourWindMatchesWithConservedTilesAndScores() {
        for (preset in TaiwanPreset.entries) {
            val rules = preset.rules()
            var room = TaiwanSession.start(UUID.randomUUID(), roster, 4, TaiwanWall.expected(rules), rules)
            mount(room)
            val totals = MutableList(4) { 0L }
            var hands = 0
            var ticks = 0
            var rotations = 0
            while (room.lifecycle() != TableSession.Lifecycle.FINISHED) {
                assertTrue(++ticks < 200_000, "Stuck $preset at hand $hands / rotation $rotations")
                val game = room.game()
                game.checkConservation()
                if (game.phase == TaiwanGame.Phase.FINISHED) {
                    assertTrue(++hands < 250)
                    val result = game.settlement!!
                    for (seat in 0..3) totals[seat] += result.deltas[seat]
                    if (result.nextDealer != game.opening.dealer) rotations++
                    assertEquals(totals, room.scores())
                    room = restore(room)
                    room.tick()
                    assertEquals(14, room.save().confirmed())
                    assertTrue(room.confirmNextHand(human, room.tableId(), room.incarnation(), room.game().decision))
                } else {
                    val view = room.view(human).game()
                    if (view.actions().isNotEmpty()) assertTrue(room.act(human, room.tableId(), room.incarnation(), view.decision(), TaiwanBot.choose(view)))
                    room.tick()
                }
            }
            room.game().checkConservation()
            for (seat in 0..3) totals[seat] += room.game().settlement!!.deltas[seat]
            assertEquals(15, rotations)
            assertEquals(Tile.NORTH, room.game().roundWind)
            assertEquals(3, room.game().opening.dealer)
            assertEquals(totals, room.scores())
            assertEquals(0L, totals.sum())
            room = restore(room)
            assertEquals(totals, room.scores())
            assertFalse(room.confirmNextHand(human, room.tableId(), room.incarnation(), room.game().decision))
            assertTrue(room.view(human).game().seats().drop(1).all { it.concealed().isEmpty() })
        }
    }
}
