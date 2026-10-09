package top.skyeyefast.mchjong.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import top.skyeyefast.mchjong.engine.TaiwanAction.Type
import top.skyeyefast.mchjong.engine.TaiwanFixtures.act
import top.skyeyefast.mchjong.engine.TaiwanFixtures.pass
import top.skyeyefast.mchjong.engine.TaiwanFixtures.fixture
import top.skyeyefast.mchjong.engine.TaiwanFixtures.pocket
import top.skyeyefast.mchjong.engine.TaiwanFixtures.wait

class TaiwanGameTest {
    @Test fun openingUsesFourPacketsAndRoundBasedFlowers() {
        val game = fixture(mapOf(0 to listOf(136), 1 to listOf(137)), tail = listOf(138, 32, 33))
        assertEquals(setOf(FlowerTile.SPRING, FlowerTile.AUTUMN), game.player(0).flowers.toSet())
        assertEquals(listOf(FlowerTile.SUMMER), game.player(1).flowers)
        assertTrue(game.player(0).concealed.any { Tile.kind(it) == 33 })
        assertTrue(game.player(1).concealed.any { Tile.kind(it) == 32 })
        assertEquals(17, game.player(0).concealed.size)
        assertEquals(16, game.player(1).concealed.size)
        assertEquals(144 - 65 - 3 - 16, game.wall.drawable)
        game.checkConservation()
        assertThrows(IllegalArgumentException::class.java) { TaiwanGame(pocket, TaiwanOpening(0, listOf(1, 1, 1)), List(144) { 0 }) }
    }

    @Test fun nearestWinnerIsIndependentOfResponseArrivalAndPaysOnce() {
        fun run(reverse: Boolean): TaiwanGame {
            val game = fixture(mapOf(0 to listOf(33), 1 to wait, 2 to wait), continuation = 2)
            act(game, 0, Type.DISCARD, 33)
            val requests = game.decisions().let { if (reverse) it.reversed() else it }
            for (d in requests) act(game, d.seat, if (d.seat == 1 || d.seat == 2) Type.WIN else Type.PASS)
            return game
        }
        val a = run(false)
        val b = run(true)
        assertEquals(1, a.settlement!!.winner)
        assertEquals(a.settlement!!.deltas, b.settlement!!.deltas)
        val transfer = a.settlement!!.transfers.single()
        assertEquals(0, transfer.from)
        assertEquals(5, transfer.dealerTai)
        assertEquals(transfer.base + transfer.handTai + transfer.dealerTai, transfer.amount)
        assertEquals(0L, a.settlement!!.deltas.sum())
        assertEquals(1, a.settlement!!.nextDealer)
        assertEquals(0, a.settlement!!.nextContinuation)
        assertTrue(a.decisions().isEmpty())
    }

    @Test fun pongBeatsChowAndCallsCannotImmediatelyKong() {
        val game = fixture(mapOf(0 to listOf(4), 1 to listOf(3, 5), 2 to listOf(4, 4)))
        act(game, 0, Type.DISCARD, 4)
        act(game, 1, Type.CHOW)
        act(game, 3, Type.PASS)
        act(game, 2, Type.PONG)
        assertEquals(2, game.turn)
        assertEquals(Meld.Type.TRIPLET, game.player(2).melds.single().type())
        assertTrue(game.player(0).discards.isEmpty())
        assertTrue(game.decisions().single().actions.none { it.type == Type.CONCEALED_KONG || it.type == Type.ADDED_KONG })
        game.checkConservation()
    }

    @Test fun concealedKongConsumesTailAndIncreasesReserve() {
        val game = fixture(mapOf(0 to listOf(0, 0, 0, 0)), tail = listOf(136, 33))
        val before = game.wall.drawable
        act(game, 0, Type.CONCEALED_KONG, 0)
        assertEquals(1, game.wall.kongs)
        assertEquals(17, game.wall.reserve)
        assertEquals(before - 3, game.wall.drawable)
        assertEquals(Meld.Type.CONCEALED_QUAD, game.player(0).melds.single().type())
        assertEquals(14, game.player(0).concealed.size)
        assertTrue(FlowerTile.SPRING in game.player(0).flowers)
        game.checkConservation()
    }

