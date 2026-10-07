package top.skyeyefast.mchjong.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.*;
import top.skyeyefast.mchjong.fixture.ChineseGameplayFixtures;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministic top views from production poses and engine-issued decisions. */
class TableLayoutEvidenceTest {
    record TileBox(WallGeometryAssertions.Solid solid, String area) {}
    record Panel(String title, List<TileBox> tiles) {}

    @Test void exportConservedPositionsAndVerifySmallTilePicking() throws Exception {
        var panels = new ArrayList<Panel>();
        var riichi = TableLayoutTest.startSession(RiichiPreset.TENHOU_4, new java.util.UUID(10, 15)).view(null);
        panels.add(new Panel("Riichi / complete wall", java.util.stream.IntStream.range(0, riichi.wall().size())
            .mapToObj(i -> riichi(RiichiTableScene.wallPiece(riichi, i, true))).toList()));
        panels.add(new Panel("Riichi / dealt", RiichiTableScene.build(riichi).stream().map(TableLayoutEvidenceTest::riichi).toList()));
        panels.add(new Panel("MCR / complete wall", McrTableScene.fullWall().stream().map(TableLayoutEvidenceTest::mcr).toList()));
        for (int remaining : new int[]{91, 40, 4}) panels.add(new Panel("MCR / wall remaining " + remaining,
            McrTableScene.build(ChineseGameplayFixtures.mcr(711, remaining, 0, ignored -> {})).stream().map(TableLayoutEvidenceTest::mcr).toList()));
        panels.add(new Panel("MCR / four kongs + eight flowers", McrTableScene.build(ChineseGameplayFixtures.fourKongsAndEightFlowers())
            .stream().map(TableLayoutEvidenceTest::mcr).toList()));
        for (boolean eastWest : new boolean[]{true, false}) {
            panels.add(new Panel("Sichuan / complete " + (eastWest ? "14-13" : "13-14"),
                SichuanTableScene.fullWall(eastWest).stream().map(TableLayoutEvidenceTest::sichuan).toList()));
            for (int remaining : new int[]{55, 25, 4}) panels.add(new Panel("Sichuan / " + (eastWest ? "SBR" : "TFMJ") + " / remaining " + remaining,
                SichuanTableScene.build(ChineseGameplayFixtures.sichuan(711, eastWest, remaining, 0, false, ignored -> {}))
                    .stream().map(TableLayoutEvidenceTest::sichuan).toList()));
        }
        for (var preset : TaiwanPreset.values()) {
            int size = preset.rules().getFlowers() == TaiwanRules.Flowers.NONE ? 136 : 144;
            panels.add(new Panel("Taiwan / complete " + size, TaiwanTableScene.fullWall(size).stream().map(TableLayoutEvidenceTest::taiwan).toList()));
            var game = TaiwanGame.shuffled(4, preset.rules(), 0, Tile.EAST, 0);
            int phase = 0, steps = 0, mostPublic = -1;
            TaiwanView publicPosition = null;
            while (true) {
                var view = game.view(0);
                int remaining = (int) view.wall().stream().filter(t -> t != Tile.ABSENT).count();
                if (phase == 0 || phase == 1 && remaining <= 40 || phase == 2 && remaining <= 4) {
                    panels.add(new Panel("Taiwan / " + size + " / remaining " + remaining,
                        TaiwanTableScene.build(view).stream().map(TableLayoutEvidenceTest::taiwan).toList()));
                    verifyPicking(game);
                    phase++;
                }
                int publicCount = view.seats().stream().mapToInt(s -> s.melds().size() * 4 + s.flowers().size()).sum();
                if (publicCount > mostPublic) { mostPublic = publicCount; publicPosition = view; }
                if (game.getPhase() == TaiwanGame.Phase.FINISHED) break;
                assertTrue(++steps < 1000);
                var decision = game.decisions().getFirst();
                var actions = decision.getActions();
                int choice = -1;
                for (int i = 0; i < actions.size(); i++) if (List.of(TaiwanAction.Type.CHOW, TaiwanAction.Type.PONG,
                    TaiwanAction.Type.OPEN_KONG, TaiwanAction.Type.CONCEALED_KONG, TaiwanAction.Type.ADDED_KONG).contains(actions.get(i).getType())) { choice = i; break; }
                if (choice < 0) for (int i = 0; i < actions.size(); i++) if (actions.get(i).getType() ==
                    (game.getPhase() == TaiwanGame.Phase.REACTION ? TaiwanAction.Type.PASS : TaiwanAction.Type.DISCARD)) { choice = i; break; }
                if (choice < 0) choice = 0;
                game.submit(decision.getSeat(), decision.getToken(), choice);
            }
            panels.add(new Panel("Taiwan / " + size + " / most public tiles", TaiwanTableScene.build(publicPosition)
                .stream().map(TableLayoutEvidenceTest::taiwan).toList()));
        }
        var five = fiveMeldPosition();
        panels.add(new Panel("Taiwan / five actual meld groups", TaiwanTableScene.build(five).stream().map(TableLayoutEvidenceTest::taiwan).toList()));
        for (var panel : panels) {
            for (var tile : panel.tiles()) WallGeometryAssertions.onFelt(tile.solid());
            WallGeometryAssertions.noIntersections(panel.tiles().stream().map(TileBox::solid).toList());
        }
        Path output = Path.of("build", "layout-evidence", "table-layouts.svg");
        Files.createDirectories(output.getParent());
        Files.writeString(output, svg(panels));
    }

