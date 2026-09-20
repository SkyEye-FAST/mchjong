package top.skyeyefast.mchjong.engine

import java.time.Instant

/**
 * Tenhou /6 JSON interchange, mlog 2.3 (not compressed XML .mjlog).
 * Protocol reference: https://github.com/Equim-chan/tensoul/blob/main/convert.js
 * Serialization belongs to the application. Payments are recorded, never re-scored here.
 */
object TenhouReplay {
    @JvmStatic
    fun export(match: ReplayMatch): Map<String, Any> {
        require(match.hands.isNotEmpty()) { "There are no completed hands to export" }
        val root = linkedMapOf<String, Any>()
        root["ver"] = "2.3"
        root["ref"] = match.id.toString()
        root["title"] = listOf("MChjong · ${match.rules.name()}", Instant.ofEpochMilli(match.startedAt).toString())
        root["name"] = List(4) { seat ->
            if (seat < match.rules.players()) match.participants[nativeSeat(match, seat)].name else ""
        }
        root["ratingc"] = "PF${match.rules.players()}"
        root["lobby"] = 0
        root["dan"] = List(4) { "" }
        root["rate"] = List(4) { 0 }
        root["sx"] = List(4) { "" }
        root["rule"] = mapOf<String, Any>(
            "disp" to buildString {
                if (match.rules.sanma()) append("三")
                append(if (match.rules.matchLength() == 1) "東" else "南")
                if (match.rules.kuitan()) append("喰")
                if (match.redFives != RedFives.NONE) append("赤")
                append(" · ${match.rules.minHan()}飜縛り · ${match.rules.name()}")
            },
            "aka51" to if (match.rules.sanma()) 0 else match.redFives.count(0),
            "aka52" to match.redFives.count(1),
            "aka53" to match.redFives.count(2),
        )
        root["log"] = match.hands.map { hand(match, it) }
        if (match.complete) {
            val last = match.hands.last()
            val score = mutableListOf<Number>()
            for (seat in 0 until 4) {
                if (seat < match.rules.players()) {
                    val nativeSeat = nativeSeat(match, seat)
                    score.add(last.finalSeats[nativeSeat].points())
                    score.add(last.finalScores[nativeSeat])
                } else {
                    score.add(0)
                    score.add(0)
                }
            }
            root["sc"] = score
        }
        return root
    }

    @JvmStatic
    fun tile(physical: Int): Int {
        val kind = Tile.kind(physical)
        return if (Tile.red(physical)) 51 + kind / 9 else (kind / 9 + 1) * 10 + kind % 9 + 1
    }

    private fun nativeSeat(match: ReplayMatch, external: Int): Int =
        (external + match.initialDealer) % match.rules.players()

    private fun externalSeat(match: ReplayMatch, nativeSeat: Int): Int =
        Math.floorMod(nativeSeat - match.initialDealer, match.rules.players())

    private fun faces(physical: List<Int>): List<Int> = physical.map(::tile)

    private fun rotate(match: ReplayMatch, values: List<Int>): List<Int> =
        List(4) { seat -> if (seat < match.rules.players()) values[nativeSeat(match, seat)] else 0 }

