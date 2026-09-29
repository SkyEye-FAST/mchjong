package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.UUID;

/** Server-issued position for a remote bot; physical tiles and legal choices stay authoritative here. */
public record BotPosition(int protocolVersion, String botId, RuleSet preset,
                          UUID tableId, UUID sessionId, int handNumber, int seat, int playerCount,
                          long decision, Opening opening, List<Event> events, List<Action> legalActions,
                          Focus focus, Integer drawnTile, List<Pon> melds) {
    public record Opening(int round, int dealer, int honba, int riichiSticks,
                          List<Integer> scores, List<Integer> hand, int doraMarker) {}
    public record Event(String kind, Integer seat, Integer tile, boolean tsumogiri, boolean riichi,
                        Integer fromSeat, Integer calledTile, List<Integer> tiles) {}
    public record Focus(int seat, int tile) {}
    public record Pon(String kind, List<Integer> tiles) {}

    public BotPosition {
        events = List.copyOf(events);
        legalActions = List.copyOf(legalActions);
        melds = List.copyOf(melds);
    }
}
