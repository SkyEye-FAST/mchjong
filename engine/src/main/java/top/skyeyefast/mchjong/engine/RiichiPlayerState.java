package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;

/** Server-only state. It must never be serialized into a client payload. */
final class RiichiPlayerState {
    transient TableSession.Participant member;
    RiichiAutoPlay autoPlay = RiichiAutoPlay.DEFAULT;
    int points;
    List<Integer> hand = new ArrayList<>();
    List<Meld> melds = new ArrayList<>();
    List<RiichiDiscard> river = new ArrayList<>();
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

    RiichiPlayerState(TableSession.Participant member) { this.member = member; }

    record Saved(RiichiAutoPlay autoPlay, int points, List<Integer> hand, List<Meld> melds,
                 List<RiichiDiscard> river, List<Integer> norths, int drawn,
                 boolean riichi, boolean doubleRiichi, boolean ippatsu, boolean riichiFuriten,
                 boolean temporaryFuriten, boolean firstTurn, boolean lastDraw, boolean rinshan,
                 boolean canDeclare, boolean pendingRiichi, boolean nextDiscardSideways,
                 List<Integer> forbiddenDiscards, int dragonPao, int windPao, int kanPao) {
        Saved {
            java.util.Objects.requireNonNull(autoPlay);
            hand = List.copyOf(hand);
            melds = List.copyOf(melds);
            river = List.copyOf(river);
            norths = List.copyOf(norths);
            forbiddenDiscards = List.copyOf(forbiddenDiscards);
        }
    }

    Saved save() {
        return new Saved(autoPlay, points, hand, melds, river, norths, drawn,
            riichi, doubleRiichi, ippatsu, riichiFuriten, temporaryFuriten, firstTurn,
            lastDraw, rinshan, canDeclare, pendingRiichi, nextDiscardSideways,
            forbiddenDiscards, dragonPao, windPao, kanPao);
    }

    static RiichiPlayerState restore(Saved saved, TableSession.Participant member) {
        var player = new RiichiPlayerState(member);
        player.autoPlay = saved.autoPlay();
        player.points = saved.points();
        player.hand.addAll(saved.hand());
        player.melds.addAll(saved.melds());
        player.river.addAll(saved.river());
        player.norths.addAll(saved.norths());
        player.drawn = saved.drawn();
        player.riichi = saved.riichi();
        player.doubleRiichi = saved.doubleRiichi();
        player.ippatsu = saved.ippatsu();
        player.riichiFuriten = saved.riichiFuriten();
        player.temporaryFuriten = saved.temporaryFuriten();
        player.firstTurn = saved.firstTurn();
        player.lastDraw = saved.lastDraw();
        player.rinshan = saved.rinshan();
        player.canDeclare = saved.canDeclare();
        player.pendingRiichi = saved.pendingRiichi();
        player.nextDiscardSideways = saved.nextDiscardSideways();
        player.forbiddenDiscards.addAll(saved.forbiddenDiscards());
        player.dragonPao = saved.dragonPao();
        player.windPao = saved.windPao();
        player.kanPao = saved.kanPao();
        return player;
    }

    boolean closed() { return melds.stream().allMatch(Meld::closed); }

    /** Every owned physical tile once; drawn and called discards are aliases. */
    List<Integer> physicalTiles() {
        var result = new ArrayList<>(hand);
        melds.forEach(meld -> result.addAll(meld.tiles()));
        result.addAll(norths);
        river.stream().filter(discard -> !discard.called()).forEach(discard -> result.add(discard.tile()));
        return result;
    }

    void resetHand() {
        autoPlay = RiichiAutoPlay.DEFAULT;
        hand.clear(); melds.clear(); river.clear(); norths.clear(); forbiddenDiscards.clear();
        drawn = Tile.ABSENT;
        riichi = doubleRiichi = ippatsu = riichiFuriten = temporaryFuriten = false;
        firstTurn = canDeclare = true;
        lastDraw = rinshan = pendingRiichi = nextDiscardSideways = false;
        dragonPao = windPao = kanPao = -1;
    }
}
