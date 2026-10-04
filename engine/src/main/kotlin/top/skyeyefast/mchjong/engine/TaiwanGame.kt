package top.skyeyefast.mchjong.engine

import top.skyeyefast.mchjong.engine.TaiwanAction.Type

/** Independent deterministic single-hand engine. Only issued decisions can mutate play. */
class TaiwanGame(
    val rules: TaiwanRules,
    val opening: TaiwanOpening,
    stock: List<Int>,
    val roundWind: Int = Tile.EAST,
    val continuation: Int = 0,
) {
    enum class Phase { TURN, REACTION, FINISHED }
    private class Player {
        val hand = mutableListOf<Int>()
        val melds = mutableListOf<Meld>()
        val flowers = mutableListOf<FlowerTile>()
        val river = mutableListOf<Int>()
        var ready = TaiwanWinContext.Ready.NONE
        var passed = false
        var discards = 0
    }
    private data class Offered(val seat: Int, val tile: Int, val addedMeld: Int? = null)
    private val players = List(4) { Player() }
    val wall = TaiwanWall(stock, opening, rules)
    private val stockIds = stock.toSet()
    var phase = Phase.TURN
        private set
    var turn: Int = opening.dealer
        private set
    var settlement: TaiwanSettlement? = null
        private set
    private var token = 0L
    private var issued = emptyList<TaiwanDecision>()
    private val responses = mutableMapOf<Int, TaiwanAction>()
    private var offered: Offered? = null
    private var drawn = Tile.ABSENT
    private var origin = TaiwanWinContext.DrawOrigin.ORDINARY
    private var turnWaits = emptySet<Int>()
    private var calls = 0
    private var draws = 0

    init {
        require(roundWind in Tile.EAST..Tile.NORTH && continuation in 0..1_000_000)
        repeat(4) {
            repeat(4) { offset -> repeat(4) { players[seat(offset)].hand += checkNotNull(wall.draw()) } }
        }
        drawn = checkNotNull(wall.draw())
        players[turn].hand += drawn
        initialFlowers()
        if (settlement == null) {
            turnWaits = waitsWithoutDraw(turn)
            publishTurn()
        }
        checkConservation()
    }

    private fun seat(offset: Int) = (opening.dealer + offset) % 4
    fun player(seat: Int): TaiwanPlayerState {
        require(seat in 0..3)
        val p = players[seat]
        return TaiwanPlayerState(seat, p.hand.sortedWith(Tile.ORDER), p.melds, p.flowers, p.river, p.ready, p.passed)
    }
    fun decisions(): List<TaiwanDecision> = java.util.List.copyOf(issued.filter { it.seat !in responses })

    /** Stale tokens, wrong seats and invented indices fail before any mutation. */
    fun submit(seat: Int, decisionToken: Long, actionIndex: Int) {
        val decision = decisions().singleOrNull { it.seat == seat && it.token == decisionToken }
        require(decision != null && actionIndex in decision.actions.indices) { "Not an issued action" }
        val action = decision.actions[actionIndex]
        if (phase == Phase.REACTION) {
            responses[seat] = action
            if (responses.size == issued.size) resolve()
        } else takeTurn(action)
        checkConservation()
    }

    /** Also useful to hosts/tests: aliases such as drawn/offered never own a second physical copy. */
    fun checkConservation() {
        val all = wall.remaining() + players.flatMap { p -> p.hand + p.melds.flatMap { it.tiles() } + p.flowers.map { it.id() } + p.river }
        check(all.size == stockIds.size && all.toSet() == stockIds) { "Physical tile conservation failed" }
        if (phase != Phase.FINISHED) for ((s, p) in players.withIndex()) {
            val expected = if (phase == Phase.TURN && s == turn || offered?.addedMeld != null && offered?.seat == s) 17 else 16
            check(p.hand.size + 3 * p.melds.size == expected) { "Invalid structural hand at seat $s" }
            check(p.hand.none(Tile::isFlower))
        }
    }

    private fun initialFlowers() {
        // Funtown: replace only flowers held at the start of each player's pass, then cycle again.
        var changed: Boolean
        do {
            changed = false
            repeat(4) { offset ->
                val s = seat(offset)
                val p = players[s]
                val flowers = p.hand.filter(Tile::isFlower)
                if (flowers.isNotEmpty()) changed = true
                for (flower in flowers) {
                    p.hand.remove(flower)
                    p.flowers += checkNotNull(FlowerTile.of(flower))
                    val tile = checkNotNull(wall.replace()) // At most eight flowers in a complete opening stock.
                    p.hand += tile
                    if (s == opening.dealer) { drawn = tile; origin = TaiwanWinContext.DrawOrigin.FLOWER_REPLACEMENT }
                }
            }
        } while (changed)
        // Initial flowers are settled only after all seats complete their replacement rounds.
        for (offset in 0..3) {
            val s = seat(offset)
            if (ownFlowerVictory(s, initial = true)) return
        }
    }

    private fun context(s: Int, method: TaiwanWinContext.Method): TaiwanWinContext {
        val p = players[s]
        val wind = Tile.EAST + (s - opening.dealer + 4) % 4
        val openingWin = if (calls != 0 || p.ready != TaiwanWinContext.Ready.NONE) TaiwanWinContext.Opening.NONE else when {
            method == TaiwanWinContext.Method.SELF_DRAW && s == opening.dealer && draws == 0 -> TaiwanWinContext.Opening.HEAVENLY
            method == TaiwanWinContext.Method.SELF_DRAW && s != opening.dealer && p.discards == 0 -> TaiwanWinContext.Opening.EARTHLY
            method == TaiwanWinContext.Method.DISCARD && s != opening.dealer &&
                players[opening.dealer].discards == 1 && players[checkNotNull(offered).seat].discards == 1 -> TaiwanWinContext.Opening.HUMAN
            else -> TaiwanWinContext.Opening.NONE
        }
        return TaiwanWinContext(method, wind, roundWind, wind - 26, p.flowers.toSet(),
            if (method == TaiwanWinContext.Method.SELF_DRAW) origin else TaiwanWinContext.DrawOrigin.ORDINARY,
            method != TaiwanWinContext.Method.ROBBING_KONG && wall.drawable == 0, openingWin, p.ready)
    }

    private fun ordinaryScore(s: Int, tile: Int, method: TaiwanWinContext.Method): TaiwanHandAnalyzer.Score? {
        val p = players[s]
        if (p.passed) return null
        val hand = if (method == TaiwanWinContext.Method.SELF_DRAW) p.hand - tile else p.hand
        return TaiwanHandAnalyzer.score(hand, p.melds, s, tile, context(s, method), rules)
    }

    private fun waitsWithoutDraw(s: Int): Set<Int> = TaiwanHandAnalyzer.waits(players[s].hand - drawn, players[s].melds, s)

    private fun publishTurn() {
        phase = Phase.TURN
        offered = null
        responses.clear()
        val p = players[turn]
        val actions = mutableListOf<TaiwanAction>()
        if (drawn != Tile.ABSENT && ordinaryScore(turn, drawn, TaiwanWinContext.Method.SELF_DRAW) != null) actions += TaiwanAction(Type.WIN)
        val discards = if (p.ready == TaiwanWinContext.Ready.NONE) p.hand.sorted() else listOf(drawn)
        for (tile in discards) {
            actions += TaiwanAction(Type.DISCARD, listOf(tile))
            if (p.ready == TaiwanWinContext.Ready.NONE && TaiwanHandAnalyzer.waits(p.hand - tile, p.melds, turn).isNotEmpty()) {
                actions += TaiwanAction(Type.READY_DISCARD, listOf(tile))
            }
        }
        if (drawn != Tile.ABSENT && p.ready == TaiwanWinContext.Ready.NONE && wall.canKong()) {
            p.hand.groupBy(Tile::kind).values.filter { it.size == 4 }.forEach { actions += TaiwanAction(Type.CONCEALED_KONG, it.sorted()) }
            p.melds.filter { it.type() == Meld.Type.TRIPLET && it.kind() == Tile.kind(drawn) }.forEach {
                actions += TaiwanAction(Type.ADDED_KONG, listOf(drawn))
            }
        }
        issued = listOf(TaiwanDecision(++token, turn, actions))
    }

    private fun takeTurn(action: TaiwanAction) {
        val p = players[turn]
        if (action.type != Type.WIN && issued.single().actions.any { it.type == Type.WIN }) p.passed = true
        when (action.type) {
            Type.WIN -> finish(turn, null, checkNotNull(ordinaryScore(turn, drawn, TaiwanWinContext.Method.SELF_DRAW)))
            Type.DISCARD, Type.READY_DISCARD -> {
                val tile = action.tiles.single()
                if (Tile.kind(tile) !in turnWaits) p.passed = false
                if (action.type == Type.READY_DISCARD) {
                    p.ready = when {
                        calls == 0 && p.discards == 0 && turn == opening.dealer -> TaiwanWinContext.Ready.HEAVENLY
                        calls == 0 && p.discards == 0 -> TaiwanWinContext.Ready.EARTHLY
                        else -> TaiwanWinContext.Ready.ORDINARY
                    }
                }
                p.hand.remove(tile)
                p.river += tile
                p.discards++
                drawn = Tile.ABSENT
                react(Offered(turn, tile))
            }
            Type.CONCEALED_KONG -> {
                p.hand.removeAll(action.tiles.toSet())
                p.melds += Meld(Meld.Type.CONCEALED_QUAD, action.tiles, turn, Tile.ABSENT)
                calls++
                wall.completeKong()
                drawReplacement(TaiwanWinContext.DrawOrigin.KONG_REPLACEMENT)
            }
            Type.ADDED_KONG -> {
                val index = p.melds.indexOfFirst { it.type() == Meld.Type.TRIPLET && it.kind() == Tile.kind(drawn) }
                react(Offered(turn, drawn, index))
            }
            else -> error("Not a turn action")
        }
    }

    private fun react(offer: Offered) {
        offered = offer
        phase = Phase.REACTION
        responses.clear()
        val id = ++token
        issued = (1..3).map { distance ->
            val s = (offer.seat + distance) % 4
            val p = players[s]
            val actions = mutableListOf(TaiwanAction(Type.PASS))
            val method = if (offer.addedMeld == null) TaiwanWinContext.Method.DISCARD else TaiwanWinContext.Method.ROBBING_KONG
            if (ordinaryScore(s, offer.tile, method) != null) actions += TaiwanAction(Type.WIN)
            if (offer.addedMeld == null && p.ready == TaiwanWinContext.Ready.NONE && wall.drawable > 0) {
                val kind = Tile.kind(offer.tile)
                val same = p.hand.filter { Tile.kind(it) == kind }.sorted()
                if (same.size >= 2) actions += TaiwanAction(Type.PONG, same.take(2))
                if (same.size == 3 && wall.canKong()) actions += TaiwanAction(Type.OPEN_KONG, same)
                if (distance == 1 && kind < 27) for (start in (kind - 2)..kind) {
                    if (start < 0 || start / 9 != kind / 9 || start % 9 > 6) continue
                    val needed = (start..start + 2).filter { it != kind }.map { k -> p.hand.firstOrNull { Tile.kind(it) == k } }
                    if (needed.all { it != null }) actions += TaiwanAction(Type.CHOW, needed.filterNotNull())
                }
            }
            TaiwanDecision(id, s, actions)
        }
    }

    private fun resolve() {
        val offer = checkNotNull(offered)
        for (decision in issued) if (decision.actions.any { it.type == Type.WIN } && responses.getValue(decision.seat).type != Type.WIN) {
            players[decision.seat].passed = true
        }
        // Issued order is distance from supplier; response arrival never changes priority.
        val winner = issued.firstOrNull { responses.getValue(it.seat).type == Type.WIN }?.seat
        if (winner != null) {
            val method = if (offer.addedMeld == null) TaiwanWinContext.Method.DISCARD else TaiwanWinContext.Method.ROBBING_KONG
            val score = checkNotNull(ordinaryScore(winner, offer.tile, method))
            if (offer.addedMeld == null) players[offer.seat].river.remove(offer.tile) else players[offer.seat].hand.remove(offer.tile)
            players[winner].hand += offer.tile
            finish(winner, offer.seat, score)
            return
        }
        if (offer.addedMeld != null) {
            val p = players[offer.seat]
            val meld = p.melds[offer.addedMeld]
            p.hand.remove(offer.tile)
            p.melds[offer.addedMeld] = Meld(Meld.Type.ADDED_QUAD, meld.tiles() + offer.tile, meld.fromSeat(), meld.calledTile())
            p.passed = false
            calls++
            wall.completeKong()
            turn = offer.seat
            drawReplacement(TaiwanWinContext.DrawOrigin.KONG_REPLACEMENT)
            return
        }
        val caller = issued.filter { responses.getValue(it.seat).type in setOf(Type.PONG, Type.OPEN_KONG) }.firstOrNull()
            ?: issued.firstOrNull { responses.getValue(it.seat).type == Type.CHOW }
        if (caller != null) {
            turn = caller.seat
            val p = players[turn]
            val action = responses.getValue(turn)
            // A call's pre-call waits define the non-winning-discard reset, just as a draw does.
            turnWaits = TaiwanHandAnalyzer.waits(p.hand, p.melds, turn)
            p.hand.removeAll(action.tiles.toSet())
            players[offer.seat].river.remove(offer.tile)
            val type = when (action.type) { Type.PONG -> Meld.Type.TRIPLET; Type.CHOW -> Meld.Type.SEQUENCE; else -> Meld.Type.OPEN_QUAD }
            p.melds += Meld(type, action.tiles + offer.tile, offer.seat, offer.tile)
            calls++
            drawn = Tile.ABSENT
            if (action.type == Type.OPEN_KONG) {
                wall.completeKong()
                drawReplacement(TaiwanWinContext.DrawOrigin.KONG_REPLACEMENT)
            } else publishTurn()
        } else {
            turn = (offer.seat + 1) % 4
            val p = players[turn]
            turnWaits = TaiwanHandAnalyzer.waits(p.hand, p.melds, turn)
            draws++
            draw(wall.draw(), TaiwanWinContext.DrawOrigin.ORDINARY)
        }
    }

    private fun drawReplacement(drawOrigin: TaiwanWinContext.DrawOrigin) {
        turnWaits = TaiwanHandAnalyzer.waits(players[turn].hand, players[turn].melds, turn)
        draw(wall.replace(), drawOrigin)
    }

    private fun draw(first: Int?, drawOrigin: TaiwanWinContext.DrawOrigin) {
        val p = players[turn]
        var tile = first
        origin = drawOrigin
        while (tile != null && Tile.isFlower(tile)) {
            p.flowers += checkNotNull(FlowerTile.of(tile))
            val robber = (0..3).firstOrNull { it != turn && players[it].flowers.size == 7 }
            if (robber != null && rules.values.getValue(TaiwanRules.Pattern.SEVEN_ROBS_ONE) > 0) {
                val special = checkNotNull(TaiwanHandAnalyzer.flowerWin(players[robber].hand, players[robber].melds, robber,
                    Tile.ABSENT, context(robber, TaiwanWinContext.Method.SELF_DRAW), TaiwanHandAnalyzer.FlowerEvent.SEVEN_ON_OPPONENT_FLOWER, rules))
                finish(robber, turn, null, special, turn)
                return
            }
            origin = TaiwanWinContext.DrawOrigin.FLOWER_REPLACEMENT
            tile = wall.replace()
        }
        if (tile == null) { finish(null, null, null); return }
        drawn = tile
        p.hand += tile
        if (!ownFlowerVictory(turn, initial = false)) publishTurn()
    }

    private fun ownFlowerVictory(s: Int, initial: Boolean): Boolean {
        val p = players[s]
        val other = (0..3).firstOrNull { it != s && players[it].flowers.isNotEmpty() }
        val eight = p.flowers.size == 8
        if (!eight && (p.flowers.size != 7 || other == null)) return false
        val pattern = if (eight) TaiwanRules.Pattern.EIGHT_FLOWERS else TaiwanRules.Pattern.SEVEN_ROBS_ONE
        if (rules.values.getValue(pattern) == 0) return false
        val initialSixteen = initial && s != opening.dealer
        val event = when {
            initialSixteen && eight -> TaiwanHandAnalyzer.FlowerEvent.EIGHT_AFTER_INITIAL_REPLACEMENT
            initialSixteen -> TaiwanHandAnalyzer.FlowerEvent.SEVEN_AFTER_INITIAL_REPLACEMENT
            eight -> TaiwanHandAnalyzer.FlowerEvent.EIGHT_AFTER_REPLACEMENT
            else -> TaiwanHandAnalyzer.FlowerEvent.SEVEN_AFTER_REPLACEMENT
        }
        // A dealer's initially held eighth flower can be replaced before a later ordinary packet tile.
        val winning = if (initialSixteen) Tile.ABSENT else drawn
        val c = context(s, TaiwanWinContext.Method.SELF_DRAW)
        val flowerContext = TaiwanWinContext(c.method, c.seatWind, c.roundWind, c.flowerNumber, c.flowers,
            TaiwanWinContext.DrawOrigin.FLOWER_REPLACEMENT, c.lastTile, c.opening, c.ready)
        val special = checkNotNull(TaiwanHandAnalyzer.flowerWin(if (initialSixteen) p.hand else p.hand - winning,
            p.melds, s, winning, flowerContext, event, rules))
        finish(s, if (eight) null else other, special.handScore, special, if (eight) null else other)
        return true
    }

    private fun finish(winner: Int?, supplier: Int?, score: TaiwanHandAnalyzer.Score?,
                       flowers: TaiwanHandAnalyzer.FlowerScore? = null, flowerPayer: Int? = null) {
        val transfers = mutableListOf<TaiwanSettlement.Transfer>()
        if (winner != null) {
            val ordinaryTai = if (flowers == null) score?.tai ?: 0 else minOf(score?.rawTai ?: 0, maxOf(0, flowers.tai - minOf(flowers.award.tai, flowers.tai)))
            val flowerTai = flowers?.let { minOf(it.award.tai, it.tai) } ?: 0
            for (payer in 0..3) if (payer != winner) {
                val ordinaryPays = score != null && (flowers != null || supplier == null || supplier == payer)
                val flowerPays = flowers != null && (flowerPayer == null || flowerPayer == payer)
                if (!ordinaryPays && !flowerPays) continue
                val tai = (if (ordinaryPays) ordinaryTai else 0) + (if (flowerPays) flowerTai else 0)
                val dealerTai = if (payer == opening.dealer || winner == opening.dealer) rules.payment.dealerTai + continuation * rules.payment.repeatTai else 0
                val amount = Math.addExact(rules.payment.base, Math.multiplyExact(rules.payment.perTai, (tai.toLong() + dealerTai)))
                transfers += TaiwanSettlement.Transfer(payer, winner, rules.payment.base, tai, dealerTai, amount)
            }
        }
        val retain = winner == null || winner == opening.dealer
        settlement = TaiwanSettlement(winner, supplier, score, flowers, transfers,
            if (retain) opening.dealer else (opening.dealer + 1) % 4, if (retain) continuation + 1 else 0)
        phase = Phase.FINISHED
        issued = emptyList()
        responses.clear()
        offered = null
    }

    companion object {
        @JvmStatic fun shuffled(seed: Long, rules: TaiwanRules = TaiwanPreset.POCKET_COMMON.rules(), dealer: Int = 0,
                                roundWind: Int = Tile.EAST, continuation: Int = 0): TaiwanGame {
            val random = java.util.Random(seed)
            val stock = (if (rules.flowers == TaiwanRules.Flowers.NONE) Tile.set(false, RedFives.NONE) else Tile.standard144Set()).toMutableList()
            java.util.Collections.shuffle(stock, random)
            return TaiwanGame(rules, TaiwanOpening(dealer, List(3) { random.nextInt(6) + 1 }), stock, roundWind, continuation)
        }
    }
}
