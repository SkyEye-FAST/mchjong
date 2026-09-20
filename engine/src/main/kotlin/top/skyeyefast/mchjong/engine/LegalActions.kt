package top.skyeyefast.mchjong.engine

import top.skyeyefast.mchjong.engine.Action.Type.*

/** Computes all choices, including alternate red-five consumption, before issuing a decision. */
internal object LegalActions {
    @JvmStatic
    fun onTurn(game: Game, seat: Int): List<Action> {
        val player = game.players[seat]
        val actions = ArrayList<Action>()
        if (player.drawn >= 0 && score(game, seat, player.drawn, true) != null) {
            actions += Action(TSUMO, player.drawn)
        }
        val ordered = ArrayList(player.hand)
        ordered.sortWith(Tile.ORDER)
        for (tile in ordered) {
            if (player.riichi && tile != player.drawn) continue
            if (Tile.kind(tile) !in player.forbiddenDiscards) actions += Action(DISCARD, tile)
        }
        if (!player.canDeclare) return actions.toList()
        if (!player.riichi && player.closed() && game.wall.remaining() >= game.rules.minRiichiWall() &&
            (!game.rules.needsRiichiDeposit() || player.points >= 1000)
        ) {
            val tenpai = HandAnalyzer.tenpaiDiscards(player.hand, player.melds)
            for (tile in ordered) if (Tile.kind(tile) in tenpai) actions += Action(RIICHI, tile)
        }
        if (game.rules.abortiveDraws() && player.firstTurn && game.uninterrupted &&
            player.hand.asSequence().map(Tile::kind).filter(Tile::terminalOrHonor).distinct().count() >= 9
        ) actions += Action(ABORT_NINE)

        if (!game.wall.canReplace()) return actions.toList()
        if (game.rules.sanma()) for (tile in ordered) {
            if (Tile.kind(tile) == Tile.NORTH && (!player.riichi || tile == player.drawn)) actions += Action(NUKI, tile)
        }
        if (game.kanCount() >= 4) return actions.toList()
        for (kind in 0..<34) {
            val matching = matching(player.hand, kind)
            if (matching.size == 4 && (!player.riichi || legalRiichiKan(game, seat, kind, matching))) {
                actions += Action(CLOSED_KAN, matching)
            }
        }
        if (!player.riichi) for (meld in player.melds) if (meld.type() == Meld.Type.PON) {
            for (tile in matching(player.hand, meld.kind())) actions += Action(ADDED_KAN, tile)
        }
        return actions.toList()
    }

    private fun legalRiichiKan(game: Game, seat: Int, kind: Int, quad: List<Int>): Boolean {
        val player = game.players[seat]
        if (player.drawn < 0 || Tile.kind(player.drawn) != kind) return false
        val before = ArrayList(player.hand)
        before.remove(player.drawn)
        val after = ArrayList(player.hand)
        after.removeAll(quad.toSet())
        val melds = ArrayList(player.melds)
        melds += Meld(Meld.Type.CLOSED_KAN, quad, seat, Tile.ABSENT)
        val waits = HandAnalyzer.waits(before, player.melds)
        if (waits != HandAnalyzer.waits(after, melds)) return false
        if (game.rules.riichiKanKeepsMelds() && !HandAnalyzer.riichiKanKeepsMelds(before, player.melds, kind)) return false
        if (game.rules.riichiKanKeepsYaku()) for (wait in waits) for (tsumo in booleanArrayOf(false, true)) {
            val previous = HandAnalyzer.score(
                before, player.melds, wait * 4, tsumo, game.wind(seat), game.round / game.rules.players(),
                0, listOf("Richi"), game.rules
            )
            val next = HandAnalyzer.score(
                after, melds, wait * 4, tsumo, game.wind(seat), game.round / game.rules.players(),
                0, listOf("Richi"), game.rules
            )
            if (previous != null && (next == null || !next.yaku().containsAll(previous.yaku()))) return false
        }
        return true
    }

