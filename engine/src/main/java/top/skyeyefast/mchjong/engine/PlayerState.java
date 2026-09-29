package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;

/** Server-only state. It must never be serialized into a client payload. */
final class PlayerState {
    transient TableSession.Participant member;
    AutoPlay autoPlay = AutoPlay.DEFAULT;
    int points;
    List<Integer> hand = new ArrayList<>();
    List<Meld> melds = new ArrayList<>();
    List<Discard> river = new ArrayList<>();
    List<Integer> norths = new ArrayList<>();
    List<Integer> flowers = new ArrayList<>();
    int drawn = Tile.ABSENT;
    boolean riichi;
    boolean doubleRiichi;
    boolean ippatsu;
    boolean riichiFuriten;
    boolean temporaryFuriten;
    boolean firstTurn = true;
    boolean lastDraw;
    boolean rinshan;
    boolean canDeclare = true;
    boolean pendingRiichi;
    boolean nextDiscardSideways;
    List<Integer> forbiddenDiscards = new ArrayList<>();
    int dragonPao = -1;
    int windPao = -1;
    int kanPao = -1;

    PlayerState(TableSession.Participant member) { this.member = member; }
    PlayerState() { this(null); }

    boolean closed() { return melds.stream().allMatch(Meld::closed); }

    /** Every owned physical tile once; drawn and called discards are aliases. */
    List<Integer> physicalTiles() {
        var result = new ArrayList<>(hand);
        melds.forEach(meld -> result.addAll(meld.tiles()));
        result.addAll(norths);
        result.addAll(flowers);
        river.stream().filter(discard -> !discard.called()).forEach(discard -> result.add(discard.tile()));
        return result;
    }

    void resetHand() {
        autoPlay = AutoPlay.DEFAULT;
        hand.clear(); melds.clear(); river.clear(); norths.clear(); flowers.clear(); forbiddenDiscards.clear();
        drawn = Tile.ABSENT;
        riichi = doubleRiichi = ippatsu = riichiFuriten = temporaryFuriten = false;
        firstTurn = canDeclare = true;
        lastDraw = rinshan = pendingRiichi = nextDiscardSideways = false;
        dragonPao = windPao = kanPao = -1;
    }
}
