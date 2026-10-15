package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.McrDiscard;
import top.skyeyefast.mchjong.engine.McrGame;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.Tile;
import static org.junit.jupiter.api.Assertions.*;

class McrLayoutTest {
    @Test void centerUsesNativeWindKindsAndKeepsOwnerLabelsWhenViewerChanges() {
        var indicator = TableBoardState.live(new McrGame(711).view(-1)).indicator();
        var title = (net.minecraft.network.chat.contents.TranslatableContents) indicator.title().getContents();
        assertEquals("mcr.mchjong.indicator_round", title.getKey());
        var prevailing = (net.minecraft.network.chat.Component) title.getArgs()[0];
        assertEquals("wind.mchjong.east.short", ((net.minecraft.network.chat.contents.TranslatableContents) prevailing.getContents()).getKey());
        assertEquals(List.of("east", "south", "west", "north"), indicator.seats().stream()
            .map(label -> ((net.minecraft.network.chat.contents.TranslatableContents) label.getContents()).getKey().replace("wind.mchjong.", "").replace(".short", "")).toList());
        assertEquals(indicator, TableBoardState.live(new McrGame(711).view(0)).replay(2).indicator());
    }

    @Test void riversCompactCalledHistoryIntoCenteredSixColumns() {
        var history = new ArrayList<McrDiscard>();
        for (int i = 0; i < 26; i++) history.add(new McrDiscard(i, i == 2 || i == 7, false));
        var parts = McrRiverLayout.of(history);
        assertEquals(24, parts.size());
        for (int index = 0; index < parts.size(); index++) {
            var part = parts.get(index);
            assertFalse(history.get(part.historyIndex()).called());
            assertEquals(index % 6, part.column());
            assertEquals(index / 6, part.row());
        }
        assertEquals(3, parts.get(2).historyIndex());
        assertEquals(14, parts.get(12).historyIndex());
        assertEquals(25, parts.get(23).historyIndex());
    }

    @Test void sourcesChooseLeftMiddleRightAndEveryKongIsOneFlatRow() {
        for (int from : new int[]{3, 2, 1}) {
            var triplet = ChineseMeldLayout.of(new Meld(Meld.Type.TRIPLET, List.of(0, 1, 2), from, 0), 0, false, TileDimensions.LARGE);
            int called = from == 3 ? 0 : from == 2 ? 1 : 2;
            assertTrue(triplet.parts().get(called).sideways());
            for (var type : List.of(Meld.Type.OPEN_QUAD, Meld.Type.ADDED_QUAD)) {
                var layout = ChineseMeldLayout.of(new Meld(type, List.of(0, 1, 2, 3), from, 0), 0, false, TileDimensions.LARGE);
                assertEquals(4, layout.parts().size());
                assertEquals(3 * (double) TileDimensions.LARGE.width() + TileDimensions.LARGE.height(), layout.width(), 1e-8);
                assertTrue(layout.parts().get(from == 1 ? 3 : called).sideways());
                assertEquals(1, layout.parts().stream().filter(ChineseMeldLayout.Part::sideways).count());
                for (int i = 1; i < 4; i++) assertTrue(layout.parts().get(i).x() > layout.parts().get(i - 1).x());
            }
        }
        var chow = new Meld(Meld.Type.SEQUENCE, List.of(0, 4, 8), 3, 4);
        assertTrue(ChineseMeldLayout.of(chow, 0, false, TileDimensions.LARGE).parts().get(0).sideways());
        assertThrows(IllegalArgumentException.class, () -> ChineseMeldLayout.of(new Meld(chow.type(), chow.tiles(), 1, 4), 0, false, TileDimensions.LARGE));
        var concealed = new Meld(Meld.Type.CONCEALED_QUAD, List.of(0, 1, 2, 3), 0, Tile.ABSENT);
        assertTrue(ChineseMeldLayout.of(concealed, 0, false, TileDimensions.LARGE).parts().stream().allMatch(part -> part.back() && !part.sideways()));
        assertTrue(ChineseMeldLayout.of(concealed, 0, true, TileDimensions.LARGE).parts().stream().noneMatch(ChineseMeldLayout.Part::back));
    }

    @Test void flowersShareTheLeftPublicAreaAndHandIndicesKeepTheirSourceIdentity() {
        var publicArea = ChineseTableLayout.publicArea(List.of(), 8, 13, false, 0,
            new ArrayList<>(), TileDimensions.LARGE);
        assertEquals(8, publicArea.flowers().size());
        assertTrue(publicArea.flowers().getFirst().x < 0);
        var game = new McrGame(711);
        var scene = McrTableScene.build(game.view(0));
        for (var piece : scene) {
            if (piece.area() == McrTableScene.Area.HAND && piece.seat() == 0)
                assertEquals(game.hand(0).get(piece.index()), piece.tile());
            if (piece.area() == McrTableScene.Area.FLOWER) {
                assertTrue(Tile.isFlower(piece.tile()));
                assertTrue(piece.flat());
                assertFalse(piece.back());
            }
        }
    }

    @Test void eightFlowersAndFourKongsUseARealConservedStockWithoutScaling() {
        var view = top.skyeyefast.mchjong.fixture.ChineseGameplayFixtures.fourKongsAndEightFlowers();
        assertEquals(8, view.seats().get(0).flowers().size());
        assertEquals(4, view.seats().get(0).melds().size());
        assertEquals(2, view.seats().get(0).hand().size());
        var scene = McrTableScene.build(view);
        assertEquals(view.wall().size(), scene.size());
        assertTrue(scene.stream().allMatch(piece -> piece.dimensions().equals(TileDimensions.LARGE)));
        for (var piece : scene) WallGeometryAssertions.onFelt(WallGeometryAssertions.solid(piece));
        WallGeometryAssertions.noIntersections(scene.stream().map(WallGeometryAssertions::solid).toList());
    }

}