    @Test fun initialEightFlowersCanWinWithSixteenOrdinaryTiles() {
        val game = fixture(mapOf(1 to (136..143).toList()))
        assertEquals(TaiwanGame.Phase.FINISHED, game.phase)
        val result = game.settlement!!
        assertEquals(1, result.winner)
        assertEquals(TaiwanRules.Pattern.EIGHT_FLOWERS, result.flowerScore!!.award.pattern)
        assertNull(result.score)
        assertEquals(16, game.player(1).concealed.size)
        assertEquals(3, result.transfers.size)
        game.checkConservation()
    }

    @Test fun openKongReplacementCanWinAndNondealerSelfDrawChargesThree() {
        val caller = listOf(0, 0, 0, 3, 4, 5, 9, 10, 11, 18, 19, 20, 24, 25, 26, 31)
        val game = fixture(mapOf(0 to listOf(0), 1 to caller), tail = listOf(31))
        act(game, 0, Type.DISCARD, 0)
        act(game, 1, Type.OPEN_KONG)
        for (d in game.decisions().toList()) act(game, d.seat, Type.PASS)
        assertEquals(Meld.Type.OPEN_QUAD, game.player(1).melds.single().type())
        act(game, 1, Type.WIN)
        val result = game.settlement!!
        assertEquals(3, result.transfers.size)
        assertTrue(result.score!!.awards.any { it.pattern == TaiwanRules.Pattern.REPLACEMENT_WIN })
        assertTrue(result.score.awards.any { it.pattern == TaiwanRules.Pattern.SELF_DRAW })
        assertEquals(listOf(1, 0, 0), result.transfers.map { it.dealerTai })
        game.checkConservation()
    }

    @Test fun addedKongRemainsAPongUntilRobberyResolves() {
        fun pending(): TaiwanGame {
            val robber = listOf(1, 2, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 31, 31)
            val game = fixture(mapOf(0 to listOf(0), 1 to listOf(0, 0, 30), 2 to robber),
                draws = listOf(32, 32, 32, 0), tail = listOf(33))
            act(game, 0, Type.DISCARD, 0)
            act(game, 1, Type.PONG)
            for (d in game.decisions().toList()) act(game, d.seat, Type.PASS)
            act(game, 1, Type.DISCARD, 30)
            pass(game)
            repeat(3) { act(game, game.turn, Type.DISCARD, 32); pass(game) }
            assertEquals(1, game.turn)
            act(game, 1, Type.ADDED_KONG)
            assertEquals(Meld.Type.TRIPLET, game.player(1).melds.single().type())
            assertEquals(0, game.wall.kongs)
            game.checkConservation()
            return game
        }
        val robbed = pending()
        act(robbed, 2, Type.WIN)
        for (d in robbed.decisions().toList()) act(robbed, d.seat, Type.PASS)
        assertEquals(2, robbed.settlement!!.winner)
        assertEquals(1, robbed.settlement!!.supplier)
        assertEquals(Meld.Type.TRIPLET, robbed.player(1).melds.single().type())
        assertTrue(robbed.settlement!!.score!!.awards.any { it.pattern == TaiwanRules.Pattern.ROBBING_KONG })
        assertEquals(0, robbed.wall.kongs)
        val completed = pending()
        pass(completed)
        assertEquals(Meld.Type.ADDED_QUAD, completed.player(1).melds.single().type())
        assertEquals(1, completed.wall.kongs)
        assertEquals(1, completed.turn)
        completed.checkConservation()
    }