    private fun hand(match: ReplayMatch, hand: ReplayHand): List<Any> {
        val result = mutableListOf<Any>()
        val players = match.rules.players()
        result.add(listOf(hand.round / players * 4 + hand.round % players, hand.honba, hand.sticks))
        result.add(rotate(match, hand.initialPoints))
        result.add(faces(hand.dora))
        result.add(faces(hand.ura))
        val incoming = MutableList(players) { mutableListOf<Any>() }
        val outgoing = MutableList(players) { mutableListOf<Any>() }
        for (event in hand.events) {
            if (event.kind == ReplayHand.Kind.DORA || event.kind == ReplayHand.Kind.RIICHI) continue
            val draws = incoming[event.seat]
            val discards = outgoing[event.seat]
            when (event.kind) {
                ReplayHand.Kind.DRAW -> draws.add(tile(event.tile))
                ReplayHand.Kind.DISCARD -> {
                    val symbol = if (event.tsumogiri) 60 else tile(event.tile)
                    discards.add(if (event.riichi) "r$symbol" else symbol)
                }
                ReplayHand.Kind.NUKI -> discards.add("f44")
                ReplayHand.Kind.MELD -> {
                    val meld = event.meld!!
                    val who = externalSeat(match, event.seat)
                    val from = externalSeat(match, meld.fromSeat())
                    when (meld.type()) {
                        Meld.Type.CHI, Meld.Type.PON -> draws.add(call(meld.type(), who, from, meld.tiles(), meld.calledTile()))
                        Meld.Type.OPEN_KAN -> {
                            draws.add(call(meld.type(), who, from, meld.tiles(), meld.calledTile()))
                            discards.add(0)
                        }
                        Meld.Type.CLOSED_KAN -> {
                            val tiles = faces(meld.tiles()).sorted()
                            discards.add("${tiles[0]}${tiles[1]}${tiles[2]}a${tiles[3]}")
                        }
                        Meld.Type.ADDED_KAN -> {
                            val pon = meld.tiles().toMutableList()
                            require(pon.remove(event.tile)) { "Missing added kan tile" }
                            discards.add(
                                call(Meld.Type.PON, who, from, pon, meld.calledTile())
                                    .replace("p", "k${tile(event.tile)}"),
                            )
                        }
                    }
                }
                ReplayHand.Kind.DORA, ReplayHand.Kind.RIICHI -> throw IllegalStateException("Unexpected replay event")
            }
        }
        for (seat in 0 until 4) {
            if (seat >= players) {
                repeat(3) { result.add(emptyList<Any>()) }
            } else {
                val nativeSeat = nativeSeat(match, seat)
                result.add(faces(hand.initialHands[nativeSeat]))
                result.add(incoming[nativeSeat])
                result.add(outgoing[nativeSeat])
            }
        }
        result.add(result(match, hand))
        return result
    }

    /** Marker position describes the source seat, and the following tile is the claimed tile. */
    @JvmStatic
    fun call(type: Meld.Type, who: Int, from: Int, tiles: List<Int>, called: Int): String {
        val owned = tiles.toMutableList()
        require(owned.remove(called)) { "Missing called tile" }
        owned.sortWith(Tile.ORDER)
        val tokens = owned.map { tile(it).toString() }.toMutableList()
        val direction = Math.floorMod(who - from - 1, 4)
        require(type == Meld.Type.CHI || direction <= 2) { "A meld cannot call from itself" }
        val index = when {
            type == Meld.Type.CHI -> 0
            type == Meld.Type.OPEN_KAN && direction == 2 -> 3
            else -> direction
        }
        val marker = when (type) {
            Meld.Type.CHI -> "c"
            Meld.Type.PON -> "p"
            else -> "m"
        }
        tokens.add(index, marker + tile(called))
        return tokens.joinToString("")
    }

    private fun result(match: ReplayMatch, hand: ReplayHand): List<Any> {
        val result = mutableListOf<Any>()
        if (hand.wins.isEmpty()) {
            result.add(
                when (hand.result) {
                    "exhaustive" -> "流局"
                    "nagashi" -> "流し満貫"
                    "nine_terminals" -> "九種九牌"
                    "four_winds" -> "四風連打"
                    "four_riichi" -> "四家立直"
                    "four_kans" -> "四開槓"
                    "triple_ron" -> "三家和"
                    else -> throw IllegalArgumentException("Unknown completed hand result: ${hand.result}")
                },
            )
            result.add(rotate(match, hand.deltas))
            return result
        }
        result.add("和了")
        for (win in hand.wins) {
            result.add(rotate(match, win.deltas))
            val details = mutableListOf<Any>()
            val who = externalSeat(match, win.seat)
            details.add(who)
            details.add(if (win.from < 0) who else externalSeat(match, win.from))
            details.add(if (win.pao < 0) who else externalSeat(match, win.pao))
            details.add(score(win.score, win.from < 0, win.seat == hand.dealer))
            for (yaku in win.yaku) {
                val value = if (yaku.yakuman) "役満" else "${yaku.han}飜"
                details.add("${yakuName(yaku.name, hand, win.seat, match.rules.players())}($value)")
            }
            if (win.dora > 0) details.add("ドラ(${win.dora}飜)")
            if (win.ura > 0) details.add("裏ドラ(${win.ura}飜)")
            if (win.redDora > 0) details.add("赤ドラ(${win.redDora}飜)")
            if (win.nukiDora > 0) details.add("抜きドラ(${win.nukiDora}飜)")
            result.add(details)
        }
        return result
    }

