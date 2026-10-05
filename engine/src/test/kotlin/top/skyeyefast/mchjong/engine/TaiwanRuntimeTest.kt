package top.skyeyefast.mchjong.engine

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.skyeyefast.mchjong.engine.TaiwanAction.Type
import top.skyeyefast.mchjong.engine.TaiwanFixtures.act
import top.skyeyefast.mchjong.engine.TaiwanFixtures.fixture
import top.skyeyefast.mchjong.engine.TaiwanFixtures.pass
import top.skyeyefast.mchjong.engine.TaiwanFixtures.wait

class TaiwanRuntimeTest {
    private fun json(game: TaiwanGame) = JsonParser.parseString(TaiwanCodec.save(game)).asJsonObject
    private fun equivalent(a: TaiwanGame, b: TaiwanGame) {
        val x = json(a); val y = json(b)
        for (key in listOf("revision", "decision")) { x.remove(key); y.remove(key) }
        assertEquals(x, y)
    }
    private fun roundTrip(game: TaiwanGame): TaiwanGame = TaiwanCodec.restore(TaiwanCodec.save(game)).also {
        equivalent(game, it)
        assertTrue(it.decision > game.decision)
        assertEquals(game.decisions().map { d -> d.seat to d.actions.map(TaiwanGameState.Action::of) },
            it.decisions().map { d -> d.seat to d.actions.map(TaiwanGameState.Action::of) })
    }
    private fun reject(game: TaiwanGame, mutate: (JsonObject) -> Unit) {
        val document = json(game); mutate(document)
        assertThrows(IllegalArgumentException::class.java) { TaiwanCodec.restore(document.toString()) }
    }

    @Test fun fixedSlotsFreezeBothStockSizesAndEveryDealerAndDiceSum() {
        for (preset in TaiwanPreset.entries) for (dealer in 0..3) for (sum in 3..18) {
            var extra = sum - 3
            val dice = List(3) { (minOf(extra, 5) + 1).also { extra -= it - 1 } }
            val rules = preset.rules()
            val stock = TaiwanWall.expected(rules)
            val opening = TaiwanOpening(dealer, dice)
            val wall = TaiwanWall(stock, opening, rules)
            assertEquals((dealer * stock.size / 4 + 2 * sum) % stock.size, opening.cutIndex(stock.size))
            repeat(65) { i -> assertEquals(stock[TaiwanWallLayout.drawSlot(stock.size, opening, i)], wall.draw()) }
            repeat(5) { i -> assertEquals(stock[TaiwanWallLayout.replacementSlot(stock.size, opening, i)], wall.replace()) }
            val saved = wall.save()
            val restored = TaiwanWall.restore(saved, opening, rules)
            assertEquals(wall.physicalSlots(), restored.physicalSlots())
            assertEquals(wall.draw(), restored.draw())
            assertEquals(wall.replace(), restored.replace())
            for (slot in stock.indices) assertEquals(slot, TaiwanWallLayout.slot(stock.size, TaiwanWallLayout.side(stock.size, slot),
                TaiwanWallLayout.stack(stock.size, slot), TaiwanWallLayout.layer(slot)))
        }
        assertEquals(2, TaiwanOpening(3, listOf(6,6,6)).cutIndex(136)) // cross wall, then skip one more stack
        assertEquals(0, TaiwanOpening(3, listOf(6,6,5)).cutIndex(136)) // exact side boundary
    }

