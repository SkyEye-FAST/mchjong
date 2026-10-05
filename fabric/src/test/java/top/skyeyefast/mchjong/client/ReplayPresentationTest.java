package top.skyeyefast.mchjong.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.*;
import static org.junit.jupiter.api.Assertions.*;

class ReplayPresentationTest {
    private static final UUID TABLE = new UUID(81, 1);
    private static final List<TableParticipant> PLAYERS = IntStream.range(0, 4)
        .mapToObj(seat -> new TableParticipant(new UUID(82, seat), "Player " + seat)).toList();

    @Test void mcrRoundSelectionIsStaticAndRemainingFollowsEveryFrame() {
        var session = McrSession.start(TABLE, PLAYERS, 711, Tile.standard144Set());
        session.synchronizeSeats(Map.of(PLAYERS.get(0).id(), 0, PLAYERS.get(1).id(), 1,
            PLAYERS.get(2).id(), 2, PLAYERS.get(3).id(), 3));
        for (int step = 0; step < 1000 && session.pendingReplays().isEmpty(); step++) {
            boolean acted = false;
            for (var player : PLAYERS) {
                var view = session.view(player.id());
                var actions = view.game().actions();
                if (actions.isEmpty()) continue;
                int selected = 0;
                for (int i = 0; i < actions.size(); i++) if (actions.get(i).type() == McrAction.Type.PASS
                    || actions.get(i).type() == McrAction.Type.DISCARD) { selected = i; break; }
                assertTrue(session.act(player.id(), TABLE, view.incarnation(), view.game().decision(), selected));
                acted = true;
                break;
            }
            assertTrue(acted, "The real match must keep advancing");
        }
        var match = session.pendingReplays().getFirst();
        assertEquals(Component.translatable("mcr.mchjong.indicator_round", Component.translatable("wind.mchjong.east.short"), 1),
            ReplayPresentation.roundLabel(match, 0));
        var playback = ReplayPresentation.of(match, 0);
        var nativeFrames = McrReplayPlayback.timeline(match, 0).frames();
        assertEquals(91, playback.frames().getFirst().board().remaining());
        assertEquals(0, playback.frames().getLast().board().remaining());
        for (int i = 0; i < nativeFrames.size(); i++)
            assertEquals(nativeFrames.get(i).view().remaining(), playback.frames().get(i).board().remaining());
    }

