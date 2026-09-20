package top.skyeyefast.mchjong.engine

import top.skyeyefast.mchjong.engine.ReplayHand.Kind.DISCARD
import top.skyeyefast.mchjong.engine.ReplayHand.Kind.DORA
import top.skyeyefast.mchjong.engine.ReplayHand.Kind.DRAW
import top.skyeyefast.mchjong.engine.ReplayHand.Kind.MELD
import top.skyeyefast.mchjong.engine.ReplayHand.Kind.NUKI
import top.skyeyefast.mchjong.engine.ReplayHand.Kind.RIICHI

/** Private recorder, persisted alongside the live game and sealed only after settlement. */
internal class ReplayRecorder(game: Game) {
    private val number = game.handNumber
    private val round = game.round
    private val dealer = game.dealer
    private val honba = game.honba
    private val sticks = game.riichiSticks
    private val initialPoints = points(game)
    private val initialHands = game.players.take(game.rules.players()).map { java.util.List.copyOf(it.hand) }
    private val initialDora = java.util.List.copyOf(game.wall.indicators(false))
    private val events = mutableListOf<ReplayHand.Event>()
    private val wins = mutableListOf<ReplayHand.Win>()
    private var pendingDeclaration = -1
    private var indicators = 1

    fun draw(seat: Int, tile: Int) {
        events += ReplayHand.Event(DRAW, seat, tile, null, false, false, true)
    }

    fun discard(seat: Int, tile: Int, drawn: Boolean, riichi: Boolean) {
        events += ReplayHand.Event(DISCARD, seat, tile, null, drawn, riichi, true)
    }

    fun riichi(seat: Int) {
        events += ReplayHand.Event(RIICHI, seat, Tile.ABSENT, null, false, false, true)
    }

    fun call(seat: Int, meld: Meld) {
        events += ReplayHand.Event(MELD, seat, meld.calledTile(), meld, false, false, true)
    }

    fun declare(game: Game, seat: Int, action: Action) {
        val tile = game.lastTile
        val meld = when (action.type()) {
            Action.Type.CLOSED_KAN -> Meld(Meld.Type.CLOSED_KAN, action.tiles(), seat, Tile.ABSENT)
            Action.Type.ADDED_KAN -> {
                val pon = game.players[seat].melds.first {
                    it.type() == Meld.Type.PON && it.kind() == Tile.kind(tile)
                }
                val tiles = pon.tiles().toMutableList()
                tiles += tile
                Meld(Meld.Type.ADDED_KAN, tiles, pon.fromSeat(), pon.calledTile())
            }
            else -> null
        }
        pendingDeclaration = events.size
        events += ReplayHand.Event(if (action.type() == Action.Type.NUKI) NUKI else MELD, seat, tile, meld, false, false, false)
    }

    fun confirmDeclaration() {
        if (pendingDeclaration >= 0) events[pendingDeclaration] = events[pendingDeclaration].commit()
        pendingDeclaration = -1
    }

    fun dora(game: Game) {
        val revealed = game.wall.indicators(false)
        while (indicators < revealed.size) {
            events += ReplayHand.Event(DORA, -1, revealed[indicators++], null, false, false, true)
        }
    }

    fun win(
        game: Game,
        seat: Int,
        from: Int,
        tile: Int,
        score: HandScore,
        before: List<Int>,
        pao: Int,
        bonus: Int,
    ) {
        val changes = List(game.rules.players()) { game.players[it].points - before[it] }
        val player = game.players[seat]
        val all = player.hand.toMutableList()
        if (tile >= 0 && all.size + player.melds.size * 3 == 13) all += tile
        player.melds.forEach { all.addAll(it.tiles()) }
        all.addAll(player.norths)
        val bonuses = score.yakuman() == 0 && !score.yaku().contains("Renhou")
        val red = if (bonuses) all.count(Tile::red) else 0
        val north = if (bonuses) player.norths.size else 0
        var ura = 0
        if (bonuses && game.rules.uraDora() && player.riichi) {
            for (i in 0 until game.wall.revealed) {
                val indicator = game.wall.tiles[game.wall.ura[i]]
                val kind = Tile.doraAfter(Tile.kind(indicator), game.rules.sanma())
                ura += all.count { Tile.kind(it) == kind }
            }
        }
        wins += ReplayHand.Win(
            seat,
            from,
            tile,
            score,
            java.util.List.copyOf(changes),
            pao,
            bonus,
            0,
            if (score.yaku().contains("Nagashi")) emptyList() else HandAnalyzer.yakuValues(score.yaku(), player.closed(), game.rules),
            maxOf(0, score.dora() - red - ura - north),
            ura,
            red,
            north,
        )
    }

    fun awardDeposit(amount: Int) {
        if (wins.isNotEmpty()) wins[0] = wins.first().withDeposit(amount)
    }

    fun finish(game: Game): ReplayHand {
        val publicSeats = game.view(null).seats()
        val allSeats = mutableListOf<TableView.Seat>()
        for (i in 0 until game.rules.players()) {
            val visible = publicSeats[i]
            val player = game.players[i]
            val hand = player.hand.toMutableList()
            hand.sortWith(Tile.ORDER)
            if (player.drawn >= 0 && hand.remove(player.drawn)) hand += player.drawn
            allSeats += TableView.Seat(
                visible.name(),
                visible.occupied(),
                visible.bot(),
                visible.ready(),
                visible.points(),
                hand,
                player.drawn,
                visible.melds(),
                visible.river(),
                visible.norths(),
                visible.riichi(),
                visible.exposed(),
            )
        }
        val ura = mutableListOf<Int>()
        if (game.rules.uraDora() && game.wins.any { game.players[it.seat()].riichi }) {
            for (i in 0 until game.wall.revealed) ura += game.wall.tiles[game.wall.ura[i]]
        }
        return ReplayHand(
            number,
            round,
            dealer,
            honba,
            sticks,
            initialPoints,
            initialHands,
            initialDora,
            java.util.List.copyOf(events),
            java.util.List.copyOf(allSeats),
            java.util.List.copyOf(wins),
            game.result,
            java.util.List.copyOf(game.deltas.subList(0, game.rules.players())),
            java.util.List.copyOf(game.wall.indicators(false)),
            java.util.List.copyOf(ura),
            java.util.List.copyOf(game.finalScores),
            java.util.List.copyOf(game.finalRanks),
        )
    }

    companion object {
        @JvmStatic
        fun points(game: Game): List<Int> = List(game.rules.players()) { game.players[it].points }
    }
}
