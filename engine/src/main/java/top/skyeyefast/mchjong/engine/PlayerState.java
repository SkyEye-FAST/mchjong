package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-only state. It must never be serialized into a client payload. */
final class PlayerState {
    UUID id;
    String name = "";
    boolean bot;
    boolean ready;
    int points;
    List<Integer> hand = new ArrayList<>();
    List<Meld> melds = new ArrayList<>();
    List<Discard> river = new ArrayList<>();
    List<Integer> norths = new ArrayList<>();
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

    boolean closed() { return melds.stream().allMatch(Meld::closed); }

    void resetHand() {
        hand.clear(); melds.clear(); river.clear(); norths.clear(); forbiddenDiscards.clear();
        drawn = Tile.ABSENT;
        riichi = doubleRiichi = ippatsu = riichiFuriten = temporaryFuriten = false;
        firstTurn = canDeclare = true;
        lastDraw = rinshan = pendingRiichi = nextDiscardSideways = false;
        dragonPao = windPao = kanPao = -1;
        ready = false;
    }
}
