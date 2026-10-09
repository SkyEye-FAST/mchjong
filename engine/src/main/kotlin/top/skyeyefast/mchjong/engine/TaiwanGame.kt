package top.skyeyefast.mchjong.engine

import top.skyeyefast.mchjong.engine.TaiwanAction.Type

/** Independent deterministic single-hand engine. Only issued decisions can mutate play. */
class TaiwanGame private constructor(
    val rules: TaiwanRules,
    val opening: TaiwanOpening,
    stock: List<Int>,
    val roundWind: Int,
    val continuation: Int,
    restoredWall: TaiwanWall?,
    deal: Boolean,
) {
    constructor(rules: TaiwanRules, opening: TaiwanOpening, stock: List<Int>, roundWind: Int = Tile.EAST, continuation: Int = 0) :
        this(rules, opening, stock, roundWind, continuation, null, true)
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
    val wall = restoredWall ?: TaiwanWall(stock, opening, rules)
    private val stockIds = TaiwanWall.expected(rules).toSet()
    var phase = Phase.TURN
        private set
    var turn: Int = opening.dealer
        private set
    var settlement: TaiwanSettlement? = null
        private set
    private var token = 1L
    var revision = 1L
        private set
    val decision: Long get() = token
    private var issued = emptyList<TaiwanDecision>()
    private val responses = mutableMapOf<Int, TaiwanAction>()
    private var offered: Offered? = null
    private var drawn = Tile.ABSENT
    private var turnDrawn = Tile.ABSENT
    private var origin = TaiwanWinContext.DrawOrigin.ORDINARY
    private var turnWaits = emptySet<Int>()
    private var calls = 0
    private var draws = 0
    private var outcome: TaiwanGameState.Outcome? = null
    private val openingWall = java.util.List.copyOf(stock)
    private val replaySteps = mutableListOf<TaiwanGameState>()
    /** Passive physical checkpoints; playback executes the engine, never these snapshots. */
    fun replayOpeningWall(): List<Int> = openingWall
    fun replaySteps(): List<TaiwanGameState> = java.util.List.copyOf(replaySteps)
    private fun checkpoint() { replaySteps += snapshot(emptyList()) }

    init {
        require(roundWind in Tile.EAST..Tile.NORTH && continuation in 0..1_000_000)
        if (deal) {
            repeat(4) {
                repeat(4) { offset -> repeat(4) { players[seat(offset)].hand += checkNotNull(wall.draw()) } }
            }
            drawn = checkNotNull(wall.draw())
            players[turn].hand += drawn
            checkpoint()
            initialFlowers()
            if (settlement == null) {
                turnWaits = waitsWithoutDraw(turn)
                publishTurn()
            }
            checkConservation()
            checkpoint()
        }
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
        replaySteps.clear()
        if (phase == Phase.REACTION) {
            responses[seat] = action
            checkpoint()
            if (responses.size == issued.size) resolve()
        } else takeTurn(action)
        revision = Math.addExact(revision, 1)
        checkConservation()
        checkpoint()
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
                    checkpoint()
                    val tile = checkNotNull(wall.replace()) // At most eight flowers in a complete opening stock.
                    p.hand += tile
                    if (s == opening.dealer) { drawn = tile; origin = TaiwanWinContext.DrawOrigin.FLOWER_REPLACEMENT }
                    checkpoint()
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
        turnDrawn = drawn
        phase = Phase.TURN
        offered = null
        responses.clear()
        val p = players[turn]
        val actions = mutableListOf<TaiwanAction>()
        if (drawn != Tile.ABSENT && ordinaryScore(turn, drawn, TaiwanWinContext.Method.SELF_DRAW) != null) actions += TaiwanAction(Type.WIN)
        val discards = if (p.ready == TaiwanWinContext.Ready.NONE) p.hand.sorted() else listOf(drawn)
        val checkedReadyKinds = mutableSetOf<Int>()
        for (tile in discards) {
            actions += TaiwanAction(Type.DISCARD, listOf(tile))
            if (p.ready == TaiwanWinContext.Ready.NONE && checkedReadyKinds.add(Tile.kind(tile)) &&
                TaiwanHandAnalyzer.waits(p.hand - tile, p.melds, turn).isNotEmpty()) {
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
                checkpoint()
                react(Offered(turn, tile))
            }
            Type.CONCEALED_KONG -> {
                p.hand.removeAll(action.tiles.toSet())
                p.melds += Meld(Meld.Type.CONCEALED_QUAD, action.tiles, turn, Tile.ABSENT)
                calls++
                wall.completeKong()
                checkpoint()
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
        checkpoint()
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
            checkpoint()
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
            origin = TaiwanWinContext.DrawOrigin.ORDINARY
            checkpoint()
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
        drawn = Tile.ABSENT
        var tile = first
        origin = drawOrigin
        while (tile != null && Tile.isFlower(tile)) {
            p.flowers += checkNotNull(FlowerTile.of(tile))
            checkpoint()
            val robber = (0..3).firstOrNull { it != turn && players[it].flowers.size == 7 }
            if (robber != null && rules.values.getValue(TaiwanRules.Pattern.SEVEN_ROBS_ONE) > 0) {
                val special = checkNotNull(TaiwanHandAnalyzer.flowerWin(players[robber].hand, players[robber].melds, robber,
                    Tile.ABSENT, context(robber, TaiwanWinContext.Method.SELF_DRAW), TaiwanHandAnalyzer.FlowerEvent.SEVEN_ON_OPPONENT_FLOWER, rules))
                finish(robber, turn, null, special, turn, TaiwanHandAnalyzer.FlowerEvent.SEVEN_ON_OPPONENT_FLOWER)
                return
            }
            origin = TaiwanWinContext.DrawOrigin.FLOWER_REPLACEMENT
            tile = wall.replace()
        }
        if (tile == null) { finish(null, null, null); return }
        drawn = tile
        p.hand += tile
        checkpoint()
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
        finish(s, if (eight) null else other, special.handScore, special, if (eight) null else other, event)
        return true
    }

    private fun finish(winner: Int?, supplier: Int?, score: TaiwanHandAnalyzer.Score?,
                       flowers: TaiwanHandAnalyzer.FlowerScore? = null, flowerPayer: Int? = null,
                       flowerEvent: TaiwanHandAnalyzer.FlowerEvent? = null) {
        token = Math.addExact(token, 1)
        val method = if (flowers != null || supplier == null) TaiwanWinContext.Method.SELF_DRAW else
            if (offered?.addedMeld != null) TaiwanWinContext.Method.ROBBING_KONG else TaiwanWinContext.Method.DISCARD
        val tile = if (winner == null || flowerEvent in setOf(TaiwanHandAnalyzer.FlowerEvent.SEVEN_ON_OPPONENT_FLOWER,
            TaiwanHandAnalyzer.FlowerEvent.EIGHT_AFTER_INITIAL_REPLACEMENT, TaiwanHandAnalyzer.FlowerEvent.SEVEN_AFTER_INITIAL_REPLACEMENT)) Tile.ABSENT
            else if (method == TaiwanWinContext.Method.SELF_DRAW) drawn else checkNotNull(offered).tile
        outcome = TaiwanGameState.Outcome(winner, supplier, tile, method, flowerEvent, flowerPayer,
            if (flowers == null && supplier != null) offered?.let { TaiwanGameState.Offer(it.seat, it.tile, it.addedMeld) } else null)
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
        if (drawn !in players[turn].hand) drawn = Tile.ABSENT
        checkpoint()
    }

    fun save(): TaiwanGameState = snapshot(responses.map { TaiwanGameState.Reply(it.key, TaiwanGameState.Action.of(it.value)) })
    private fun snapshot(replies: List<TaiwanGameState.Reply>): TaiwanGameState = TaiwanGameState(TaiwanGameState.FORMAT, TaiwanGameState.Rules.of(rules),
        TaiwanGameState.Opening(opening.dealer, opening.dice), roundWind, continuation, revision, token, phase, turn,
        wall.save(), players.map { TaiwanGameState.Player(it.hand, it.melds, it.flowers, it.river, it.ready, it.passed, it.discards) },
        drawn, turnDrawn, origin, turnWaits, calls, draws, offered?.let { TaiwanGameState.Offer(it.seat, it.tile, it.addedMeld) },
        replies, outcome, TaiwanGameState.Result.of(settlement))

    fun view(recipient: Int): TaiwanView = view(recipient, true)
    fun view(recipient: Int, allowActions: Boolean): TaiwanView {
        require(recipient in -1..3)
        return TaiwanView(revision, token, TaiwanGameState.Rules.of(rules), TaiwanGameState.Opening(opening.dealer, opening.dice),
            roundWind, continuation, phase, turn, recipient, wall.drawable, wall.reserve,
            wall.physicalSlots().map { if (it == Tile.ABSENT) it else Tile.HIDDEN },
            players.mapIndexed { s, p -> TaiwanView.Seat(p.hand.size, if (s == recipient) p.hand.sortedWith(Tile.ORDER) else emptyList(),
                if (s == recipient) drawn.takeIf { s == turn && it in p.hand } ?: Tile.ABSENT else Tile.ABSENT,
                p.melds.map { if (it.closed() && s != recipient) Meld(it.type(), List(4) { Tile.HIDDEN }, s, Tile.ABSENT) else it },
                p.flowers, p.river, p.ready) },
            offered?.let { TaiwanView.Focus(it.seat, it.tile, it.addedMeld != null) },
            if (allowActions) decisions().singleOrNull { it.seat == recipient }?.actions?.map(TaiwanGameState.Action::of).orEmpty() else emptyList(),
            recipient in responses, if (recipient >= 0) players[recipient].passed else null, TaiwanView.Result.of(settlement))
    }

    companion object {
        @JvmStatic fun restore(state: TaiwanGameState): TaiwanGame {
            val rules = state.rules().restore()
            val opening = state.opening().restore()
            val game = TaiwanGame(rules, opening, emptyList(), state.roundWind(), state.continuation(),
                TaiwanWall.restore(state.wall(), opening, rules), false)
            game.phase = state.phase()
            game.turn = state.turn()
            game.token = state.decision()
            game.revision = state.revision()
            game.drawn = state.drawn()
            game.turnDrawn = state.turnDrawn()
            game.origin = state.origin()
            game.turnWaits = state.turnWaits()
            game.calls = state.calls()
            game.draws = state.draws()
            for ((seat, saved) in state.players().withIndex()) {
                val p = game.players[seat]
                p.hand += saved.hand(); p.melds += saved.melds(); p.flowers += saved.flowers(); p.river += saved.river()
                p.ready = saved.ready(); p.passed = saved.passed(); p.discards = saved.discards()
            }
            game.offered = state.offer()?.let { Offered(it.seat(), it.tile(), it.addedMeld()) }
            game.outcome = state.outcome()
            game.validateRestored(state)
            // Rebuild every action from the position. A response stores a choice, never an action cache.
            if (game.phase == Phase.TURN) game.publishTurn()
            else if (game.phase == Phase.REACTION) {
                game.react(checkNotNull(game.offered))
                for (reply in state.replies()) {
                    require(reply.seat() !in game.responses)
                    val action = game.issued.singleOrNull { it.seat == reply.seat() }?.actions
                        ?.singleOrNull { TaiwanGameState.Action.of(it) == reply.action() }
                    require(action != null) { "Saved response is no longer legal" }
                    game.responses[reply.seat()] = action
                }
            } else game.token = Math.addExact(game.token, 1)
            game.revision = Math.addExact(game.revision, 1)
            game.checkConservation()
            return game
        }
        @JvmStatic fun shuffled(seed: Long, rules: TaiwanRules = TaiwanPreset.POCKET_COMMON.rules(), dealer: Int = 0,
                                roundWind: Int = Tile.EAST, continuation: Int = 0): TaiwanGame {
            val random = java.util.Random(seed)
            val stock = (if (rules.flowers == TaiwanRules.Flowers.NONE) Tile.set(false, RedFives.NONE) else Tile.standard144Set()).toMutableList()
            java.util.Collections.shuffle(stock, random)
            return TaiwanGame(rules, TaiwanOpening(dealer, List(3) { random.nextInt(6) + 1 }), stock, roundWind, continuation)
        }
    }

    private fun validateRestored(state: TaiwanGameState) {
        require(turnDrawn == Tile.ABSENT || turnDrawn in 0..135)
        require((phase == Phase.REACTION) == (offered != null))
        require(phase == Phase.REACTION || state.replies().isEmpty())
        require((phase == Phase.FINISHED) == (state.settlement() != null && outcome != null))
        require(phase == Phase.FINISHED || state.settlement() == null && outcome == null)
        val all = wall.remaining() + players.flatMap { it.hand + it.melds.flatMap(Meld::tiles) + it.flowers.map(FlowerTile::id) + it.river }
        require(all.size == stockIds.size && all.toSet() == stockIds) { "Invalid physical stock" }
        require(wall.kongs == players.sumOf { p -> p.melds.count { it.quad() } })
        require(calls == players.sumOf { p -> p.melds.size + p.melds.count { it.type() == Meld.Type.ADDED_QUAD } })
        val terminal = outcome
        val initialFlower = terminal?.flowerEvent() in setOf(TaiwanHandAnalyzer.FlowerEvent.EIGHT_AFTER_INITIAL_REPLACEMENT,
            TaiwanHandAnalyzer.FlowerEvent.SEVEN_AFTER_INITIAL_REPLACEMENT)
        val tailTakes = players.sumOf { it.flowers.size } + wall.kongs
        val missingReplacement = terminal?.flowerEvent() == TaiwanHandAnalyzer.FlowerEvent.SEVEN_ON_OPPONENT_FLOWER ||
            phase == Phase.FINISHED && terminal?.winner() == null && state.wall().tail() == tailTakes - 1
        require(state.wall().tail() == tailTakes - if (missingReplacement) 1 else 0)
        require(origin != TaiwanWinContext.DrawOrigin.KONG_REPLACEMENT || players[turn].melds.any { it.quad() })
        require(origin != TaiwanWinContext.DrawOrigin.FLOWER_REPLACEMENT || players[turn].flowers.isNotEmpty())
        for ((seat, p) in players.withIndex()) {
            val expected = if (phase != Phase.FINISHED) {
                if (phase == Phase.TURN && seat == turn || offered?.addedMeld != null && seat == turn) 17 else 16
            } else when {
                initialFlower -> if (seat == opening.dealer) 17 else 16
                terminal?.winner() == seat && terminal.tile() != Tile.ABSENT -> 17
                else -> 16
            }
            require(p.hand.size + p.melds.size * 3 == expected)
            require(p.hand.all { it in 0..135 } && p.river.all { it in 0..135 })
            // The adapter remains the single owner of meld structure/provenance validation.
            val base = if (expected == 17) p.hand.dropLast(1) else p.hand
            TaiwanHandAnalyzer.waits(base, p.melds, seat)
            require(p.ready != TaiwanWinContext.Ready.HEAVENLY || seat == opening.dealer)
            require(p.ready != TaiwanWinContext.Ready.EARTHLY || seat != opening.dealer)
            require(p.ready == TaiwanWinContext.Ready.NONE || p.discards > 0)
            if (p.ready != TaiwanWinContext.Ready.NONE && phase != Phase.FINISHED) {
                val readyBase = if (expected == 17) p.hand - drawn else p.hand
                require(TaiwanHandAnalyzer.waits(readyBase, p.melds, seat).isNotEmpty())
            }
            val claims = players.sumOf { other -> other.melds.count { !it.closed() && it.fromSeat() == seat } }
            val ron = if (terminal?.flowerEvent() == null && terminal?.supplier() == seat && terminal.method() == TaiwanWinContext.Method.DISCARD) 1 else 0
            require(p.discards == p.river.size + claims + ron) { "Invalid discard chronology" }
        }
        require(state.wall().front() == 65 + draws || phase == Phase.FINISHED && terminal?.winner() == null && state.wall().front() == 64 + draws)
        require(drawn == Tile.ABSENT || drawn in players[turn].hand)
        offered?.let { offer ->
            require(offer.seat == turn)
            if (offer.addedMeld == null) {
                require(drawn == Tile.ABSENT && players[turn].river.lastOrNull() == offer.tile)
                val beforeDiscard = players[turn].hand + offer.tile
                val base = if (turnDrawn != Tile.ABSENT) {
                    require(turnDrawn in beforeDiscard)
                    beforeDiscard - turnDrawn
                } else {
                    val last = players[turn].melds.lastOrNull()
                    require(last != null && last.type() in setOf(Meld.Type.SEQUENCE, Meld.Type.TRIPLET))
                    beforeDiscard + last.tiles().filter { it != last.calledTile() }
                }
                val melds = if (turnDrawn == Tile.ABSENT) players[turn].melds.dropLast(1) else players[turn].melds
                require(turnWaits == TaiwanHandAnalyzer.waits(base, melds, turn))
            } else {
                require(offer.tile == drawn && players[turn].ready == TaiwanWinContext.Ready.NONE && wall.canKong())
                val meld = players[turn].melds.getOrNull(offer.addedMeld)
                require(meld?.type() == Meld.Type.TRIPLET && meld.kind() == Tile.kind(offer.tile))
                require(turnDrawn == drawn && turnWaits == waitsWithoutDraw(turn))
            }
        }
        if (phase == Phase.TURN) {
            require(turnDrawn == drawn)
            if (drawn != Tile.ABSENT) require(turnWaits == waitsWithoutDraw(turn))
            else {
                val p = players[turn]
                val last = p.melds.lastOrNull()
                require(last != null && last.type() in setOf(Meld.Type.SEQUENCE, Meld.Type.TRIPLET) && p.ready == TaiwanWinContext.Ready.NONE)
                require(turnWaits == TaiwanHandAnalyzer.waits(p.hand + last.tiles().filter { it != last.calledTile() }, p.melds.dropLast(1), turn))
            }
        }
        if (phase == Phase.FINISHED) restoreSettlement(checkNotNull(terminal), checkNotNull(state.settlement()))
    }

    /** Session hand boundaries keep request tokens increasing within one room incarnation. */
    fun rebaseDecision(previous: Long) {
        require(previous >= 0 && previous < Long.MAX_VALUE - 1)
        token = Math.addExact(maxOf(token, previous), 1)
        issued = issued.map { TaiwanDecision(token, it.seat, it.actions) }
    }

    private fun restoreSettlement(end: TaiwanGameState.Outcome, saved: TaiwanGameState.Result) {
        if (end.winner() == null) {
            require(wall.drawable == 0 && end.supplier() == null && end.tile() == Tile.ABSENT && end.flowerEvent() == null && end.flowerPayer() == null && end.winningOffer() == null)
            finish(null, null, null)
        } else {
            val s = end.winner()
            val p = players[s]
            val event = end.flowerEvent()
            if (event == null) {
                require(!p.passed && end.flowerPayer() == null && end.tile() in p.hand)
                if (end.method() == TaiwanWinContext.Method.SELF_DRAW) require(end.supplier() == null && end.winningOffer() == null && s == turn && end.tile() == drawn)
                else {
                    val offer = checkNotNull(end.winningOffer())
                    require(offer.seat() == end.supplier() && offer.seat() == turn && offer.tile() == end.tile())
                    require((end.method() == TaiwanWinContext.Method.ROBBING_KONG) == (offer.addedMeld() != null))
                    if (offer.addedMeld() != null) {
                        val meld = players[turn].melds.getOrNull(offer.addedMeld())
                        require(meld?.type() == Meld.Type.TRIPLET && meld.kind() == Tile.kind(end.tile()))
                        require(wall.canKong() && players[turn].ready == TaiwanWinContext.Ready.NONE && turnDrawn == end.tile())
                    }
                    offered = Offered(offer.seat(), offer.tile(), offer.addedMeld())
                }
                val score = checkNotNull(TaiwanHandAnalyzer.score(p.hand - end.tile(), p.melds, s, end.tile(), context(s, end.method()), rules))
                finish(s, end.supplier(), score)
            } else {
                require(end.method() == TaiwanWinContext.Method.SELF_DRAW && end.winningOffer() == null)
                val eight = event in setOf(TaiwanHandAnalyzer.FlowerEvent.EIGHT_AFTER_REPLACEMENT, TaiwanHandAnalyzer.FlowerEvent.EIGHT_AFTER_INITIAL_REPLACEMENT)
                require(p.flowers.size == if (eight) 8 else 7)
                val payer = if (eight) null else (0..3).single { it != s && players[it].flowers.size == 1 }
                require(end.supplier() == payer && end.flowerPayer() == payer)
                val initial = event in setOf(TaiwanHandAnalyzer.FlowerEvent.EIGHT_AFTER_INITIAL_REPLACEMENT, TaiwanHandAnalyzer.FlowerEvent.SEVEN_AFTER_INITIAL_REPLACEMENT)
                if (initial) require(draws == 0 && calls == 0 && players.all { it.discards == 0 } && s != opening.dealer && end.tile() == Tile.ABSENT)
                else if (event == TaiwanHandAnalyzer.FlowerEvent.SEVEN_ON_OPPONENT_FLOWER) require(end.tile() == Tile.ABSENT && payer == turn)
                else require(end.tile() == drawn && s == turn && end.tile() in p.hand)
                val c = context(s, TaiwanWinContext.Method.SELF_DRAW)
                val fc = TaiwanWinContext(c.method, c.seatWind, c.roundWind, c.flowerNumber, c.flowers,
                    TaiwanWinContext.DrawOrigin.FLOWER_REPLACEMENT, c.lastTile, c.opening, c.ready)
                val flower = checkNotNull(TaiwanHandAnalyzer.flowerWin(p.hand - end.tile(), p.melds, s, end.tile(), fc, event, rules))
                finish(s, payer, flower.handScore, flower, payer, event)
            }
        }
        require(TaiwanGameState.Result.of(settlement) == saved && outcome == end) { "Settlement disagrees with position" }
    }
}