    @Test void bothSichuanPresetsKeepStaticHandLabelsAndCurrentWallCounts() {
        for (var preset : SichuanPreset.values()) {
            var game = new SichuanGame(711, preset.config(), Tile.sichuanSet());
            var recorder = new SichuanReplayRecorder(game);
            for (int step = 0; step < 1000 && !game.ended(); step++) {
                boolean acted = false;
                for (int seat = 0; seat < 4; seat++) {
                    var actions = game.actions(seat);
                    if (actions.isEmpty()) continue;
                    int selected = 0;
                    for (int i = 0; i < actions.size(); i++) if (actions.get(i).type() == SichuanAction.Type.PASS
                        || actions.get(i).type() == SichuanAction.Type.DISCARD) { selected = i; break; }
                    var before = game.save();
                    assertTrue(game.act(seat, game.decision(), selected));
                    game.validate();
                    recorder.accepted(before, seat, actions, selected, game);
                    acted = true;
                    break;
                }
                assertTrue(acted, "The real match must keep advancing");
            }
            assertTrue(game.ended());
            var match = new ReplayMatch(new UUID(83, 1), TABLE, 1, 2, PLAYERS.stream()
                .map(p -> new ReplayMatch.Participant(p.id(), p.name(), false)).toList(), MahjongVariant.SICHUAN,
                false, null, null, new SichuanReplay(game.rules(), List.of(recorder.finish(game))), null);
            assertEquals(Component.translatable("sichuan.mchjong.indicator_round", 1, 8), ReplayPresentation.roundLabel(match, 0));
            var playback = ReplayPresentation.of(match, 0);
            var nativeFrames = SichuanReplayPlayback.timeline(match, 0).frames();
            assertEquals(55, playback.frames().getFirst().board().remaining());
            assertEquals(0, playback.frames().getLast().board().remaining());
            assertEquals(Component.translatable("sichuan.mchjong.results", 1), playback.frames().getLast().caption());
            for (int i = 0; i < nativeFrames.size(); i++) assertEquals(nativeFrames.get(i).state().wall().slots().stream()
                .filter(tile -> tile != Tile.ABSENT).count(), playback.frames().get(i).board().remaining());
        }
    }
    @Test void taiwanUsesNativeWallSlotsAndKeepsEveryOtherHandAndCoveredKongHidden() {
        for (var preset : TaiwanPreset.values()) {
            var game = TaiwanGame.shuffled(4,preset.rules(),0,Tile.EAST,0);
            var recorder = new TaiwanReplayRecorder(1,List.of(0L,0L,0L,0L),game);
            for (int step = 0; step < 1000 && game.getPhase() != TaiwanGame.Phase.FINISHED; step++) {
                var d = game.decisions().getFirst(); int selected = -1;
                for (int i = 0; i < d.getActions().size(); i++) {
                    var type = d.getActions().get(i).getType();
                    if (type == TaiwanAction.Type.DISCARD || type == TaiwanAction.Type.PASS) { selected = i; break; }
                }
                var before = game.save(); game.submit(d.getSeat(),d.getToken(),selected);
                recorder.accepted(before,d.getSeat(),d.getActions(),selected,game);
            }
            var hand = recorder.finish(game);
            var match = new ReplayMatch(UUID.randomUUID(),TABLE,1,2,PLAYERS.stream()
                .map(p -> new ReplayMatch.Participant(p.id(),p.name(),false)).toList(),MahjongVariant.TAIWAN,false,
                null,null,null,new TaiwanReplay(TaiwanGameState.Rules.of(preset.rules()),List.of(hand)));
            var presentation = ReplayPresentation.of(match,0); var nativeFrames = TaiwanReplayPlayback.timeline(match,0).frames();
            assertEquals(java.util.stream.IntStream.range(0,hand.recording().wall().size()).mapToObj(i ->
                TaiwanWallLayout.drawSlot(hand.recording().wall().size(),hand.recording().opening().restore(),i)).toList(),presentation.wallDrawOrder());
            assertEquals(hand.decisions().size(),presentation.decisions().size());
            assertEquals(Component.translatable("taiwan.mchjong.round",Component.translatable("wind.mchjong.east.short"),1,0),ReplayPresentation.roundLabel(match,0));
            for (int side = 0; side < 4; side++) for (var layer : TaiwanWallLayout.Layer.values())
                for (int stack = 0; stack < TaiwanWallLayout.stacks(hand.recording().wall().size()); stack++)
                    assertEquals(TaiwanWallLayout.slot(hand.recording().wall().size(),side,stack,layer),
                        presentation.wallSlots().get(side).get(layer.ordinal()*TaiwanWallLayout.stacks(hand.recording().wall().size())+stack));
            for (int i = 0; i < nativeFrames.size(); i++) {
                var nativeFrame = nativeFrames.get(i); var frame = presentation.frames().get(i);
                assertEquals(nativeFrame.drawable(),frame.board().remaining());
                for (int viewer = 0; viewer < 4; viewer++) {
                    var board = frame.board().replay(viewer);
                    assertFalse(board.layHandsOpen());
                    assertEquals(nativeFrame.state().players().get(viewer).hand(),board.seats().get(viewer).hand());
                    for (int seat = 0; seat < 4; seat++) if (seat != viewer) {
                        assertTrue(board.seats().get(seat).hand().stream().allMatch(t -> t == Tile.HIDDEN));
                        assertTrue(board.seats().get(seat).melds().stream().filter(Meld::closed)
                            .allMatch(m -> m.tiles().stream().allMatch(t -> t == Tile.HIDDEN)));
                    }
                }
            }
            assertTrue(presentation.frames().getLast().settled());
            assertEquals(hand.settlement().deltas(),presentation.result().deltas());
            for (int viewer = 0; viewer < 4; viewer++) {
                var result = presentation.result().withViewer(viewer);
                for (int seat = 0; seat < 4; seat++) if (seat != viewer) {
                    assertFalse(result.seats().get(seat).exposed());
                    assertTrue(result.seats().get(seat).hand().stream().allMatch(t -> t == Tile.HIDDEN));
                }
            }
        }
        var kong = new Meld(Meld.Type.CONCEALED_QUAD,List.of(0,1,2,3),0,Tile.ABSENT);
        assertEquals(List.of(Tile.HIDDEN,Tile.HIDDEN,Tile.HIDDEN,Tile.HIDDEN),TableBoardState.taiwanMelds(List.of(kong),0,1).getFirst().tiles());
        assertEquals(List.of(kong),TableBoardState.taiwanMelds(List.of(kong),0,0));
    }
}
