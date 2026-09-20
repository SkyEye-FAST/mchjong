package top.skyeyefast.mchjong.engine

/** A finished hand, with physical tile identities and the original, authoritative payments. */
@JvmRecord
data class ReplayHand(
    val number: Int,
    val round: Int,
    val dealer: Int,
    val honba: Int,
    val sticks: Int,
    val initialPoints: List<Int>,
    val initialHands: List<List<Int>>,
    val initialDora: List<Int>,
    val events: List<Event>,
    val finalSeats: List<TableView.Seat>,
    val wins: List<Win>,
    val result: String,
    val deltas: List<Int>,
    val dora: List<Int>,
    val ura: List<Int>,
    val finalScores: List<Double>,
    val finalRanks: List<Int>,
) {
    enum class Kind { DRAW, DISCARD, MELD, NUKI, RIICHI, DORA }

    /** Uncommitted kan/north declarations are retained when they are robbed. */
    @JvmRecord
    data class Event(
        val kind: Kind,
        val seat: Int,
        val tile: Int,
        val meld: Meld?,
        val tsumogiri: Boolean,
        val riichi: Boolean,
        val committed: Boolean,
    ) {
        init {
            require(seat >= (if (kind == Kind.DORA) -1 else 0) && seat <= 3) { "Invalid replay seat" }
            if (kind != Kind.MELD && kind != Kind.RIICHI) Tile.kind(tile)
            if (kind == Kind.MELD) java.util.Objects.requireNonNull(meld)
        }

        internal fun commit(): Event = copy(committed = true)
    }

    @JvmRecord
    data class Yaku(val name: String, val han: Int, val yakuman: Boolean) {
        init {
            require(name.length <= 128 && han in 0..26) { "Invalid recorded yaku" }
        }
    }

    @JvmRecord
    data class Win(
        val seat: Int,
        val from: Int,
        val tile: Int,
        val score: HandScore,
        val deltas: List<Int>,
        val pao: Int,
        val honba: Int,
        val sticks: Int,
        val yaku: List<Yaku>,
        val dora: Int,
        val ura: Int,
        val redDora: Int,
        val nukiDora: Int,
    ) {
        internal fun withDeposit(amount: Int): Win {
            val updated = deltas.toMutableList()
            updated[seat] = updated[seat] + amount * 1000
            return copy(deltas = java.util.List.copyOf(updated), sticks = amount)
        }
    }

    init {
        val players = initialHands.size
        require(
            players in 3..4 && initialPoints.size == players && finalSeats.size == players &&
                dealer in 0 until players && round >= 0 && honba >= 0 && sticks >= 0 &&
                events.size <= 1024 && dora.size <= 5 && ura.size <= 5 && initialDora.size == 1,
        ) { "Invalid replay hand" }
        for (hand in initialHands) {
            require(hand.size == 13) { "Initial hands need thirteen tiles" }
            hand.forEach(Tile::kind)
        }
        require(
            number >= 1 && round < players * 4 && deltas.size == players && wins.size <= players &&
                (finalScores.isEmpty() || finalScores.size == players) &&
                (finalRanks.isEmpty() || finalRanks.size == players),
        ) { "Invalid replay result" }
        initialDora.forEach(Tile::kind)
        dora.forEach(Tile::kind)
        ura.forEach(Tile::kind)
        require(finalScores.none { !it.isFinite() } && finalRanks.none { it !in 1..players }) { "Invalid final ranking" }
        for (event in events) {
            require(event.seat < players) { "Inactive replay seat" }
            if (event.kind == Kind.MELD) validateMeld(event.meld!!, players)
        }
        for (seat in finalSeats) {
            require(
                seat.name().length <= 128 && seat.hand().size <= 14 && seat.melds().size <= 4 &&
                    seat.river().size <= 100 && seat.norths().size <= 4 && seat.drawn() >= Tile.ABSENT,
            ) { "Invalid replay seat contents" }
            seat.hand().forEach(Tile::kind)
            seat.norths().forEach(Tile::kind)
            seat.river().forEach { Tile.kind(it.tile()) }
            seat.melds().forEach { validateMeld(it, players) }
            if (seat.drawn() != Tile.ABSENT) Tile.kind(seat.drawn())
        }
        for (win in wins) {
            require(
                win.seat in 0 until players && win.from in -1 until players && win.pao in -1 until players &&
                    win.deltas.size == players && win.yaku.size <= 64 && win.score.yaku().size <= 64,
            ) { "Invalid replay win" }
            Tile.kind(win.tile)
            require(win.score.yaku().none { it == null || it.length > 128 }) { "Invalid replay yaku name" }
        }
    }

    private fun validateMeld(meld: Meld, players: Int) {
        java.util.Objects.requireNonNull(meld.type())
        require(meld.fromSeat() in 0 until players && meld.tiles().size == if (meld.kan()) 4 else 3) {
            "Invalid replay meld"
        }
        meld.tiles().forEach(Tile::kind)
        require(meld.closed() || meld.tiles().contains(meld.calledTile())) { "Invalid called tile" }
    }
}
