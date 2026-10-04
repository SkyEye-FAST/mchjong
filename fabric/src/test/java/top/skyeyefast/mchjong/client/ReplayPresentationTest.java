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
                false, null, null, new SichuanReplay(game.rules(), List.of(recorder.finish(game))));
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
}