    @Test fun seventhFlowerAndOpponentEighthRouteToTheLoneFlowerHolder() {
        val initial = fixture(mapOf(1 to (136..142).toList(), 2 to listOf(143)))
        assertEquals(1, initial.settlement!!.winner)
        assertEquals(2, initial.settlement!!.transfers.single().from)
        assertNull(initial.settlement!!.score)
        val opponent = fixture(mapOf(1 to (136..142).toList()), draws = listOf(32, 143))
        act(opponent, 0, Type.DISCARD)
        pass(opponent)
        act(opponent, 1, Type.DISCARD, 32)
        val before = opponent.wall.drawable
        pass(opponent)
        assertEquals(1, opponent.settlement!!.winner)
        assertEquals(2, opponent.settlement!!.transfers.single().from)
        assertEquals(before - 1, opponent.wall.drawable) // The opponent does not replace the eighth flower.
        assertEquals(16, opponent.player(1).concealed.size)
        opponent.checkConservation()
    }

    @Test fun reservePoliciesAndFinalReplacementPermissionAreExplicit() {
        val r = TaiwanRules(pocket.name, pocket.values, pocket.exclusions, pocket.sources, pocket.flowers,
            pocket.flowerSets, pocket.pinfu, pocket.replacements, pocket.taiLimit, TaiwanRules.Reserve.FIXED_SIXTEEN)
        for (rules in listOf(pocket, r)) {
            val wall = TaiwanWall(Tile.standard144Set(), TaiwanOpening(0, listOf(1, 1, 1)), rules)
            while (wall.drawable > 2) assertNotNull(wall.draw())
            assertTrue(wall.canKong())
            wall.completeKong()
            assertNotNull(wall.replace())
            assertEquals(if (rules.reserve == TaiwanRules.Reserve.FIXED_SIXTEEN) 1 else 0, wall.drawable)
            while (wall.draw() != null) { /* consume the last ordinary permission */ }
            assertNull(wall.replace())
            assertFalse(wall.canKong())
            assertEquals(wall.reserve, wall.remaining().size)
        }
    }

    @Test fun flowerCapIsAllocatedOnceAndEachPayerOwesOneBase() {
        val capped = TaiwanRules("CAPPED_FLOWERS", pocket.values, pocket.exclusions, pocket.sources, pocket.flowers,
            pocket.flowerSets, pocket.pinfu, pocket.replacements, 4, payment = TaiwanRules.Payment(base = 10, perTai = 2))
        val game = fixture(mapOf(0 to listOf(30), 1 to wait.take(10) + (136..141), 2 to listOf(143)),
            draws = listOf(142), tail = wait.drop(10) + listOf(32, 33), rules = capped)
        act(game, 0, Type.DISCARD, 30)
        pass(game)
        val result = game.settlement!!
        assertEquals(1, result.winner)
        assertNotNull(result.score)
        assertEquals(4, result.flowerScore!!.tai)
        assertEquals(8 + result.score!!.rawTai, result.flowerScore.rawTai)
        assertEquals(listOf(0, 4, 0), result.transfers.map { it.handTai })
        assertEquals(listOf(12L, 18L, 10L), result.transfers.map { it.amount })
        assertEquals(0L, result.deltas.sum())
        game.checkConservation()
    }

    @Test fun finalDrawableFlowerCannotConsumeTheReserve() {
        val game = fixture(mapOf(1 to listOf(136, 137), 2 to listOf(138, 139), 3 to listOf(140, 141, 142)),
            tail = (0..6).toList(), lastDraw = 143)
        while (game.phase != TaiwanGame.Phase.FINISHED) {
            if (game.phase == TaiwanGame.Phase.REACTION) pass(game) else act(game, game.turn, Type.DISCARD)
        }
        assertNull(game.settlement!!.winner)
        assertEquals(16, game.wall.remaining().size)
        assertEquals(8, (0..3).sumOf { game.player(it).flowers.size })
        assertEquals(1, game.settlement!!.nextContinuation)
        game.checkConservation()
    }

