package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.client.TableAnimation;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableScene;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.client.TileMesh;
import top.skyeyefast.mchjong.engine.Discard;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Real renderer, synthetic public snapshots: no fixture action is sent to the live server. */
final class AnimationSmoke {
    private TableView fixture;
    private int ticks;
    private int windowWidth, windowHeight, guiScale;
    private final List<TableSettings.Information> hidden = new ArrayList<>();

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) {
        ticks++;
        if (fixture == null) {
            TableView base = table.clientView();
            var seats = new ArrayList<TableView.Seat>();
            for (int seat = 0; seat < base.rules().players(); seat++) seats.add(seat(seat == 0
                ? IntStream.range(0, 14).boxed().toList() : Collections.nCopies(13, Tile.HIDDEN), List.of(), List.of(), false));
            var wall = new ArrayList<>(Collections.nCopies(136, Tile.HIDDEN));
            for (int i = 0; i < 53; i++) wall.set(i, Tile.ABSENT);
            fixture = new TableView(base.tableId(), base.revision() + 1, base.decision() + 1, base.handNumber() + 1,
                base.rules(), Game.Phase.TURN, 0, 0, 0, 0, 0, 0, 70, 12, wall, null, seats, List.of(), List.of(),
                "playing", List.of(), List.of(), base.timeControl(), base.clocks(), List.of(), false, null);
            table.acceptView(fixture);
            for (var information : TableSettings.Information.values())
                if (information != TableSettings.Information.ROUND && information != TableSettings.Information.TURN
                        && TableSettings.get().show(information)) {
                    hidden.add(information);
                    TableSettings.get().toggle(information);
                }
            TableSettings.get().animations = true;
            TableScreen screen = new TableScreen(table.getBlockPos());
            client.setScreen(screen);
            screen.resetView();
        }
        if (ticks == 6) capture(client, output, "12-wall-rising.png");
        if (ticks == 18) capture(client, output, "13-dealing-packets.png");
        if (ticks == 60) {
            if (TableAnimation.of(table).dealing(Util.getMillis())) throw new IllegalStateException("Deal did not finish");
            capture(client, output, "14-animated-deal-complete.png");
        }
        if (ticks == 61) {
            var seats = new ArrayList<>(fixture.seats());
            seats.set(0, seat(IntStream.range(0, 13).boxed().toList(), List.of(),
                List.of(new Discard(13, true, false, true)), false));
            update(table, seats, fixture.wall());
        }
        if (ticks == 65) capture(client, output, "15-riichi-discard-moving.png");
        if (ticks == 74) {
            var seats = new ArrayList<>(fixture.seats());
            seats.set(0, seat(seats.getFirst().hand(), List.of(), List.of(new Discard(13, true, true, true)), true));
            seats.set(1, seat(Collections.nCopies(11, Tile.HIDDEN),
                List.of(new Meld(Meld.Type.PON, List.of(13, 14, 15), 0, 13)), List.of(), false));
            update(table, seats, fixture.wall());
        }
        if (ticks == 78) capture(client, output, "16-pon-and-riichi-stick-moving.png");
        if (ticks == 90) {
            capture(client, output, "17-meld-and-riichi-settled.png");
            var seats = new ArrayList<>(fixture.seats());
            var hand = new ArrayList<>(IntStream.range(4, 13).boxed().toList());
            hand.add(16);
            seats.set(0, seat(hand, List.of(new Meld(Meld.Type.CLOSED_KAN, List.of(0, 1, 2, 3), 0, Tile.ABSENT)),
                seats.getFirst().river(), true));
            var wall = new ArrayList<>(fixture.wall());
            wall.set(53, Tile.ABSENT);
            update(table, seats, wall);
        }
        if (ticks == 94) capture(client, output, "18-closed-kan-moving.png");
        if (ticks == 105) {
            TableSettings.get().animations = false;
            capture(client, output, "19-closed-kan-settled.png");
        }
        if (ticks == 110) {
            hidden.forEach(TableSettings.get()::toggle);
            InputSmoke.verify(client, table);
        }
        if (ticks == 114) capture(client, output, "20-riichi-selection.png");
        if (ticks >= 116 && ticks <= 172 && (ticks - 116) % 14 == 0) {
            int count = (ticks - 116) / 14;
            fixture = table.clientView();
            var seats = new ArrayList<>(fixture.seats());
            var melds = List.of(
                new Meld(Meld.Type.PON, List.of(0, 1, 2), 1, 0),
                new Meld(Meld.Type.CLOSED_KAN, List.of(4, 5, 6, 7), 0, Tile.ABSENT),
                new Meld(Meld.Type.ADDED_KAN, List.of(8, 9, 10, 11), 3, 8),
                new Meld(Meld.Type.OPEN_KAN, List.of(12, 13, 14, 15), 2, 12));
            seats.set(0, seat(IntStream.range(80, 94 - count * 3).boxed().toList(),
                melds.subList(0, count), List.of(), false));
            update(table, seats, fixture.wall());
            TableSettings.get().animations = false;
            var screen = new TableScreen(table.getBlockPos());
            client.setScreen(screen);
            screen.resetView();
        }
        if (ticks >= 126 && ticks <= 182 && (ticks - 126) % 14 == 0) {
            verifyCornerVisible(client, table);
            capture(client, output, "40-layout-" + (ticks - 126) / 14 + "-melds.png");
        }
        if (ticks == 184) {
            windowWidth = client.getWindow().getWidth();
            windowHeight = client.getWindow().getHeight();
            guiScale = client.options.guiScale().get();
            client.getWindow().setWindowed(960, 720);
            client.options.guiScale().set(3);
            client.resizeDisplay();
            var melds = IntStream.range(0, 4).mapToObj(i -> new Meld(Meld.Type.OPEN_KAN,
                List.of(i * 4, i * 4 + 1, i * 4 + 2, i * 4 + 3), i % 3 + 1, i * 4)).toList();
            var seats = new ArrayList<>(fixture.seats());
            seats.set(0, seat(List.of(80, 81), melds, List.of(), false));
            update(table, seats, fixture.wall());
            var screen = new TableScreen(table.getBlockPos());
            client.setScreen(screen);
            screen.resetView();
        }
        if (ticks == 204) {
            if (client.screen.width != 320 || client.screen.height != 240)
                throw new IllegalStateException("Corner layout viewport is not 320x240");
            verifyCornerVisible(client, table);
            capture(client, output, "41-layout-four-kans-320x240.png");
        }
        if (ticks == 206) {
            client.getWindow().setWindowed(windowWidth, windowHeight);
            client.options.guiScale().set(guiScale);
            client.resizeDisplay();
        }
        if (ticks == 220) {
            verifyCornerVisible(client, table);
            capture(client, output, "42-layout-four-kans.png");
        }
        if (ticks == 222 || ticks == 238) {
            var seats = new ArrayList<>(fixture.seats());
            var melds = IntStream.range(0, 2).mapToObj(i -> new Meld(Meld.Type.OPEN_KAN,
                List.of(i * 4, i * 4 + 1, i * 4 + 2, i * 4 + 3), i + 1, i * 4)).toList();
            seats.set(0, seat(IntStream.range(80, ticks == 222 ? 87 : 88).boxed().toList(), melds, List.of(), false));
            update(table, seats, fixture.wall());
            var screen = new TableScreen(table.getBlockPos());
            client.setScreen(screen);
            screen.resetView();
        }
        if (ticks == 236 || ticks == 252) {
            verifyCornerVisible(client, table);
            var pieces = TableScene.build(table.clientView());
            double center = pieces.stream().filter(p -> p.seat() == 0 && p.area() == TableScene.Area.HAND && p.index() < 7)
                .mapToDouble(p -> p.position().x).average().orElseThrow();
            if (ticks == 236 ? Math.abs(center) > 1e-7 : center >= 0 || center < -TableScene.HAND_STEP - TableScene.DRAW_GAP)
                throw new IllegalStateException("Two-kan hand did not use the closest feasible center: " + center);
            capture(client, output, ticks == 236 ? "43-layout-two-kans-waiting.png" : "44-layout-two-kans-drawn.png");
        }
        if (ticks == 254) {
            TableSettings.get().animations = true;
            return true;
        }
        return false;
    }

    private void update(MahjongTableBlockEntity table, List<TableView.Seat> seats, List<Integer> wall) {
        fixture = new TableView(fixture.tableId(), fixture.revision() + 1, fixture.decision() + 1, fixture.handNumber(), fixture.rules(),
            Game.Phase.TURN, 0, 0, 0, 0, seats.getFirst().riichi() ? 1 : 0, 0, 70, fixture.wallBreak(), wall, null,
            seats, List.of(), List.of(), "playing", List.of(), List.of(), fixture.timeControl(), fixture.clocks(), List.of(), false, null);
        table.acceptView(fixture);
    }

    private static TableView.Seat seat(List<Integer> hand, List<Meld> melds, List<Discard> river, boolean riichi) {
        return new TableView.Seat("Player", true, false, false, riichi ? 24000 : 25000, hand,
            hand.size() % 3 == 2 ? hand.getLast() : Tile.ABSENT, melds, river, List.of(), riichi, false);
    }

    /** Check the complete rendered tile envelopes, not just the centers of the last meld. */
    private static void verifyCornerVisible(Minecraft client, MahjongTableBlockEntity table) {
        var camera = client.gameRenderer.getMainCamera();
        double yaw = Math.toRadians(camera.getYRot()), pitch = Math.toRadians(camera.getXRot());
        var forward = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        var right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        var up = right.cross(forward);
        double focal = client.screen.height / (2 * Math.tan(Math.toRadians(client.options.fov().get()) / 2));
        for (var piece : TableScene.build(table.clientView())) {
            if (piece.seat() != 0 || piece.area() != TableScene.Area.HAND && piece.area() != TableScene.Area.MELD) continue;
            double x = TileMesh.WIDTH * TableScene.TILE_SCALE / 2;
            double y = (piece.flat() ? TileMesh.DEPTH : TileMesh.HEIGHT) * TableScene.TILE_SCALE / 2;
            double z = (piece.flat() ? TileMesh.HEIGHT : TileMesh.DEPTH) * TableScene.TILE_SCALE / 2;
            if (Math.floorMod(Math.round(piece.yaw() / 90), 2) == 1) { double swap = x; x = z; z = swap; }
            for (int dx : new int[]{-1, 1}) for (int dy : new int[]{-1, 1}) for (int dz : new int[]{-1, 1}) {
                var point = TableGeometry.world(table.getBlockPos(), piece.position().add(dx * x, dy * y, dz * z))
                    .subtract(camera.getPosition());
                double depth = point.dot(forward);
                double screenX = client.screen.width / 2.0 + point.dot(right) * focal / depth;
                double screenY = client.screen.height / 2.0 - point.dot(up) * focal / depth;
                if (depth <= 0 || screenX < 2 || screenX > client.screen.width - 2
                        || screenY < 64 || screenY > client.screen.height - 20)
                    throw new IllegalStateException("Hand or right-corner meld is clipped or covered by HUD: " + piece);
            }
        }
    }

    private static void capture(Minecraft client, Path output, String name) {
        Screenshot.grab(output.toFile(), name, client.getMainRenderTarget(), ignored -> {});
    }
}
