package top.skyeyefast.mchjong.engine

import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap

/** Payments and match progression are separate from action legality and transport. */
internal object Settlement {
    @JvmStatic
    fun win(game: Game, winners: List<Int>, from: Int, tile: Int) {
        val before = points(game)
        game.wins.clear()
        for ((index, seat) in winners.withIndex()) {
            val score = LegalActions.score(game, seat, tile, from < 0)
                ?: throw IllegalStateException("Settlement requires a legal winning hand")
            game.wins.add(TableView.Win(seat, from, tile, score))
            game.exposed[seat] = true
            val receiveHonba = index == 0
            val winBefore = ReplayRecorder.points(game)
            payWin(game, seat, from, score, if (receiveHonba) game.honba else 0)
            game.recorder?.win(
                game,
                seat,
                from,
                tile,
                score,
                winBefore,
                pao(game, seat, score).keys.firstOrNull() ?: -1,
                if (receiveHonba) game.honba else 0,
            )
        }
        game.players[winners.first()].points += game.riichiSticks * 1000
        game.recorder?.awardDeposit(game.riichiSticks)
        game.riichiSticks = 0
        finish(game, game.dealer in winners, false, false, if (from < 0) "tsumo" else "ron", before)
    }

    private fun payWin(game: Game, winner: Int, from: Int, score: HandScore, honba: Int) {
        val liable = pao(game, winner, score)
        val liableUnits = liable.values.sum()
        val normalUnits = score.yakuman() - liableUnits
        if (score.yakuman() == 0 || normalUnits > 0) {
            val ron = if (score.yakuman() == 0) score.ron() else normalUnits * if (winner == game.dealer) 48000 else 32000
            val dealerPay = if (score.yakuman() == 0) score.tsumoDealer() else normalUnits * 16000
            val childPay = if (score.yakuman() == 0) score.tsumoChild() else normalUnits * if (winner == game.dealer) 16000 else 8000
            if (from >= 0) {
                transfer(game, from, winner, ron)
            } else {
                for (payer in 0 until game.rules.players()) if (payer != winner) {
                    transfer(game, payer, winner, if (payer == game.dealer) dealerPay else childPay)
                }
            }
        }
        for ((payer, units) in liable) {
            var total = units * if (winner == game.dealer) 48000 else 32000
            if (from < 0) {
                if (game.rules.sanma()) total -= units * if (winner == game.dealer) 16000 else 8000
                transfer(game, payer, winner, total)
            } else if (payer == from) {
                transfer(game, from, winner, total)
            } else {
                transfer(game, payer, winner, total / 2)
                transfer(game, from, winner, total / 2)
            }
        }
        val honbaTotal = honba * 100 * (game.rules.players() - 1)
        if (
            liable.isNotEmpty() && !(from >= 0 && game.rules.paoRonHonbaByDiscarder()) &&
            !(from < 0 && game.rules.paoTsumoHonbaShared() && normalUnits > 0)
        ) {
            transfer(game, liable.keys.first(), winner, honbaTotal)
        } else if (from >= 0) {
            transfer(game, from, winner, honbaTotal)
        } else {
            for (payer in 0 until game.rules.players()) if (payer != winner) {
                transfer(game, payer, winner, honba * 100)
            }
        }
    }

    private fun pao(game: Game, seat: Int, score: HandScore): LinkedHashMap<Int, Int> {
        val liable = LinkedHashMap<Int, Int>()
        if (score.yakuman() == 0) return liable
        val player = game.players[seat]
        if (player.dragonPao >= 0 && score.yaku().contains("Daisangen")) merge(liable, player.dragonPao, 1)
        if (player.windPao >= 0 && score.yaku().contains("Daisushi")) {
            merge(liable, player.windPao, if (game.rules.doubleYakuman()) 2 else 1)
        }
        if (player.kanPao >= 0 && score.yaku().contains("Sukantsu")) merge(liable, player.kanPao, 1)
        if (game.rules.wholeHandPao() && liable.isNotEmpty()) {
            val payer = liable.keys.first()
            liable.clear()
            liable[payer] = score.yakuman()
        } else if (!game.rules.compoundYakuman()) {
            var remaining = score.yakuman()
            val entries = liable.entries.iterator()
            while (entries.hasNext()) {
                val entry = entries.next()
                val units = minOf(remaining, entry.value)
                if (units == 0) entries.remove() else entry.setValue(units)
                remaining -= units
            }
        }
        return liable
    }

    private fun merge(values: MutableMap<Int, Int>, key: Int, amount: Int) {
        values[key] = (values[key] ?: 0) + amount
    }

    @JvmStatic
    fun abort(game: Game, reason: String) {
        finish(game, true, true, true, reason, points(game))
    }

