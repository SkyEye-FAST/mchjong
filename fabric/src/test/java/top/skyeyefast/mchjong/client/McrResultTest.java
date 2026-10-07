package top.skyeyefast.mchjong.client;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.*;
import static org.junit.jupiter.api.Assertions.*;

class McrResultTest {
    @Test void mixedKongKeepsItsSixPointIdentityForPlayersAndSpectators() {
        for (boolean mixed : List.of(false, true)) {
            var initial = new McrGame(711).view(0);
            int points = mixed ? 6 : 4;
            var score = new McrHandScore(points + 4, points + 4, true, List.of(
                new McrHandScore.Fan("TWO_MELDED_KONGS", 1, points, mixed),
                new McrHandScore.Fan("LAST_TILE", 1, 4, false)));
            var context = new McrWinContext(McrWinContext.Method.DISCARD, Tile.EAST, Tile.EAST,
                false, McrWinContext.KongWin.NONE, true, 0);
            var win = new McrSettlement.Win(0, 1, initial.seats().get(0).hand().get(0), context, score,
                List.of(points + 28, -points - 12, -8, -8));
            var view = new McrView(initial.revision(), initial.decision(), 1, McrGame.Phase.HAND_END,
                0, 0, Tile.EAST, 0, initial.remaining(), initial.opening(), initial.wall(), null,
                initial.seats(), List.of(), false, false, win, List.of());
            var participants = IntStream.range(0, 4).mapToObj(i -> new TableParticipant(new UUID(0, i + 1), "Player " + i)).toList();
            for (int viewer : List.of(0, -1)) {
                var row = TableResultState.mcr(view, participants, viewer).wins().getFirst().rows().getFirst();
                assertEquals("mcr.mchjong.fan." + (mixed ? "mixed_kong_pair" : "two_melded_kongs"),
                    ((TranslatableContents) row.label().getContents()).getKey());
                assertEquals(Integer.toString(points), row.badge().getString());
            }
        }
    }
}