    private static TaiwanView fiveMeldPosition() {
        var game = TaiwanGame.shuffled(16, TaiwanPreset.values()[0].rules(), 0, Tile.EAST, 0);
        for (int step = 0; step < 1000 && game.getPhase() != TaiwanGame.Phase.FINISHED; step++) {
            var view = game.view(0);
            if (view.seats().stream().anyMatch(seat -> seat.melds().size() == 5)) {
                return view;
            }
            var decision = game.decisions().getFirst();
            var actions = decision.getActions();
            int choice = -1;
            for (int i = 0; i < actions.size(); i++) if (List.of(TaiwanAction.Type.CHOW, TaiwanAction.Type.PONG,
                TaiwanAction.Type.OPEN_KONG, TaiwanAction.Type.CONCEALED_KONG, TaiwanAction.Type.ADDED_KONG).contains(actions.get(i).getType())) { choice = i; break; }
            if (choice < 0) for (int i = 0; i < actions.size(); i++) if (actions.get(i).getType() ==
                (game.getPhase() == TaiwanGame.Phase.REACTION ? TaiwanAction.Type.PASS : TaiwanAction.Type.DISCARD)) { choice = i; break; }
            if (choice < 0) choice = 0;
            game.submit(decision.getSeat(), decision.getToken(), choice);
        }
        throw new AssertionError("Seeded play did not reach five melds");
    }

    private static void verifyPicking(TaiwanGame game) {
        for (int viewer = 0; viewer < 4; viewer++) {
            var scene = TaiwanTableScene.build(game.view(viewer));
            var eye = TableGeometry.orient(0, 2.10, TableGeometry.STOOL_DISTANCE, viewer);
            for (var piece : scene) if (piece.seat() == viewer && piece.area() == TaiwanTableScene.Area.HAND) {
                var ray = piece.position().subtract(eye);
                double hit = pick(piece, eye, ray);
                assertTrue(Double.isFinite(hit));
                for (var other : scene) assertTrue(hit <= pick(other, eye, ray) + 1e-7, "Taiwan hand center occluded");
            }
        }
    }
    private static double pick(TaiwanTableScene.Piece piece, net.minecraft.world.phys.Vec3 eye, net.minecraft.world.phys.Vec3 ray) {
        return TilePicking.distanceSquared(new TableAnimation.Pose(piece.position(), piece.yaw(),
            piece.flat() ? piece.back() ? 90 : -90 : 0, piece.tile(), piece.back()), piece.dimensions(), eye, ray, false);
    }
    private static TileBox riichi(RiichiTableScene.Piece p) {
        var d = RiichiTableScene.DIMENSIONS;
        return new TileBox(new WallGeometryAssertions.Solid(p.position(), p.yaw(), d.width(), p.flat() ? d.depth() : d.height(), p.flat() ? d.height() : d.depth()), p.area().name());
    }
    private static TileBox mcr(McrTableScene.Piece p) { return new TileBox(WallGeometryAssertions.solid(p), p.area().name()); }
    private static TileBox sichuan(SichuanTableScene.Piece p) { return new TileBox(WallGeometryAssertions.solid(p), p.area().name()); }
    private static TileBox taiwan(TaiwanTableScene.Piece p) {
        var d = p.dimensions();
        return new TileBox(new WallGeometryAssertions.Solid(p.position(), p.yaw(), d.width(), p.flat() ? d.depth() : d.height(), p.flat() ? d.height() : d.depth()), p.area().name());
    }
    private static String svg(List<Panel> panels) {
        var out = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1440\" height=\"" + ((panels.size() + 3) / 4 * 370 + 60) + "\" viewBox=\"0 0 1440 " + ((panels.size() + 3) / 4 * 370 + 60) + "\"><rect width=\"100%\" height=\"100%\" fill=\"#101e23\"/><g font-family=\"sans-serif\" fill=\"#f1eee3\"><text x=\"20\" y=\"25\" font-size=\"18\">Production geometry / equal scale / engine-reachable positions</text><text x=\"20\" y=\"47\" font-size=\"13\">Blue: wall | Ivory: hand | Gold: meld | Pink: flower | Green: river | Center: wind panel</text>");
        for (int i = 0; i < panels.size(); i++) {
            var panel = panels.get(i);
            out.append(String.format(Locale.ROOT, "<g transform=\"translate(%d %d)\"><text x=\"12\" y=\"20\" font-size=\"14\">%s</text><g transform=\"translate(180 195) scale(116)\"><rect x=\"-1.3125\" y=\"-1.3125\" width=\"2.625\" height=\"2.625\" fill=\"#20584f\"/><rect x=\"-.265\" y=\"-.265\" width=\".53\" height=\".53\" fill=\"#0d171d\"/>", i % 4 * 360, 60 + i / 4 * 370, panel.title()));
            for (var tile : panel.tiles().stream().sorted(java.util.Comparator.comparingDouble(t -> t.solid().position().y)).toList()) {
                var b = tile.solid();
                String color = switch (tile.area()) { case "WALL" -> "#75a8d0"; case "MELD" -> "#e3c082"; case "FLOWER", "NORTH" -> "#efa3b7"; case "RIVER" -> "#a9d8b8"; default -> "#f1eee3"; };
                out.append(String.format(Locale.ROOT, "<rect transform=\"translate(%.6f %.6f) rotate(%.3f)\" x=\"%.6f\" y=\"%.6f\" width=\"%.6f\" height=\"%.6f\" fill=\"%s\" stroke=\"#17262a\" stroke-width=\".004\"/>", b.position().x, b.position().z, -b.yaw(), -b.width()/2, -b.depth()/2, b.width(), b.depth(), color));
            }
            out.append("</g></g>");
        }
        return out.append("</g></svg>").toString();
    }
}