    @Test fun readyDiscardsMergeEquivalentCopiesButKeepDifferentFacesAndPhysicalDiscards() {
        val hand = (0..11).toList() + listOf(27, 27, 27, 28, 28)
        for (preset in TaiwanPreset.entries) for (kind in hand.distinct()) {
            val game = fixture(mapOf(0 to hand), rules = preset.rules())
            val decision = game.decisions().single()
            val discards = decision.actions.filter { it.type == Type.DISCARD }
            val ready = decision.actions.filter { it.type == Type.READY_DISCARD }
            assertEquals(game.player(0).concealed.toSet(), discards.map { it.tiles.single() }.toSet())
            assertEquals(hand.distinct().sorted(), ready.map { Tile.kind(it.tiles.single()) }.sorted())
            val index = decision.actions.indexOfFirst { it.type == Type.READY_DISCARD && Tile.kind(it.tiles.single()) == kind }
            val tile = decision.actions[index].tiles.single()
            game.submit(0, decision.token, index)
            assertFalse(tile in game.player(0).concealed)
            assertEquals(hand.count { it == kind } - 1, game.player(0).concealed.count { Tile.kind(it) == kind })
            game.checkConservation()
        }
    }

    @Test fun readyLocksDiscardsAndStaleOrInventedActionsAreAtomic() {
        val game = fixture(mapOf(0 to wait + 30), draws = listOf(31, 31, 31, 32))
        val old = game.decisions().single()
        val before = game.player(0).concealed
        assertThrows(IllegalArgumentException::class.java) { game.submit(1, old.token, 0) }
        assertThrows(IllegalArgumentException::class.java) { game.submit(0, old.token, old.actions.size) }
        assertEquals(before, game.player(0).concealed)
        act(game, 0, Type.READY_DISCARD, 30)
        assertThrows(IllegalArgumentException::class.java) { game.submit(0, old.token, 0) }
        pass(game)
        repeat(3) {
            val seat = game.turn
            act(game, seat, Type.DISCARD, 31)
            pass(game)
        }
        assertEquals(0, game.turn)
        val discards = game.decisions().single().actions.filter { it.type == Type.DISCARD }
        assertEquals(1, discards.size)
        assertEquals(32, Tile.kind(discards.single().tiles.single()))
        assertTrue(game.decisions().single().actions.none { it.type == Type.READY_DISCARD || it.type == Type.CONCEALED_KONG })
    }

    @Test fun passingBlocksRonAndSelfDrawUntilNonWinningDiscard() {
        val game = fixture(mapOf(0 to listOf(33), 1 to wait), draws = listOf(33))
        act(game, 0, Type.DISCARD, 33)
        assertTrue(game.decisions().single { it.seat == 1 }.actions.any { it.type == Type.WIN })
        pass(game)
        assertTrue(game.player(1).passedWin)
        assertTrue(game.decisions().single().actions.none { it.type == Type.WIN })
        act(game, 1, Type.DISCARD, 0)
        assertFalse(game.player(1).passedWin)
        game.checkConservation()
    }

    @Test fun seededGamesRunToSettlementAndPreserveStockForBothProfiles() {
        for (preset in TaiwanPreset.entries) for (seed in 0L..7L) {
            val a = TaiwanGame.shuffled(seed, preset.rules())
            val b = TaiwanGame.shuffled(seed, preset.rules())
            assertEquals((0..3).map { a.player(it).concealed }, (0..3).map { b.player(it).concealed })
            var steps = 0
            while (a.phase != TaiwanGame.Phase.FINISHED) {
                assertTrue(++steps < 1000)
                val d = a.decisions().first()
                val index = d.actions.indexOfFirst { it.type == Type.WIN }.takeIf { it >= 0 }
                    ?: d.actions.indexOfFirst { it.type == if (a.phase == TaiwanGame.Phase.REACTION) Type.PASS else Type.DISCARD }
                a.submit(d.seat, d.token, index)
            }
            assertEquals(0L, a.settlement!!.deltas.sum())
            a.checkConservation()
            if (a.settlement!!.winner == null) {
                assertEquals(0, a.wall.drawable)
                assertEquals(a.opening.dealer, a.settlement!!.nextDealer)
                assertEquals(1, a.settlement!!.nextContinuation)
            }
        }
    }
}