    @JvmStatic
    fun onReaction(game: Game, seat: Int): List<Action> {
        val player = game.players[seat]
        val actions = ArrayList<Action>()
        val kind = Tile.kind(game.lastTile)
        val waits = HandAnalyzer.waits(player.hand, player.melds)
        if (kind in waits && !player.temporaryFuriten && !player.riichiFuriten &&
            player.river.none { Tile.kind(it.tile()) in waits }
        ) {
            val score = score(game, seat, game.lastTile, false)
            val kokushi = score?.yaku()?.any { it.startsWith("Kokushi") } == true
            val pending = game.pending
            val permitted = pending == null || when (pending.type()) {
                ADDED_KAN -> true
                CLOSED_KAN -> game.rules.robConcealedKan() && kokushi
                NUKI -> game.rules.robNorthWithoutKokushi() || kokushi
                else -> false
            }
            if (score != null && permitted) actions += Action(RON, game.lastTile)
        }
        if (game.pending == null && !player.riichi && game.wall.remaining() > 0 && !game.fourKanAbort) {
            val matching = matching(player.hand, kind)
            for (a in matching.indices) for (b in a + 1..<matching.size) {
                val used = listOf(matching[a], matching[b])
                if (canDiscardAfter(player, used, setOf(kind))) actions += Action(PON, used)
            }
            if (matching.size == 3 && game.wall.canReplace() && game.kanCount() < 4) {
                actions += Action(OPEN_KAN, matching)
            }
            if (!game.rules.sanma() && seat == game.next(game.lastFrom) && kind < 27) {
                for (low in maxOf(kind / 9 * 9, kind - 2)..minOf(kind, kind / 9 * 9 + 6)) {
                    val others = ArrayList<Int>(2)
                    for (tileKind in low..low + 2) if (tileKind != kind) others += tileKind
                    for (first in matching(player.hand, others[0])) for (second in matching(player.hand, others[1])) {
                        val used = listOf(first, second)
                        val action = Action(CHI, used)
                        if (canDiscardAfter(player, used, forbiddenAfterCall(action, game.lastTile))) actions += action
                    }
                }
            }
        }
        if (actions.isNotEmpty()) actions += Action(PASS)
        return actions.toList()
    }

    private fun canDiscardAfter(player: PlayerState, used: List<Int>, forbidden: Set<Int>): Boolean =
        player.hand.any { it !in used && Tile.kind(it) !in forbidden }

    /** Shared by legal generation, call execution and bot simulation. */
    @JvmStatic
    fun forbiddenAfterCall(action: Action, claimed: Int): Set<Int> {
        val called = Tile.kind(claimed)
        val forbidden = HashSet<Int>()
        forbidden += called
        if (action.type() == CHI) {
            val low = minOf(called, action.tiles().minOf(Tile::kind))
            if (called == low && low % 9 < 6) forbidden += low + 3
            if (called == low + 2 && low % 9 > 0) forbidden += low - 1
        }
        return forbidden
    }

    private fun matching(hand: List<Int>, kind: Int): List<Int> = hand.filter { Tile.kind(it) == kind }.sorted()

    @JvmStatic
    fun score(game: Game, seat: Int, tile: Int, tsumo: Boolean): HandScore? {
        val player = game.players[seat]
        val extra = ArrayList<String>()
        if (player.riichi) {
            extra += if (player.doubleRiichi) "WRichi" else "Richi"
            if (game.rules.ippatsu() && player.ippatsu) extra += "Ippatsu"
        }
        if (tsumo) {
            if (player.rinshan) extra += "Rinshan"
            else if (player.lastDraw) extra += "Haitei"
            if (player.firstTurn && game.uninterrupted) extra += if (seat == game.dealer) "Tenhou" else "Chihou"
        } else if (game.pending?.type() == ADDED_KAN) extra += "Chankan"
        else if (game.pending == null && game.wall.remaining() == 0) extra += "Houtei"
        if (!tsumo && game.rules.renhouMangan() && seat != game.dealer && player.firstTurn && game.uninterrupted) {
            extra += "Renhou"
        }
        val dora = HandBonuses.count(
            player.hand, player.melds, player.norths, tile,
            HandBonuses.indicators(game.wall.indicators(game.rules.uraDora() && player.riichi), game.rules.sanma())
        )
        return HandAnalyzer.score(
            player.hand, player.melds, tile, tsumo, game.wind(seat), game.round / game.rules.players(),
            dora, extra, game.rules
        )
    }

    @JvmStatic
    fun formalTenpai(game: Game, seat: Int): Boolean {
        val player = game.players[seat]
        val melds = if (game.rules.formalTenpaiIgnoresMelds()) emptyList() else player.melds
        return HandAnalyzer.waits(player.hand, melds).isNotEmpty()
    }
}