    @Test fun everySeededActionRoundTripsWithoutRedealingOrLeakingWallIdentity() {
        for (preset in TaiwanPreset.entries) {
            var game = TaiwanGame.shuffled(19, preset.rules())
            var steps = 0
            while (game.phase != TaiwanGame.Phase.FINISHED) {
                assertTrue(++steps < 1000)
                val old = game.decisions().first()
                game = roundTrip(game)
                assertThrows(IllegalArgumentException::class.java) { game.submit(old.seat, old.token, 0) }
                for (s in -1..3) {
                    val view = game.view(s)
                    assertEquals(view, TaiwanCodec.decodeView(TaiwanCodec.encodeView(view)))
                    assertTrue(view.wall().all { it == Tile.HIDDEN || it == Tile.ABSENT })
                    for (other in 0..3) if (other != s) assertTrue(view.seats()[other].concealed().isEmpty())
                }
                val d = game.decisions().first()
                val choice = d.actions.indexOfFirst { it.type == Type.WIN }.takeIf { it >= 0 }
                    ?: d.actions.indexOfFirst { it.type == if (game.phase == TaiwanGame.Phase.REACTION) Type.PASS else Type.DISCARD }
                game.submit(d.seat, d.token, choice)
            }
            roundTrip(game)
        }
    }

