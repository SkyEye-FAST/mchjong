package top.skyeyefast.mchjong.engine

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import top.skyeyefast.mchjong.engine.TaiwanAction.Type
import top.skyeyefast.mchjong.engine.TaiwanFixtures.act
import top.skyeyefast.mchjong.engine.TaiwanFixtures.fixture
import top.skyeyefast.mchjong.engine.TaiwanFixtures.pass
import top.skyeyefast.mchjong.engine.TaiwanFixtures.wait

class TaiwanSessionTest {
    private val roster=List(4) { TableParticipant(UUID(0,it+1L),"Player $it") }
    private fun mount(session: TaiwanSession) = session.synchronizeSeats(roster.mapIndexed { s,p -> p.id() to s }.toMap())
    private fun session(game: TaiwanGame): TaiwanSession {
        val initial=TaiwanSession.start(UUID.randomUUID(),roster,81,TaiwanWall.expected(game.rules),game.rules)
        val s=initial.save()
        return TaiwanSession.restore(TaiwanSession.State(s.format(),s.room(),s.rules(),s.stock(),s.control(),s.clocks(),0,0,s.futureSeed(),emptyList(),game.save())).also { mount(it) }
    }
    private fun restore(session: TaiwanSession): TaiwanSession = TaiwanCodec.restoreSession(TaiwanCodec.saveSession(session)).also {
        assertEquals(session.scores(),it.scores()); assertNotEquals(session.incarnation(),it.incarnation()); assertTrue(it.paused() || it.lifecycle()==TableSession.Lifecycle.FINISHED)
        mount(it)
    }
    private fun submit(session: TaiwanSession, seat: Int, type: Type, kind: Int?=null) {
        val v=session.view(roster[seat].id()).game()
        val index=v.actions().indexOfFirst { it.type()==type && (kind==null || it.tiles().any { t -> Tile.kind(t)==kind }) }
        assertTrue(index>=0,"Missing $type/$kind")
        assertTrue(session.act(roster[seat].id(),session.tableId(),session.incarnation(),v.decision(),index))
    }
    @Test fun humanRoomUsesBothStocksAndRegisteredVariantDispatch() {
        for (preset in TaiwanPreset.entries) {
            val rules=preset.rules(); val room=TaiwanSession(UUID.randomUUID(),24,rules)
            roster.forEachIndexed { seat,p -> assertTrue(room.join(p.id(),p.name(),seat)) }
            assertTrue(room.configureEquipment(false,TaiwanWall.expected(rules)))
            assertEquals(MahjongVariant.TAIWAN,room.variant()); assertTrue(room.view(roster[0].id()).equipped())
            val lobby=(TableSessionCodec.restore(TableSessionCodec.save(room)) as TaiwanSession)
            mount(lobby)
            val actions=lobby.roomActions(roster[0].id())
            val begin=actions.indexOfFirst { it.type()==RoomAction.Type.BEGIN_SEATING }
            assertTrue(lobby.actRoom(roster[0].id(),lobby.tableId(),lobby.incarnation(),lobby.decision(),begin))
            // Seat assignment can permute the humans, so remount each authenticated identity.
            lobby.synchronizeSeats(lobby.participants().mapIndexed { s,p -> p.id() to s }.toMap())
            for (p in lobby.participants()) {
                val ready=lobby.roomActions(p.id()).indexOfFirst { it.type()==RoomAction.Type.READY }
                assertTrue(lobby.actRoom(p.id(),lobby.tableId(),lobby.incarnation(),lobby.decision(),ready))
            }
            assertEquals(TableSession.Lifecycle.PLAYING,lobby.lifecycle())
            assertEquals(if (preset==TaiwanPreset.POCKET_COMMON) 144 else 136,lobby.game().wall.physicalSlots().size)
            val view=lobby.view(lobby.participants()[0].id())
            assertEquals(view,TaiwanCodec.decodeSessionView(TaiwanCodec.encodeSessionView(view)))
        }
    }
    @Test fun reactionClocksAndViewsRevealOnlyTheRecipientsOwnResponse() {
        var session=session(fixture(mapOf(0 to listOf(33),1 to wait,2 to wait)))
        submit(session,0,Type.DISCARD,33)
        val before=JsonParser.parseString(TaiwanCodec.encodeSessionView(session.view(UUID.randomUUID()))).asJsonObject
        submit(session,2,Type.WIN)
        val after=JsonParser.parseString(TaiwanCodec.encodeSessionView(session.view(UUID.randomUUID()))).asJsonObject
        before.remove("revision"); after.remove("revision"); before.getAsJsonObject("game").remove("revision"); after.getAsJsonObject("game").remove("revision")
        assertEquals(before,after)
        assertNull(session.view(UUID.randomUUID()).clock())
        val clocks=session.save().clocks()
        session.tick()
        assertEquals(clocks[2],session.save().clocks()[2])
        assertEquals(clocks[1].moveTicks()-1,session.save().clocks()[1].moveTicks())
        session=restore(session)
        assertTrue(session.view(roster[2].id()).game().responded())
        submit(session,1,Type.WIN); submit(session,3,Type.PASS)
        assertEquals(1,session.game().settlement!!.winner)
    }
    @Test fun pauseRestorationAndExitVotesPreserveAllClockAllowances() {
        var session=session(fixture())
        session.tick()
        val clocks=session.save().clocks(); val age=session.save().age()
        session.synchronizeSeats(emptyMap<UUID,Int>(),roster.map { it.id() }.toSet())
        repeat(15) { session.tick() }
        assertTrue(session.paused()); assertEquals(clocks,session.save().clocks()); assertEquals(age,session.save().age())
        val oldIncarnation=session.incarnation(); val oldToken=session.game().decision
        session=restore(session)
        assertFalse(session.act(roster[0].id(),session.tableId(),oldIncarnation,oldToken,0))
        assertEquals(clocks,session.save().clocks())
        for (p in roster) session.unseat(p.id())
        val leave=session.view(roster.last().id())
        assertTrue(leave.leaveDecision()); assertEquals(-1,leave.recipient()); assertTrue(leave.game().seats().all { it.concealed().isEmpty() })
        assertEquals(leave,TaiwanCodec.decodeSessionView(TaiwanCodec.encodeSessionView(leave)))
        assertFalse(session.view(UUID.randomUUID()).leaveDecision())
        session=restore(session)
        assertTrue(session.requestExit(roster[0].id()))
        val withVote=restore(session)
        repeat(10) { withVote.tick() }
        assertEquals(clocks,withVote.save().clocks())
        assertTrue(withVote.answerExit(roster[1].id(),withVote.save().room().exitVote().id(),false))
        withVote.tick(); assertEquals(clocks[0].moveTicks()-1,withVote.save().clocks()[0].moveTicks())
    }
    @Test fun drawRetainsDealerAndPartialAcknowledgementsResumeWithoutPayingTwice() {
        val game=fixture()
        while (game.phase!=TaiwanGame.Phase.FINISHED) if (game.phase==TaiwanGame.Phase.REACTION) pass(game) else act(game,game.turn,Type.DISCARD)
        var session=session(game)
        val token=session.game().decision
        assertTrue(session.confirmNextHand(roster[0].id(),session.tableId(),session.incarnation(),token))
        assertFalse(session.confirmNextHand(roster[0].id(),session.tableId(),session.incarnation(),token))
        repeat(31) { session.tick() }
        session=restore(session)
        assertEquals(1,session.save().confirmed()); assertEquals(31,session.save().age())
        for (seat in 1..3) assertTrue(session.confirmNextHand(roster[seat].id(),session.tableId(),session.incarnation(),session.game().decision))
        assertEquals(0,session.game().opening.dealer); assertEquals(1,session.game().continuation); assertEquals(Tile.EAST,session.game().roundWind)
        assertEquals(2,session.view(roster[0].id()).handNumber()); assertEquals(listOf(0L,0L,0L,0L),session.scores())
        assertTrue(session.game().decision>token)
        restore(session)
    }
    @Test fun pausedResultReadingResumesAtItsExactDeadlineAndTimeoutNeverClaimsAWin() {
        val game=fixture(mapOf(0 to listOf(33),1 to wait))
        act(game,0,Type.DISCARD,33); act(game,1,Type.WIN); pass(game)
        var room=session(game)
        repeat(199) { room.tick() }
        val scores=room.scores(); val token=room.game().decision
        room.synchronizeSeats(emptyMap<UUID,Int>())
        repeat(205) { room.tick() }
        assertEquals(199,room.save().age()); assertEquals(1,room.view(roster[0].id()).handNumber())
        room=restore(room); room.tick()
        assertEquals(2,room.view(roster[0].id()).handNumber()); assertEquals(scores,room.scores())
        assertEquals(1,room.game().opening.dealer); assertEquals(0,room.game().continuation)
        assertTrue(room.game().decision>token)

        val waiting=session(fixture(mapOf(0 to listOf(33),1 to wait)))
        submit(waiting,0,Type.DISCARD,33)
        val saved=waiting.save()
        val timed=TaiwanSession.restore(TaiwanSession.State(saved.format(),saved.room(),saved.rules(),saved.stock(),TimeControl(0,1),
            List(4) { TimeControl.Clock(20,0,false) },saved.age(),saved.confirmed(),saved.futureSeed(),saved.completed(),saved.game()))
        mount(timed); repeat(20) { timed.tick() }
        assertEquals(TaiwanGame.Phase.TURN,timed.game().phase); assertNull(timed.game().settlement)
        assertTrue(timed.game().player(1).passedWin)
    }

