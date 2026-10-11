package top.skyeyefast.mchjong.engine

import mahjongutils.hora.HoraHandPattern
import mahjongutils.hora.RegularHoraHandPattern
import mahjongutils.hora.ChitoiHoraHandPattern
import mahjongutils.models.MentsuType
import mahjongutils.models.TatsuType

/** The library retains the winning concealed group last; ron opens only that triplet for scoring. */
internal object RiichiYakuFacts {
    fun of(pattern: HoraHandPattern, complete: List<Int>, melds: List<Meld>, extra: List<String>): YakuFacts {
        val groups = melds.map { YakuFacts.Group(if (it.quad()) "quad" else if (it.type() == Meld.Type.SEQUENCE) "sequence" else "triplet", it.tiles().minOf(Tile::kind), it.closed()) }.toMutableList()
        var pair = -1
        var wait = "special"
        val shape = when (pattern) {
            is RegularHoraHandPattern -> {
                pair = Tile.parseKind(pattern.jyantou.toString())
                wait = when (pattern.agariTatsu?.type) {
                    null -> "pair"; TatsuType.Ryanmen -> "two_sided"; TatsuType.Kanchan -> "closed"
                    TatsuType.Penchan -> "edge"; TatsuType.Toitsu -> "triplet"
                }
                pattern.menzenMentsu.forEachIndexed { index, group ->
                    groups += YakuFacts.Group(if (group.type == MentsuType.Shuntsu) "sequence" else "triplet",
                        Tile.parseKind(group.tile.toString()), pattern.tsumo || wait != "triplet" || index != pattern.menzenMentsu.lastIndex)
                }
                "regular"
            }
            is ChitoiHoraHandPattern -> "seven_pairs"
            else -> "thirteen_orphans"
        }
        val flags = mutableSetOf(if (pattern.tsumo) "self_draw" else "discard")
        for (name in extra) when (name) {
            "Richi" -> flags += "riichi"
            "WRichi" -> { flags += "riichi"; flags += "double_riichi" }
            "Ippatsu" -> flags += "ippatsu"
            "Chankan" -> flags += "robbing_kong"
            "Rinshan" -> flags += "kong_replacement"
            "Haitei" -> flags += "last_draw"
            "Houtei" -> flags += "last_discard"
            "Tenhou" -> flags += "heavenly_win"
            "Chihou" -> flags += "earthly_win"
            "Renhou" -> flags += "human_win"
        }
        val physical = complete + melds.flatMap { it.tiles() }
        return YakuFacts(shape, melds.all(Meld::closed), physical.map(Tile::kind), groups, pair, wait, flags, physical.count(Tile::red), 0)
    }
}