    @Test fun submittedWinsRemainPrivateAndLegalAcrossReactionRestore() {
        val game = fixture(mapOf(0 to listOf(33), 1 to wait, 2 to wait))
        act(game,0,Type.DISCARD,33)
        val spectatorBefore = JsonParser.parseString(TaiwanCodec.encodeView(game.view(-1))).asJsonObject
        val old = game.decisions().single { it.seat == 2 }
        act(game,2,Type.WIN)
        val spectatorAfter = JsonParser.parseString(TaiwanCodec.encodeView(game.view(-1))).asJsonObject
        spectatorBefore.remove("revision"); spectatorAfter.remove("revision")
        assertEquals(spectatorBefore,spectatorAfter)
        assertTrue(game.view(2).responded()); assertFalse(game.view(1).responded())
        val restored = roundTrip(game)
        assertTrue(restored.view(2).responded()); assertTrue(restored.view(2).actions().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { restored.submit(2,old.token,0) }
        act(restored,1,Type.WIN); act(restored,3,Type.PASS)
        assertEquals(1,restored.settlement!!.winner)
        roundTrip(restored)
        reject(game) { it.getAsJsonArray("replies")[0].asJsonObject.addProperty("seat",0) }
        reject(game) { it.getAsJsonArray("replies").add(it.getAsJsonArray("replies")[0].deepCopy()) }
        reject(game) { it.getAsJsonArray("replies")[0].asJsonObject.getAsJsonObject("action").addProperty("type","ADDED_KONG") }
    }
    @Test fun opponentHandAndFutureWallChangesCannotAffectAnyRecipientProjection() {
        val game=fixture()
        val document=json(game)
        val players=document.getAsJsonArray("players")
        val first=players[1].asJsonObject.get("hand")
        val second=players[2].asJsonObject.get("hand")
        players[1].asJsonObject.add("hand",second); players[2].asJsonObject.add("hand",first)
        val slots=document.getAsJsonObject("wall").getAsJsonArray("slots")
        val occupied=(0 until slots.size()).filter { slots[it].asInt!=Tile.ABSENT }
        val tile=slots[occupied[0]]; slots.set(occupied[0],slots[occupied[1]]); slots.set(occupied[1],tile)
        val changed=TaiwanCodec.restore(document.toString())
        for (recipient in listOf(-1,0,3)) {
            val before=JsonParser.parseString(TaiwanCodec.encodeView(game.view(recipient))).asJsonObject
            val after=JsonParser.parseString(TaiwanCodec.encodeView(changed.view(recipient))).asJsonObject
            for (key in listOf("revision","decision")) { before.remove(key); after.remove(key) }
            assertEquals(before,after)
        }
    }

    private fun pendingAddedKong(): TaiwanGame {
        val robber = listOf(1,2,9,10,11,12,13,14,15,16,17,18,19,20,31,31)
        val game = fixture(mapOf(0 to listOf(0),1 to listOf(0,0,30),2 to robber),draws=listOf(32,32,32,0),tail=listOf(33))
        act(game,0,Type.DISCARD,0); act(game,1,Type.PONG); pass(game)
        act(game,1,Type.DISCARD,30); pass(game)
        repeat(3) { act(game,game.turn,Type.DISCARD,32); pass(game) }
        act(game,1,Type.ADDED_KONG)
        return game
    }
    @Test fun addedKongTransactionsRestoreBeforeCommitAndBeforeRobbery() {
        val game = pendingAddedKong()
        act(game,3,Type.PASS)
        val restored = roundTrip(game)
        assertEquals(Meld.Type.TRIPLET,restored.player(1).melds.single().type())
        act(restored,2,Type.WIN); pass(restored)
        assertEquals(0,restored.wall.kongs); assertEquals(2,restored.settlement!!.winner)
        roundTrip(restored)
        val completed = roundTrip(pendingAddedKong()); pass(completed)
        assertEquals(Meld.Type.ADDED_QUAD,completed.player(1).melds.single().type())
        assertEquals(1,completed.wall.kongs); roundTrip(completed)
        reject(game) { it.getAsJsonObject("offer").addProperty("addedMeld",4) }
    }
    @Test fun chowAndPongAfterKongDrawRestoreTheirMandatoryDiscardAndPassingBasis() {
        for (type in listOf(Type.CHOW,Type.PONG)) {
            val owned=if (type==Type.CHOW) listOf(3,5) else listOf(4,4)
            var game=fixture(mapOf(0 to listOf(0,0,0,0,4),1 to owned),tail=listOf(32))
            act(game,0,Type.CONCEALED_KONG,0); act(game,0,Type.DISCARD,4)
            act(game,1,type); pass(game)
            game=roundTrip(game)
            assertTrue(game.decisions().single().actions.none { it.type in setOf(Type.WIN,Type.CONCEALED_KONG,Type.ADDED_KONG) })
            act(game,1,Type.DISCARD); roundTrip(game)
        }
    }
    @Test fun readyAndPassedWinRestoreTheirDifferentResetContracts() {
        var ready = fixture(mapOf(0 to wait+30),draws=listOf(31,31,31,32))
        act(ready,0,Type.READY_DISCARD,30); ready=roundTrip(ready); pass(ready)
        repeat(3) { act(ready,ready.turn,Type.DISCARD,31); ready=roundTrip(ready); pass(ready) }
        ready=roundTrip(ready)
        assertEquals(TaiwanWinContext.Ready.HEAVENLY,ready.player(0).ready)
        assertEquals(listOf(32),ready.decisions().single().actions.filter { it.type==Type.DISCARD }.map { Tile.kind(it.tiles.single()) })
        var passed=fixture(mapOf(0 to listOf(33),1 to wait),draws=listOf(33))
        act(passed,0,Type.DISCARD,33); pass(passed); passed=roundTrip(passed)
        assertTrue(passed.player(1).passedWin); assertTrue(passed.view(1).passedWin())
        assertNull(passed.view(-1).passedWin()); assertFalse(passed.view(0).passedWin())
        assertTrue(passed.decisions().single().actions.none { it.type==Type.WIN })
        act(passed,1,Type.DISCARD,0); passed=roundTrip(passed)
        assertFalse(passed.player(1).passedWin)
    }
    @Test fun concealedKongsAndAllLosingHandsStayHiddenEvenAtSettlement() {
        val game=fixture(mapOf(0 to listOf(0,0,0,0)))
        act(game,0,Type.CONCEALED_KONG,0)
        assertTrue(game.view(-1).seats()[0].melds().single().tiles().all { it==Tile.HIDDEN })
        assertTrue(game.view(0).seats()[0].melds().single().tiles().all { it>=0 })
        while (game.phase!=TaiwanGame.Phase.FINISHED) {
            if (game.phase==TaiwanGame.Phase.REACTION) pass(game) else act(game,game.turn,Type.DISCARD)
        }
        assertTrue(game.view(-1).seats().all { it.concealed().isEmpty() })
        assertTrue(game.view(-1).seats()[0].melds().single().tiles().all { it==Tile.HIDDEN })
        assertFalse(TaiwanCodec.encodeView(game.view(-1)).contains("winningGroup"))
        roundTrip(game)
    }
    @Test fun finishedFlowerAndOrdinaryResultsAreRecomputedAndPaymentsCannotBeAltered() {
        val pocket=TaiwanFixtures.pocket
        val capped=TaiwanRules("CAPPED",pocket.values,pocket.exclusions,pocket.sources,pocket.flowers,pocket.flowerSets,pocket.pinfu,pocket.replacements,4,
            payment=TaiwanRules.Payment(base=10,perTai=2))
        val cases=listOf(fixture(mapOf(1 to (136..143).toList())),fixture(mapOf(1 to (136..142).toList(),2 to listOf(143))),
            fixture(mapOf(0 to listOf(33),1 to wait)).also { act(it,0,Type.DISCARD,33); act(it,1,Type.WIN); pass(it) },
            fixture(mapOf(0 to wait+33)).also { act(it,0,Type.WIN) },
            fixture(mapOf(1 to (136..142).toList()),draws=listOf(32,143)).also {
                act(it,0,Type.DISCARD); pass(it); act(it,1,Type.DISCARD,32); pass(it)
            },
            fixture(mapOf(0 to listOf(30),1 to wait.take(10)+(136..141),2 to listOf(143)),draws=listOf(142),tail=wait.drop(10)+listOf(32,33),rules=capped).also {
                act(it,0,Type.DISCARD,30); pass(it)
            })
        for (game in cases) {
            roundTrip(game)
            reject(game) { it.getAsJsonObject("settlement").getAsJsonArray("transfers")[0].asJsonObject.addProperty("amount",999) }
            reject(game) { it.getAsJsonObject("settlement").addProperty("nextDealer",3) }
            reject(game) { it.getAsJsonObject("settlement").getAsJsonArray("deltas").set(0,com.google.gson.JsonPrimitive(100)) }
        }
    }
    @Test fun invalidZonesProvenanceCursorsAndJsonAreRejectedAtomically() {
        val game=fixture(mapOf(0 to listOf(0,0,0,0)))
        reject(game) { it.getAsJsonObject("wall").addProperty("front",66) }
        reject(game) { it.getAsJsonObject("wall").addProperty("tail",-1) }
        reject(game) { it.getAsJsonObject("wall").addProperty("kongs",1) }
        reject(game) { val hand=it.getAsJsonArray("players")[1].asJsonObject.getAsJsonArray("hand"); hand.set(0,hand[1]) }
        act(game,0,Type.CONCEALED_KONG,0)
        reject(game) { it.getAsJsonArray("players")[0].asJsonObject.getAsJsonArray("melds")[0].asJsonObject.addProperty("fromSeat",1) }
        val doc=TaiwanCodec.save(game)
        for (bad in listOf(doc.replaceFirst("\"format\":1","\"format\":1,\"format\":1"),doc.replaceFirst("\"format\":1","\"format\":2"),
            doc.replaceFirst("\"format\":1","\"format\":1.5"),doc.replaceFirst("\"format\":1","\"format\":\"1\""),doc.replaceFirst("\"format\":1,",""),
            doc.replaceFirst("\"format\":1","\"format\":1,\"unknown\":false"),doc+"{}", " ".repeat(65537),"{\"x\":"+"[".repeat(17)+"0"+"]".repeat(17)+"}"))
            assertThrows(IllegalArgumentException::class.java) { TaiwanCodec.restore(bad) }
        assertThrows(IllegalArgumentException::class.java) { TaiwanCodec.decodeView(doc) }
        assertThrows(IllegalArgumentException::class.java) { TaiwanCodec.restore(TaiwanCodec.encodeView(game.view(0))) }
        val leaking=JsonParser.parseString(TaiwanCodec.encodeView(game.view(0))).asJsonObject
        leaking.getAsJsonArray("seats")[1].asJsonObject.add("concealed",json(game).getAsJsonArray("players")[1].asJsonObject.get("hand"))
        assertThrows(IllegalArgumentException::class.java) { TaiwanCodec.decodeView(leaking.toString()) }
    }
}