    /** Deterministic legal-action driver for lifecycle tests, not a production opponent. */
    private fun choose(view: TaiwanView): Int {
        val actions=view.actions()
        actions.indexOfFirst { it.type()==Type.WIN }.takeIf { it>=0 }?.let { return it }
        if (view.phase()==TaiwanGame.Phase.REACTION) return actions.indexOfFirst { it.type()==Type.PASS }
        actions.indexOfFirst { it.type()==Type.READY_DISCARD }.takeIf { it>=0 }?.let { return it }
        val own=view.seats()[view.recipient()]
        val candidates=actions.withIndex().filter { it.value.type()==Type.DISCARD }.distinctBy { Tile.kind(it.value.tiles().single()) }
        return candidates.minWith(compareBy<IndexedValue<TaiwanGameState.Action>> {
            TaiwanHandAnalyzer.shanten(own.concealed()-it.value.tiles().single(),own.melds(),view.recipient())
        }.thenByDescending { isolated(own.concealed(),Tile.kind(it.value.tiles().single())) }).index
    }
    private fun isolated(hand: List<Int>, kind: Int): Int {
        val counts=hand.groupingBy(Tile::kind).eachCount()
        var support=2*(counts.getValue(kind)-1)
        if (kind<27) for (near in kind-2..kind+2) if (near!=kind && near>=0 && near/9==kind/9) support+=counts[near]?:0
        return -support
    }
    @Test fun bothPresetsCompleteFourRoundsWithRepeatFinalDealersAndRestoredScores() {
        for (preset in TaiwanPreset.entries) {
            var session=TaiwanSession.start(UUID.randomUUID(),roster,4,TaiwanWall.expected(preset.rules()),preset.rules())
            mount(session)
            var actions=0; var hands=0; var rotations=0; var continuation=0; var sawRepeat=false
            var finalPosition: TaiwanSession.State? = null
            val totals=MutableList(4) { 0L }
            while (session.lifecycle()!=TableSession.Lifecycle.FINISHED) {
                val game=session.game()
                if (rotations==15 && finalPosition==null) finalPosition=session.save()
                assertEquals(rotations%4,game.opening.dealer); assertEquals(Tile.EAST+rotations/4,game.roundWind); assertEquals(continuation,game.continuation)
                if (game.phase==TaiwanGame.Phase.FINISHED) {
                    assertTrue(++hands<250)
                    val result=game.settlement!!
                    for (seat in 0..3) totals[seat]+=result.deltas[seat]
                    assertEquals(totals,session.scores())
                    session=restore(session)
                    val retain=result.nextDealer==game.opening.dealer
                    if (retain) sawRepeat=true else rotations++
                    continuation=result.nextContinuation
                    for (p in roster) assertTrue(session.confirmNextHand(p.id(),session.tableId(),session.incarnation(),session.game().decision))
                } else {
                    assertTrue(++actions<60_000)
                    val d=game.decisions().first()
                    val view=session.view(roster[d.seat].id()).game()
                    assertTrue(session.act(roster[d.seat].id(),session.tableId(),session.incarnation(),d.token,choose(view)))
                }
            }
            val last=session.game().settlement!!
            for (s in 0..3) totals[s]+=last.deltas[s]
            assertEquals(15,rotations); assertEquals(3,session.game().opening.dealer); assertEquals(Tile.NORTH,session.game().roundWind)
            assertNotEquals(3,last.winner); assertTrue(session.matchEnded()); assertTrue(sawRepeat)
            assertEquals(totals,session.scores()); session=restore(session); assertEquals(totals,session.scores())
            assertFalse(session.confirmNextHand(roster[0].id(),session.tableId(),session.incarnation(),session.game().decision))
            assertTrue(session.view(UUID.randomUUID()).game().seats().all { it.concealed().isEmpty() })
            // Exercise a guaranteed final-dealer win using a separately played physical fixture.
            val position=checkNotNull(finalPosition)
            val dealerWin=fixture(mapOf(3 to wait+33),rules=preset.rules(),dealer=3,roundWind=Tile.NORTH,continuation=position.game().continuation())
            act(dealerWin,3,Type.WIN)
            val repeat=TaiwanSession.restore(TaiwanSession.State(position.format(),position.room(),position.rules(),position.stock(),position.control(),position.clocks(),
                0,0,position.futureSeed(),position.completed(),dealerWin.save()))
            mount(repeat)
            assertFalse(repeat.matchEnded()); assertEquals(TableSession.Lifecycle.PLAYING,repeat.lifecycle())
            for (p in roster) assertTrue(repeat.confirmNextHand(p.id(),repeat.tableId(),repeat.incarnation(),repeat.game().decision))
            assertEquals(3,repeat.game().opening.dealer); assertEquals(Tile.NORTH,repeat.game().roundWind)
            assertEquals(dealerWin.continuation+1,repeat.game().continuation)
            restore(repeat)
        }
    }
    @Test fun malformedSessionChainAndViewCannotCrossTheSaveBoundary() {
        val session=session(fixture())
        val doc=JsonParser.parseString(TaiwanCodec.saveSession(session)).asJsonObject
        doc.addProperty("futureSeed",1)
        assertThrows(IllegalArgumentException::class.java) { TaiwanCodec.restoreSession(doc.toString()) }
        val data=JsonParser.parseString(TaiwanCodec.saveSession(session)).asJsonObject
        data.getAsJsonObject("game").addProperty("roundWind",Tile.SOUTH)
        assertThrows(IllegalArgumentException::class.java) { TaiwanCodec.restoreSession(data.toString()) }
        assertThrows(IllegalArgumentException::class.java) { TaiwanCodec.decodeSessionView(TaiwanCodec.saveSession(session)) }
        assertThrows(IllegalArgumentException::class.java) { TaiwanCodec.restoreSession(TaiwanCodec.encodeSessionView(session.view(roster[0].id()))) }
    }
}
