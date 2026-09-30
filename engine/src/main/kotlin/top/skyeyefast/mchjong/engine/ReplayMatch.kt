package top.skyeyefast.mchjong.engine

import java.util.UUID

/** Completed hands only. Rule-specific records live in their own payloads. */
@JvmRecord
data class ReplayMatch(
    val id: UUID,
    val tableId: UUID,
    val startedAt: Long,
    val updatedAt: Long,
    val participants: List<Participant>,
    val variant: MahjongVariant,
    val complete: Boolean,
    val riichi: RiichiReplay?,
    val mcr: McrReplay?,
) {
    @JvmRecord
    data class Participant(val id: UUID, val name: String, val bot: Boolean) {
        init { require(name.isNotBlank() && name.length <= 128) { "Invalid replay player name" } }
    }

    @JvmRecord
    data class Header(
        val id: UUID,
        val startedAt: Long,
        val updatedAt: Long,
        val variant: MahjongVariant,
        val riichiRules: RiichiRules?,
        val hands: Int,
        val complete: Boolean,
        val names: List<String>,
        val finalScores: List<Double>,
        val finalRanks: List<Int>,
    ) {
        init {
            require(
                variant != MahjongVariant.SICHUAN && startedAt > 0 && updatedAt >= startedAt && hands in 1..1024 &&
                    (variant == MahjongVariant.RIICHI) == (riichiRules != null) &&
                    names.size == (riichiRules?.players() ?: 4) &&
                    names.none { it.isBlank() || it.length > 128 } &&
                    (finalScores.isEmpty() || finalScores.size == names.size) &&
                    (finalRanks.isEmpty() || finalRanks.size == names.size) &&
                    finalScores.none { !it.isFinite() } && finalRanks.none { it !in 1..names.size } &&
                    (!complete || finalScores.size == names.size && finalRanks.size == names.size),
            ) { "Invalid replay header" }
        }
    }

    @JvmRecord
    data class Index(val page: Int, val search: String, val oldestFirst: Boolean, val matches: List<Header>, val more: Boolean) {
        init { require(page in 0..100_000 && search.length <= 80 && matches.size <= 12) { "Invalid replay page" } }
    }

    init {
        require(variant != MahjongVariant.SICHUAN && startedAt > 0 && updatedAt >= startedAt &&
            (variant == MahjongVariant.RIICHI) == (riichi != null) &&
            (variant == MahjongVariant.MCR) == (mcr != null) &&
            participants.size == (riichi?.rules?.players() ?: 4) &&
            participants.map { it.id }.distinct().size == participants.size &&
            handCount() <= 1024 && (!complete || handCount() > 0)) { "Invalid replay match" }
        if (complete) {
            require(riichi?.hands?.lastOrNull()?.let { it.finalScores.size == participants.size && it.finalRanks.size == participants.size }
                ?: (mcr!!.hands.size == 16)) { "Missing final replay standings" }
        }
    }

    fun handCount(): Int = riichi?.hands?.size ?: mcr!!.hands.size

    fun header(): Header {
        val scores = if (!complete) emptyList() else riichi?.hands?.last()?.finalScores
            ?: mcr!!.hands.last().finalPoints.map(Int::toDouble)
        val ranks = if (!complete) emptyList() else riichi?.hands?.last()?.finalRanks ?: run {
            val points = mcr!!.hands.last().finalPoints
            points.map { score -> 1 + points.count { it > score } }
        }
        return Header(id, startedAt, updatedAt, variant, riichi?.rules, handCount(), complete,
            java.util.List.copyOf(participants.map { it.name }), java.util.List.copyOf(scores), java.util.List.copyOf(ranks))
    }

    fun permits(player: UUID): Boolean = participants.any { !it.bot && it.id == player }

    fun appendRiichi(hand: ReplayHand, ended: Boolean): ReplayMatch = copy(
        updatedAt = System.currentTimeMillis(), complete = ended,
        riichi = requireNotNull(riichi).copy(hands = java.util.List.copyOf(riichi.hands + hand)),
    )

    fun appendMcr(hand: McrReplayHand, ended: Boolean): ReplayMatch = copy(
        updatedAt = System.currentTimeMillis(), complete = ended,
        mcr = requireNotNull(mcr).copy(hands = java.util.List.copyOf(mcr.hands + hand)),
    )
}