    @JvmStatic
    fun exhaustive(game: Game) {
        val before = points(game)
        val tenpai = BooleanArray(4)
        val nagashi = mutableListOf<Int>()
        var count = 0
        for (seat in 0 until game.rules.players()) {
            val player = game.players[seat]
            tenpai[seat] = LegalActions.formalTenpai(game, seat)
            if (tenpai[seat]) count++
            game.exposed[seat] = tenpai[seat]
            if (
                game.rules.nagashiMangan() && player.river.isNotEmpty() &&
                (game.rules.nagashiAllowsCalls() || player.closed()) &&
                player.river.all { !it.called() && Tile.terminalOrHonor(Tile.kind(it.tile())) }
            ) {
                nagashi += seat
            }
        }
        if (nagashi.isNotEmpty()) {
            for (seat in nagashi) {
                val score = HandScore(
                    5,
                    0,
                    0,
                    if (seat == game.dealer) 12000 else 8000,
                    4000,
                    if (seat == game.dealer) 4000 else 2000,
                    listOf("Nagashi"),
                    0,
                )
                game.wins.add(TableView.Win(seat, -1, Tile.ABSENT, score))
                // Nagashi replaces noten payments. It does not take the riichi pot or honba.
                payWin(game, seat, -1, score, 0)
            }
        } else if (count > 0 && count < game.rules.players()) {
            val pool = if (game.rules.sanma()) 2000 else 3000
            for (seat in 0 until game.rules.players()) {
                game.players[seat].points += if (tenpai[seat]) pool / count else -pool / (game.rules.players() - count)
            }
        }
        finish(game, tenpai[game.dealer], true, false, if (nagashi.isEmpty()) "exhaustive" else "nagashi", before)
    }

    private fun transfer(game: Game, payer: Int, recipient: Int, points: Int) {
        if (payer == recipient || points < 0) throw IllegalStateException("Invalid point transfer")
        game.players[payer].points -= points
        game.players[recipient].points += points
    }

    private fun points(game: Game): IntArray = IntArray(game.players.size) { game.players[it].points }

    private fun finish(game: Game, repeats: Boolean, draw: Boolean, abort: Boolean, reason: String, before: IntArray) {
        game.dealerRepeats = repeats
        game.drawResult = draw
        game.abortResult = abort
        game.result = reason
        for (player in game.players) player.ready = false
        val end = matchEnds(game)
        game.newDecision(if (end) Game.Phase.MATCH_END else Game.Phase.HAND_END)
        if (end) finalScores(game)
        game.deltas = ArrayList()
        for (seat in 0 until 4) game.deltas.add(game.players[seat].points - before[seat])
        game.finishReplay()
    }

    private fun matchEnds(game: Game): Boolean {
        val n = game.rules.players()
        if (game.rules.bankruptcy()) {
            for (seat in 0 until n) if (game.players[seat].points < 0) return true
        }
        if (game.round < game.rules.scheduledRounds() - 1 || game.abortResult) return false
        val top = ranking(game).first()
        if (game.dealerRepeats) {
            return game.rules.agariYame() && top == game.dealer && game.players[top].points >= game.rules.targetPoints()
        }
        if (!game.rules.extension()) return true
        return game.players[top].points >= game.rules.targetPoints() || game.round >= game.rules.scheduledRounds() + n - 1
    }

    private fun ranking(game: Game): List<Int> =
        (0 until game.rules.players()).sortedWith(
            compareBy<Int> { -game.players[it].points }
                .thenBy { Math.floorMod(it - game.initialDealer, game.rules.players()) },
        )

    private fun finalScores(game: Game) {
        val ranking = ranking(game)
        val groups = mutableListOf<MutableList<Int>>()
        for (seat in ranking) {
            if (
                groups.isNotEmpty() && game.rules.sharedRanks() &&
                game.players[groups.last().first()].points == game.players[seat].points
            ) {
                groups.last().add(seat)
            } else {
                groups += mutableListOf(seat)
            }
        }
        val top = groups.first()
        if (game.rules.awardFinalDeposits()) {
            for (i in top.indices) {
                game.players[top[i]].points += game.riichiSticks * (10 / top.size + if (i < 10 % top.size) 1 else 0) * 100
            }
            game.riichiSticks = 0
        }
        val floating = ranking.count { game.players[it].points >= game.rules.returnPoints() }
        val bonus = game.rules.placementPoints(floating)
        val uma = bonus.copyOf()
        bonus[0] += (game.rules.returnPoints() - game.rules.startingPoints()) * game.rules.players()
        game.finalScores = ArrayList(Collections.nCopies(game.rules.players(), 0.0))
        game.finalUma = ArrayList(Collections.nCopies(game.rules.players(), 0.0))
        game.finalRanks = ArrayList(Collections.nCopies(game.rules.players(), 0))
        var place = 0
        for (group in groups) {
            val rank = place + 1
            var placement = 0
            var umaPlacement = 0
            for (ignored in group.indices) {
                umaPlacement += uma[place]
                placement += bonus[place++]
            }
            for (i in group.indices) {
                val seat = group[i]
                val share = if (game.rules.roundSharedPlacement()) {
                    (Math.floorDiv(placement / 100, group.size) + if (i < Math.floorMod(placement / 100, group.size)) 1 else 0) / 10.0
                } else {
                    placement / (1000.0 * group.size)
                }
                game.finalRanks[seat] = rank
                game.finalScores[seat] = (game.players[seat].points - game.rules.returnPoints()) / 1000.0 + share
                val umaShare = if (game.rules.roundSharedPlacement()) {
                    (Math.floorDiv(umaPlacement / 100, group.size) + if (i < Math.floorMod(umaPlacement / 100, group.size)) 1 else 0) / 10.0
                } else {
                    umaPlacement / (1000.0 * group.size)
                }
                game.finalUma[seat] = umaShare
                val player = game.players[seat]
                if (game.rules.experienceRewards() && player.id != null && !player.bot) {
                    val experience = Math.round(kotlin.math.abs(umaShare) * 100).toInt() * (if (umaShare < 0) -1 else 1)
                    if (experience > 0 || experience < 0 && game.rules.deductNegativeExperience())
                        game.pendingExperience.merge(player.id, experience, Integer::sum)
                }
            }
        }
    }
}
