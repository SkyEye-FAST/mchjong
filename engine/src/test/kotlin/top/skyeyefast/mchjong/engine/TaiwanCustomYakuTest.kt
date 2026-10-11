package top.skyeyefast.mchjong.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class TaiwanCustomYakuTest {
    private val text = YakuSet.Text("House", "独自役", "自定义役", "自訂役")
    private fun leaf(type: YakuCondition.Type, min: Int = 0, max: Int = 0, vararg values: String) =
        YakuCondition(type, emptyList(), values.toList(), min, max)
    private val regular = leaf(YakuCondition.Type.SHAPE, values = arrayOf("regular"))
    private fun award(id: String, condition: YakuCondition = regular, tai: Int = 20, vararg excludes: String) =
        YakuSet.Definition(id, text, text, condition, YakuSet.Reward(YakuSet.RewardType.TAI, tai, tai), excludes.toList())
    private fun rules(vararg awards: YakuSet.Definition, cap: Int? = null, zero: Boolean = false): TaiwanRules {
        val r = TaiwanPreset.POCKET_COMMON.rules()
        return TaiwanRules(r.name, if (zero) r.values.mapValues { 0 } else r.values, r.exclusions, r.sources,
            r.flowers, r.flowerSets, r.pinfu, r.replacements, cap, r.reserve, r.payment,
            YakuSelection.of(YakuSet("house:taiwan", MahjongVariant.TAIWAN, text, awards.toList())))
    }
    private fun physical(kinds: List<Int>): List<Int> {
        val counts = IntArray(34)
        return kinds.map { Tile.id(it, counts[it]++, false) }
    }
    private fun context(self: Boolean = false, flowers: Set<FlowerTile> = emptySet()) = TaiwanWinContext(
        if (self) TaiwanWinContext.Method.SELF_DRAW else TaiwanWinContext.Method.DISCARD, Tile.EAST, Tile.EAST, 1,
        flowers, if (flowers.isEmpty()) TaiwanWinContext.DrawOrigin.ORDINARY else TaiwanWinContext.DrawOrigin.FLOWER_REPLACEMENT,
        false, TaiwanWinContext.Opening.NONE, TaiwanWinContext.Ready.NONE)
    private val ambiguous = physical(listOf(0,0,0,1,1,1,2,2,2,12,13,14,24,25,26,31,31))
    private fun score(hand: List<Int>, rules: TaiwanRules, self: Boolean = false) =
        TaiwanHandAnalyzer.score(hand.dropLast(1), emptyList(), 0, hand.last(), context(self), rules)!!

    @Test fun nativeSwitchesApplyBeforeExclusionsAndDisableFlowerEvents() {
        val base = TaiwanPreset.POCKET_COMMON.rules()
        val selection = base.yakuSelection.withNative("mchjong:taiwan/concealed_self_draw", false)
            .withNative("mchjong:taiwan/eight_flowers", false).withNative("mchjong:taiwan/seven_robs_one", false)
        val selected = TaiwanGameState.Rules.of(base).withYakuSelection(selection).restore()
        val result = score(ambiguous, selected, true)
        assertFalse(result.awards.any { it.nativePattern() == TaiwanRules.Pattern.CONCEALED_SELF_DRAW })
        assertTrue(result.awards.any { it.nativePattern() == TaiwanRules.Pattern.CONCEALED })
        assertTrue(result.awards.any { it.nativePattern() == TaiwanRules.Pattern.SELF_DRAW })
        assertNull(TaiwanHandAnalyzer.flowerWin(ambiguous.dropLast(1), emptyList(), 0, Tile.ABSENT,
            context(true, FlowerTile.entries.toSet()), TaiwanHandAnalyzer.FlowerEvent.EIGHT_AFTER_INITIAL_REPLACEMENT, selected))
        val game = TaiwanFixtures.fixture(mapOf(0 to listOf(30), 1 to TaiwanFixtures.wait.take(10) + (136..141), 2 to listOf(143)),
            draws = listOf(142), tail = TaiwanFixtures.wait.drop(10) + listOf(32, 33), rules = selected)
        val turn = game.decisions().single()
        game.submit(0, turn.token, turn.actions.indexOfFirst { it.type == TaiwanAction.Type.DISCARD && it.tiles.any { t -> Tile.kind(t) == 30 } })
        game.decisions().toList().forEach { d -> game.submit(d.seat, d.token, d.actions.indexOfFirst { it.type == TaiwanAction.Type.PASS }) }
        assertNull(game.settlement)
        assertEquals(selection, TaiwanCodec.restore(TaiwanCodec.save(game)).save().rules().yakuSelection())
    }

    @Test fun awardsAreAppliedBeforeSelectingDecompositionAndCap() {
        val base = score(ambiguous, TaiwanPreset.POCKET_COMMON.rules())
        assertTrue(base.awards.any { it.nativePattern() == TaiwanRules.Pattern.THREE_CONCEALED_TRIPLETS })
        val custom = award("house:sequences", leaf(YakuCondition.Type.GROUPS, 5, 5, "sequence", "all", "*"))
        val result = score(ambiguous, rules(custom, cap = 5))
        assertEquals(5, result.tai)
        assertTrue(result.rawTai >= 20)
        assertTrue(result.shape.groups.all { it.type == TaiwanHandAnalyzer.GroupType.SEQUENCE })
        assertEquals(20, result.awards.single { it.id == "house:sequences" }.tai)
        assertNull(result.awards.single { it.id == "house:sequences" }.source)
        assertEquals(20, score(ambiguous, rules(custom, zero = true)).tai)
    }

    @Test fun exclusionsStaySimultaneousAndDiscardTripletsAreExposed() {
        val a = award("house:a", tai = 2, excludes = arrayOf("mchjong:taiwan/concealed"))
        val b = award("house:b", tai = 3, excludes = arrayOf("house:a"))
        val result = score(ambiguous, rules(a, b))
        assertFalse(result.awards.any { it.id in setOf("house:a", "mchjong:taiwan/concealed") })
        assertTrue(result.awards.any { it.id == "house:b" })
        val complete = physical(listOf(0,0,10,10,10,20,20,20,30,30,30,31,31,31,32,32,0))
        val concealed = rules(award("house:five", leaf(YakuCondition.Type.GROUPS, 5, 5, "triplet", "concealed", "*")))
        assertFalse(score(complete, concealed).awards.any { it.id == "house:five" })
        assertTrue(score(complete, concealed, true).awards.any { it.id == "house:five" })
    }

    @Test fun flowerVictoryKeepsSeparateAwardAndDoesNotInventAnOrdinaryWin() {
        val rules = rules(award("house:regular"), cap = 12)
        val c = context(true, FlowerTile.entries.toSet())
        val regular = TaiwanHandAnalyzer.flowerWin(ambiguous.dropLast(1), emptyList(), 0, ambiguous.last(), c,
            TaiwanHandAnalyzer.FlowerEvent.EIGHT_AFTER_REPLACEMENT, rules)!!
        assertEquals(TaiwanRules.Pattern.EIGHT_FLOWERS, regular.award.nativePattern())
        assertTrue(regular.handScore!!.awards.any { it.id == "house:regular" })
        assertEquals(regular.award.tai + regular.handScore.rawTai, regular.rawTai)
        assertEquals(12, regular.tai)
        val initial = TaiwanHandAnalyzer.flowerWin(ambiguous.dropLast(1), emptyList(), 0, Tile.ABSENT, c,
            TaiwanHandAnalyzer.FlowerEvent.EIGHT_AFTER_INITIAL_REPLACEMENT, rules)!!
        assertNull(initial.handScore)
        assertEquals(initial.award.tai, initial.rawTai)
    }

    @Test fun frozenRulesAndCustomAwardSurviveGameViewSessionAndReplay() {
        var condition = regular
        repeat(3) { condition = YakuCondition(YakuCondition.Type.ALL, listOf(condition), emptyList(), 0, 0) }
        val rules = rules(award("house:regular", condition))
        val game = TaiwanFixtures.fixture(mapOf(0 to listOf(33), 1 to TaiwanFixtures.wait), rules = rules)
        val recorder = TaiwanReplayRecorder(1, List(4) { 0L }, game)
        fun act(seat: Int, type: TaiwanAction.Type, kind: Int? = null) {
            val decision = game.decisions().single { it.seat == seat }
            val selected = decision.actions.indexOfFirst { it.type == type && (kind == null || it.tiles.any { t -> Tile.kind(t) == kind }) }
            assertTrue(selected >= 0)
            val before = game.save()
            game.submit(seat, decision.token, selected)
            recorder.accepted(before, seat, decision.actions, selected, game)
        }
        act(0, TaiwanAction.Type.DISCARD, 33)
        act(1, TaiwanAction.Type.WIN)
        game.decisions().toList().forEach { act(it.seat, TaiwanAction.Type.PASS) }
        val saved = TaiwanCodec.save(game)
        assertTrue(TaiwanReplayPlayback.samePosition(game.save(), TaiwanCodec.restore(saved).save()))
        val view = game.view(0, true)
        assertEquals(view, TaiwanCodec.decodeView(TaiwanCodec.encodeView(view)))
        assertTrue(view.result().awards().any { it.id == "house:regular" })
        val forged = com.google.gson.JsonParser.parseString(TaiwanCodec.encodeView(view)).asJsonObject
        forged.getAsJsonObject("result").getAsJsonArray("awards").single { it.asJsonObject["id"].asString == "house:regular" }
            .asJsonObject.addProperty("tai", 21)
        assertThrows(IllegalArgumentException::class.java) { TaiwanCodec.decodeView(forged.toString()) }
        val hand = recorder.finish(game)
        val players = (0..3).map { ReplayMatch.Participant(UUID(80, it.toLong()), "Player $it", it != 0) }
        val replay = ReplayMatch(UUID.randomUUID(), UUID(81,1), 1,2,players, MahjongVariant.TAIWAN, false,
            null,null,null,TaiwanReplay(TaiwanGameState.Rules.of(rules),listOf(hand)))
        val restored = ReplayCodec.decode(ReplayCodec.encode(replay), ReplayMatch::class.java)
        ReplayCodec.validate(restored)
        assertTrue(TaiwanReplayPlayback.samePosition(game.save(), TaiwanReplayPlayback.timeline(restored, 0).frames().last().state()))
        val roster = players.map { TableParticipant(it.id, it.name, it.bot, false, BotDifficulty.EASY, null, true) }
        val session = TaiwanSession.start(UUID(81,1), roster, 4, Tile.standard144Set(), rules)
        val resumed = TaiwanCodec.restoreSession(TaiwanCodec.saveSession(session))
        assertEquals(session.save().rules(), resumed.save().rules())
        assertTrue(TaiwanReplayPlayback.samePosition(session.game().save(), resumed.game().save()))
    }

    @Test fun simultaneousSevenFlowerAndCustomHandRouteTheCappedComponentsToTheirPayers() {
        val rules = rules(award("house:regular"), cap = 12)
        val wait = TaiwanFixtures.wait
        val game = TaiwanFixtures.fixture(mapOf(0 to listOf(30), 1 to wait.take(10) + (136..141), 2 to listOf(143)),
            draws = listOf(142), tail = wait.drop(10) + listOf(32, 33), rules = rules)
        val turn = game.decisions().single()
        game.submit(0, turn.token, turn.actions.indexOfFirst { it.type == TaiwanAction.Type.DISCARD && it.tiles.any { t -> Tile.kind(t) == 30 } })
        game.decisions().toList().forEach { d -> game.submit(d.seat, d.token, d.actions.indexOfFirst { it.type == TaiwanAction.Type.PASS }) }
        val result = game.settlement!!
        val flower = result.flowerScore!!
        assertTrue(flower.handScore!!.awards.any { it.id == "house:regular" })
        assertEquals(12, flower.tai)
        assertEquals(12, result.transfers.single { it.from == 2 }.handTai)
        assertTrue(result.transfers.filter { it.from != 2 }.all { it.handTai == 12 - flower.award.tai })
        assertEquals(0L, result.deltas.sum())
        assertTrue(TaiwanReplayPlayback.samePosition(game.save(), TaiwanCodec.restore(TaiwanCodec.save(game)).save()))
    }
}