    private fun score(score: HandScore, tsumo: Boolean, dealer: Boolean): String {
        val basic = score.ron() / if (dealer) 6 else 4
        val prefix = if (basic >= 8000) {
            when {
                score.yakuman() == 0 -> "数え役満"
                score.yakuman() > 1 -> "${score.yakuman()}倍役満"
                else -> "役満"
            }
        } else {
            when (basic) {
                6000 -> "三倍満"
                4000 -> "倍満"
                3000 -> "跳満"
                2000 -> "満貫"
                else -> "${score.fu()}符${score.han()}飜"
            }
        }
        return prefix + if (!tsumo) {
            "${score.ron()}点"
        } else if (dealer) {
            "${score.tsumoChild()}点∀"
        } else {
            "${score.tsumoChild()}-${score.tsumoDealer()}点"
        }
    }

    private fun yakuName(name: String, hand: ReplayHand, seat: Int, players: Int): String = when (name) {
        "SelfWind" -> "自風 " + "東南西北"[Math.floorMod(seat - hand.dealer, players)]
        "RoundWind" -> "場風 " + "東南西北"[hand.round / players]
        "Tsumo" -> "門前清自摸和"
        "Pinhu" -> "平和"
        "Tanyao" -> "断幺九"
        "Ipe" -> "一盃口"
        "Haku" -> "役牌 白"
        "Hatsu" -> "役牌 發"
        "Chun" -> "役牌 中"
        "Sanshoku" -> "三色同順"
        "Ittsu" -> "一気通貫"
        "Chanta" -> "混全帯幺九"
        "Chitoi" -> "七対子"
        "Toitoi" -> "対々和"
        "Sananko" -> "三暗刻"
        "Honroto" -> "混老頭"
        "Sandoko" -> "三色同刻"
        "Sankantsu" -> "三槓子"
        "Shosangen" -> "小三元"
        "Honitsu" -> "混一色"
        "Junchan" -> "純全帯幺九"
        "Ryanpe" -> "二盃口"
        "Chinitsu" -> "清一色"
        "Kokushi" -> "国士無双"
        "Suanko" -> "四暗刻"
        "Daisangen" -> "大三元"
        "Tsuiso" -> "字一色"
        "Shousushi" -> "小四喜"
        "Lyuiso" -> "緑一色"
        "Chinroto" -> "清老頭"
        "Sukantsu" -> "四槓子"
        "Churen" -> "九蓮宝燈"
        "Daisushi" -> "大四喜"
        "ChurenNineWaiting" -> "純正九蓮宝燈"
        "SuankoTanki" -> "四暗刻単騎"
        "KokushiThirteenWaiting" -> "国士無双十三面待ち"
        "Richi" -> "立直"
        "Ippatsu" -> "一発"
        "Rinshan" -> "嶺上開花"
        "Chankan" -> "槍槓"
        "Haitei" -> "海底摸月"
        "Houtei" -> "河底撈魚"
        "WRichi" -> "ダブル立直"
        "Tenhou" -> "天和"
        "Chihou" -> "地和"
        "Renhou" -> "人和"
        else -> throw IllegalArgumentException("Unknown scoring yaku: $name")
    }
}
