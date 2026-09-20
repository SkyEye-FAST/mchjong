package top.skyeyefast.mchjong.engine

import java.util.UUID

/** Contains completed hands only. No RNG seed, future wall, or active hand is exported. */
@JvmRecord
data class ReplayMatch(
    val id: UUID,
    val tableId: UUID,
    val startedAt: Long,
    val updatedAt: Long,
    val rules: RuleConfig,
    val initialDealer: Int,
    val participants: List<Participant>,
    val hands: List<ReplayHand>,
    val complete: Boolean,
    val redFives: RedFives,
) {
    @JvmRecord
    data class Participant(val id: UUID, val name: String, val bot: Boolean) {
        init {
            require(name.isNotBlank() && name.length <= 128) { "Invalid replay player name" }
        }
    }

    @JvmRecord
    data class Header(
        val id: UUID,
        val updatedAt: Long,
        val rules: RuleConfig,
        val hands: Int,
        val complete: Boolean,
        val names: List<String>,
    ) {
        init {
            require(hands in 1..1024 && names.size == rules.players() && names.none { it.isBlank() || it.length > 128 }) {
                "Invalid replay header"
            }
        }
    }

    @JvmRecord
    data class Index(
        val page: Int,
        val search: String,
        val oldestFirst: Boolean,
        val matches: List<Header>,
        val more: Boolean,
    ) {
        init {
            require(page in 0..100_000 && search.length <= 80 && matches.size <= 12) { "Invalid replay page" }
        }
    }

    init {
        require(rules.allows(redFives)) { "Invalid replay red-five composition" }
        require(
            participants.size == rules.players() && initialDealer in participants.indices && hands.size <= 1024 &&
                (!complete || hands.isNotEmpty()),
        ) { "Invalid replay match" }
        require(hands.all { it.initialHands.size == rules.players() }) { "Replay rules mismatch" }
        if (complete) {
            require(hands.last().finalScores.size == rules.players() && hands.last().finalRanks.size == rules.players()) {
                "Missing final standings"
            }
        }
    }

    fun header(): Header = Header(id, updatedAt, rules, hands.size, complete, java.util.List.copyOf(participants.map { it.name }))

    fun permits(player: UUID): Boolean = participants.any { !it.bot && it.id == player }

    fun append(hand: ReplayHand, ended: Boolean): ReplayMatch =
        ReplayMatch(
            id,
            tableId,
            startedAt,
            System.currentTimeMillis(),
            rules,
            initialDealer,
            participants,
            java.util.List.copyOf(hands + hand),
            ended,
            redFives,
        )
}
