package top.skyeyefast.mchjong.engine

/** A read-only timeline. It never sends game actions or exposes any still-playing hand. */
object ReplayPlayback {
    @JvmRecord
    data class Frame(
        val seats: List<TableView.Seat>,
        val dora: List<Int>,
        val cursor: Int,
        val steps: Int,
        val rawCursor: Int,
        val settled: Boolean,
        val event: ReplayHand.Event?,
    )

    @JvmRecord
    data class Timeline(val frames: List<Frame>) {
        init {
            require(frames.size >= 2 && frames.first().cursor == 0 && frames.last().settled) { "Invalid replay timeline" }
        }
    }

    private class Seat(hand: List<Int>, var points: Int) {
        val hand = hand.toMutableList()
        val melds = mutableListOf<Meld>()
        val river = mutableListOf<Discard>()
        val norths = mutableListOf<Int>()
        var drawn = Tile.ABSENT
        var riichi = false
        var nextSideways = false

        fun remove(tile: Int) {
            require(hand.remove(tile)) { "Replay uses an unowned tile" }
        }
    }

    @JvmStatic
    fun at(match: ReplayMatch, handIndex: Int, requested: Int): Frame {
        val hand = match.hands[handIndex]
        val steps = hand.events.size + 1
        val cursor = requested.coerceIn(0, steps)
        if (cursor == steps) {
            return Frame(java.util.List.copyOf(hand.finalSeats), java.util.List.copyOf(hand.dora), cursor, steps, hand.events.size, true, null)
        }
        val seats = MutableList(match.rules.players()) { Seat(hand.initialHands[it], hand.initialPoints[it]) }
        val dora = hand.initialDora.toMutableList()
        for (i in 0 until cursor) {
            val event = hand.events[i]
            if (event.kind == ReplayHand.Kind.DORA) {
                dora += event.tile
                continue
            }
            val seat = seats[event.seat]
            when (event.kind) {
                ReplayHand.Kind.DRAW -> {
                    require(event.tile !in seat.hand && seat.hand.size < 14) { "Invalid replay draw" }
                    seat.hand += event.tile
                    seat.drawn = event.tile
                }
                ReplayHand.Kind.DISCARD -> {
                    seat.remove(event.tile)
                    seat.drawn = Tile.ABSENT
                    seat.river += Discard(event.tile, event.riichi || seat.nextSideways, false, event.tsumogiri)
                    seat.nextSideways = false
                }
                ReplayHand.Kind.RIICHI -> {
                    seat.riichi = true
                    seat.points -= 1000
                }
                ReplayHand.Kind.NUKI -> if (event.committed) {
                    seat.remove(event.tile)
                    seat.norths += event.tile
                    seat.drawn = Tile.ABSENT
                }
                ReplayHand.Kind.MELD -> {
                    if (!event.committed) continue
                    val meld = event.meld!!
                    if (meld.type() == Meld.Type.ADDED_KAN) {
                        seat.remove(event.tile)
                        val pon = seat.melds.indexOfLast { it.type() == Meld.Type.PON && it.kind() == meld.kind() }
                        require(pon >= 0) { "Added kan has no pon" }
                        seat.melds[pon] = meld
                    } else {
                        for (tile in meld.tiles()) if (meld.closed() || tile != meld.calledTile()) seat.remove(tile)
                        seat.melds += meld
                        if (!meld.closed()) {
                            val source = seats[meld.fromSeat()]
                            require(source.river.isNotEmpty() && source.river.last().tile() == meld.calledTile()) {
                                "Meld does not claim the last discard"
                            }
                            val discard = source.river.last()
                            source.river[source.river.lastIndex] = discard.markCalled()
                            source.nextSideways = discard.riichi()
                        }
                    }
                    seat.drawn = Tile.ABSENT
                }
                ReplayHand.Kind.DORA -> error("Unexpected replay event")
            }
        }
        val view = seats.mapIndexed { i, seat ->
            seat.hand.sortWith(Tile.ORDER)
            if (seat.drawn >= 0 && seat.hand.remove(seat.drawn)) seat.hand += seat.drawn
            val player = match.participants[i]
            TableView.Seat(
                hand.finalSeats[i].entityBot(),
                player.name,
                true,
                player.bot,
                false,
                seat.points,
                seat.hand,
                seat.drawn,
                seat.melds,
                seat.river,
                seat.norths,
                seat.riichi,
                true,
                seat.riichi && hand.finalSeats[i].doubleRiichi(),
            )
        }
        return Frame(
            java.util.List.copyOf(view),
            java.util.List.copyOf(dora),
            cursor,
            steps,
            cursor,
            false,
            if (cursor == 0) null else hand.events[cursor - 1],
        )
    }

    /**
     * Compiles raw recorder events into user-facing replay steps. Riichi payment and dora reveal events are folded into
     * the action immediately before them, so navigation follows meaningful table actions instead of recorder internals.
     */
    @JvmStatic
    fun timeline(match: ReplayMatch, handIndex: Int): Timeline {
        val hand = match.hands[handIndex]
        val frames = mutableListOf(at(match, handIndex, 0))
        var eventIndex = 0
        while (eventIndex < hand.events.size) {
            val event = hand.events[eventIndex]
            if (event.kind == ReplayHand.Kind.RIICHI || event.kind == ReplayHand.Kind.DORA) {
                eventIndex++
                continue
            }
            var rawCursor = eventIndex + 1
            while (rawCursor < hand.events.size &&
                (hand.events[rawCursor].kind == ReplayHand.Kind.RIICHI || hand.events[rawCursor].kind == ReplayHand.Kind.DORA)
            ) rawCursor++
            frames += at(match, handIndex, rawCursor).copy(event = event)
            eventIndex = rawCursor
        }
        frames += at(match, handIndex, Int.MAX_VALUE)
        val last = frames.lastIndex
        return Timeline(java.util.List.copyOf(frames.mapIndexed { index, frame -> frame.copy(cursor = index, steps = last) }))
    }
}
