package top.skyeyefast.mchjong.engine

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import top.skyeyefast.mchjong.engine.TaiwanAction.Type
import top.skyeyefast.mchjong.engine.TaiwanFixtures.fixture
import top.skyeyefast.mchjong.engine.TaiwanFixtures.wait
import top.skyeyefast.mchjong.engine.TaiwanReplayHand.Kind

class TaiwanReplayTest {
    private val players = (0..3).map { ReplayMatch.Participant(UUID(71,it.toLong()),"Player $it",it != 0) }
    private fun match(hand: TaiwanReplayHand) = ReplayMatch(UUID.randomUUID(),UUID(72,1),1,2,players,
        MahjongVariant.TAIWAN,false,null,null,null,TaiwanReplay(hand.recording().rules(),listOf(hand)))
    private inner class Run(var game: TaiwanGame) {
        var recorder = TaiwanReplayRecorder(1,List(4) { 0L },game)
        fun act(seat: Int, type: Type, kind: Int? = null) {
            val d = game.decisions().single { it.seat == seat }
            val selected = d.actions.indexOfFirst { it.type == type && (kind == null || it.tiles.any { t -> Tile.kind(t) == kind }) }
            assertTrue(selected >= 0,"Missing $type/$kind")
            val before = game.save()
            game.submit(seat,d.token,selected)
            recorder.accepted(before,seat,d.actions,selected,game)
        }
        fun pass() { game.decisions().toList().forEach { act(it.seat,Type.PASS) } }
        fun restore() {
            val recording = SichuanCodec.decode(ReplayCodec.encode(recorder.save()),TaiwanReplayRecorder.State::class.java,8*1024*1024)
            game = TaiwanCodec.restore(TaiwanCodec.save(game)); recorder = TaiwanReplayRecorder(recording)
            assertTrue(TaiwanReplayPlayback.samePosition(TaiwanReplayPlayback.reconstruct(recording).save(),game.save()))
        }
        fun finish(): TaiwanReplayHand {
            var moves = 0
            while (game.phase != TaiwanGame.Phase.FINISHED) {
                assertTrue(++moves < 1000)
                if (game.phase == TaiwanGame.Phase.REACTION) pass() else act(game.turn,Type.DISCARD)
            }
            val hand = recorder.finish(game)
            val replay = ReplayCodec.decode(ReplayCodec.encode(match(hand)),ReplayMatch::class.java)
            ReplayCodec.validate(replay)
            val timeline = TaiwanReplayPlayback.timeline(replay,0)
            assertTrue(TaiwanReplayPlayback.samePosition(game.save(),timeline.frames().last().state()))
            assertEquals(hand.finalScores(),timeline.frames().last().scores())
            assertEquals(hand.events(),timeline.frames().map { it.event() })
            for (frame in timeline.frames()) {
                val state = frame.state()
                val owned = state.wall().slots().filter { it >= 0 } + state.players().flatMap { p ->
                    p.hand()+p.melds().flatMap { it.tiles() }+p.flowers().map { it.id() }+p.river() }
                assertEquals(hand.recording().wall().toSet(),owned.toSet())
                assertEquals(owned.size,owned.toSet().size)
            }
            return hand
        }
    }
    @Test fun bothWallsOrdinaryPlayAndRecorderRestoreKeepExactPhysicalState() {
        for (preset in TaiwanPreset.entries) {
            val run = Run(TaiwanGame.shuffled(4,preset.rules()))
            run.act(0,Type.DISCARD); run.restore()
            run.act(run.game.decisions().first().seat,Type.PASS); run.restore() // Partial reaction.
            run.pass()
            val hand = run.finish()
            assertEquals(if (preset == TaiwanPreset.POCKET_COMMON) 144 else 136,hand.recording().wall().size)
            assertTrue(hand.events().any { it.kind() == Kind.DRAW })
            assertNull(hand.settlement().winner())
        }
    }
    @Test fun initialAndContinuousFlowersAreAutomaticFramesWithNoInventedDecision() {
        val run = Run(fixture(mapOf(0 to listOf(136),1 to listOf(137)),draws=listOf(139),tail=listOf(138,32,33,140,31)))
        run.act(0,Type.DISCARD); run.pass()
        val hand = run.finish()
        val opening = hand.events().filter { it.actionCursor() == 0 }
        assertEquals(listOf(136,137,138),opening.filter { it.kind() == Kind.FLOWER }.map { it.tile() })
        assertEquals(3,opening.count { it.kind() == Kind.REPLACEMENT })
        assertTrue(hand.events().any { it.kind() == Kind.REPLACEMENT && it.tile() == 140 })
        assertTrue(hand.decisions().all { d -> d.options().none { it.type().name.contains("FLOWER") } })
    }
    @Test fun chowPongAndReactionPriorityComeFromAcceptedEngineChoices() {
        val pong = Run(fixture(mapOf(0 to listOf(4),1 to listOf(3,5),2 to listOf(4,4))))
        pong.act(0,Type.DISCARD,4); pong.act(1,Type.CHOW); pong.restore(); pong.act(3,Type.PASS); pong.act(2,Type.PONG)
        val hand = pong.finish()
        assertTrue(hand.events().any { it.kind() == Kind.PONG })
        assertFalse(hand.events().any { it.kind() == Kind.CHOW })
        val chow = Run(fixture(mapOf(0 to listOf(4),1 to listOf(3,5))))
        chow.act(0,Type.DISCARD,4); chow.act(1,Type.CHOW); chow.pass()
        assertTrue(chow.finish().events().any { it.kind() == Kind.CHOW })
    }
    @Test fun allKongsReplacementWinAndRobbingWindowReplayExactly() {
        val concealed = Run(fixture(mapOf(0 to listOf(0,0,0,0)),tail=listOf(136,33)))
        concealed.act(0,Type.CONCEALED_KONG,0); concealed.restore()
        assertTrue(concealed.finish().events().any { it.kind() == Kind.CONCEALED_KONG })
        val caller = listOf(0,0,0,3,4,5,9,10,11,18,19,20,24,25,26,31)
        val open = Run(fixture(mapOf(0 to listOf(0),1 to caller),tail=listOf(31)))
        open.act(0,Type.DISCARD,0); open.act(1,Type.OPEN_KONG); open.pass(); open.act(1,Type.WIN)
        assertTrue(open.finish().events().any { it.kind() == Kind.OPEN_KONG })
        fun pending(): Run {
            val robber = listOf(1,2,9,10,11,12,13,14,15,16,17,18,19,20,31,31)
            val run = Run(fixture(mapOf(0 to listOf(0),1 to listOf(0,0,30),2 to robber),draws=listOf(32,32,32,0),tail=listOf(33)))
            run.act(0,Type.DISCARD,0); run.act(1,Type.PONG); run.pass(); run.act(1,Type.DISCARD,30); run.pass()
            repeat(3) { run.act(run.game.turn,Type.DISCARD,32); run.pass() }
            run.act(1,Type.ADDED_KONG); run.act(3,Type.PASS); run.restore()
            return run
        }
        val robbed = pending(); robbed.act(2,Type.WIN); robbed.pass()
        val robbery = robbed.finish()
        assertEquals(TaiwanWinContext.Method.ROBBING_KONG,robbery.finalState().outcome().method())
        assertTrue(robbery.events().any { it.kind() == Kind.KONG_OFFER })
        assertTrue(robbery.events().any { it.kind() == Kind.ROBBING_KONG })
        assertFalse(robbery.events().any { it.kind() == Kind.ADDED_KONG })
        val committed = pending(); committed.pass()
        assertTrue(committed.finish().events().any { it.kind() == Kind.ADDED_KONG })
    }
    @Test fun readyNormalWinAndCompulsoryFlowerVictoriesAreNative() {
        val ready = Run(fixture(mapOf(0 to wait+30),draws=listOf(31,31,31,32)))
        ready.act(0,Type.READY_DISCARD,30); ready.pass(); ready.restore()
        assertTrue(ready.finish().events().any { it.kind() == Kind.READY })
        val win = Run(fixture(mapOf(0 to listOf(33),1 to wait,2 to wait)))
        win.act(0,Type.DISCARD,33); win.act(2,Type.WIN); win.restore(); win.act(3,Type.PASS); win.act(1,Type.WIN)
        assertEquals(1,win.finish().settlement().winner())
        for (eight in listOf(false,true)) {
            val flowers = Run(fixture(if (eight) mapOf(1 to (136..143).toList()) else mapOf(1 to (136..142).toList(),2 to listOf(143))))
            val hand = flowers.finish()
            assertTrue(hand.decisions().isEmpty())
            assertTrue(hand.events().any { it.kind() == Kind.FLOWER_WIN && it.actionCursor() == 0 })
        }
        val steal = Run(fixture(mapOf(1 to (136..142).toList()),draws=listOf(32,143)))
        steal.act(0,Type.DISCARD); steal.pass(); steal.act(1,Type.DISCARD,32); steal.pass()
        assertEquals(TaiwanHandAnalyzer.FlowerEvent.SEVEN_ON_OPPONENT_FLOWER,steal.finish().finalState().outcome().flowerEvent())
        for (eight in listOf(false,true)) {
            val own=Run(fixture(if (eight) mapOf(1 to (136..142).toList()) else mapOf(1 to (136..141).toList(),2 to listOf(143)),
                draws=listOf(if (eight) 143 else 142),tail=(0..7).toList()))
            own.act(0,Type.DISCARD); own.pass()
            assertEquals(if (eight) TaiwanHandAnalyzer.FlowerEvent.EIGHT_AFTER_REPLACEMENT else TaiwanHandAnalyzer.FlowerEvent.SEVEN_AFTER_REPLACEMENT,
                own.finish().finalState().outcome().flowerEvent())
        }
    }
    @Test fun changedOptionsSelectionEventsWallAndSettlementAreRejected() {
        val run = Run(fixture(mapOf(0 to listOf(33),1 to wait)))
        run.act(0,Type.DISCARD,33); run.act(1,Type.WIN); run.pass()
        val replay = match(run.finish())
        fun altered(change: (com.google.gson.JsonObject) -> Unit) {
            val tree = JsonParser.parseString(ReplayCodec.encode(replay)).asJsonObject
            val hand = tree.getAsJsonObject("taiwan").getAsJsonArray("hands")[0].asJsonObject
            change(hand)
            assertThrows(IllegalArgumentException::class.java) { ReplayCodec.validate(ReplayCodec.decode(tree.toString(),ReplayMatch::class.java)) }
        }
        altered { h -> h.getAsJsonObject("recording").getAsJsonArray("decisions")[0].asJsonObject.addProperty("selected",999) }
        altered { h -> h.getAsJsonObject("recording").getAsJsonArray("decisions")[0].asJsonObject.getAsJsonArray("options").remove(0) }
        altered { h -> h.getAsJsonObject("recording").getAsJsonArray("events")[1].asJsonObject.addProperty("tile",135) }
        altered { h -> val wall=h.getAsJsonObject("recording").getAsJsonArray("wall"); val a=wall[6]; wall.set(6,wall[7]); wall.set(7,a) }
        altered { h -> h.getAsJsonObject("settlement").addProperty("nextContinuation",1) }
        altered { h -> h.getAsJsonObject("finalState").getAsJsonObject("wall").addProperty("tail",99) }
    }
    @Test fun headerKeepsAdjacentLongScoresDistinctAboveDoubleIntegerPrecision() {
        val scores=listOf(9_007_199_254_740_993L,9_007_199_254_740_992L,-9_007_199_254_740_992L,-9_007_199_254_740_993L)
        val header=ReplayMatch.Header(UUID(72,2),1,2,MahjongVariant.TAIWAN,null,null,TaiwanGameState.Rules.of(TaiwanFixtures.pocket),
            16,true,players.map { it.name },emptyList(),listOf(1,2,3,4),scores)
        assertEquals(header,ReplayCodec.decode(ReplayCodec.encode(header),ReplayMatch.Header::class.java))
        assertThrows(IllegalArgumentException::class.java) {
            header.copy(finalRanks=listOf(1,1,3,4))
        }
    }
    @Test fun fullOneHumanThreeBotMatchArchivesOnceWithExactLongStandingsAndPermissions() {
        val roster = players.map { TableParticipant(it.id,it.name,it.bot,false,BotDifficulty.EASY,null,true) }
        val rules = TaiwanPreset.SOUTHERN_COMMON.rules()
        var room = TaiwanSession.start(UUID(72,1),roster,4,Tile.set(false,RedFives.NONE),rules)
        fun mount() = room.synchronizeSeats(mapOf(players[0].id to 0))
        mount(); var ticks=0; var restores=0
        while (room.lifecycle() != TableSession.Lifecycle.FINISHED) {
            assertTrue(++ticks < 200_000)
            val game=room.game()
            if (game.phase == TaiwanGame.Phase.FINISHED) {
                room=TaiwanCodec.restoreSession(TaiwanCodec.saveSession(room)); mount(); restores++
                assertTrue(room.confirmNextHand(players[0].id,room.tableId(),room.incarnation(),room.game().decision))
            } else {
                val view=room.view(players[0].id).game()
                if (view.actions().isNotEmpty()) assertTrue(room.act(players[0].id,room.tableId(),room.incarnation(),view.decision(),TaiwanBot.choose(view)))
                room.tick()
                if (ticks==17) { room=TaiwanCodec.restoreSession(TaiwanCodec.saveSession(room)); mount() }
            }
        }
        assertTrue(restores >= 15)
        val archive=room.pendingReplays().single()
        assertTrue(archive.complete); assertEquals(room.scores(),archive.header().taiwanScores)
        assertEquals(room.scores().map { s -> 1+room.scores().count { it>s } },archive.header().finalRanks)
        assertEquals(3,archive.participants.count { it.bot }); assertTrue(archive.permits(players[0].id))
        assertFalse(archive.permits(players[1].id)); assertFalse(archive.permits(UUID.randomUUID()))
        ReplayCodec.validate(archive)
        room=TaiwanCodec.restoreSession(TaiwanCodec.saveSession(room)); mount(); repeat(10) { room.tick() }
        assertEquals(listOf(archive),room.pendingReplays())
        room.acknowledgeReplay(archive.id); room=TaiwanCodec.restoreSession(TaiwanCodec.saveSession(room)); room.clearMatch()
        assertTrue(room.pendingReplays().isEmpty())
    }
}
