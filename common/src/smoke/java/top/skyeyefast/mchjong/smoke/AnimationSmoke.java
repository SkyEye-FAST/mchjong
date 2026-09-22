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
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Discard;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.mixin.GameRendererAccessor;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Real renderer, synthetic public snapshots: no fixture action is sent to the live server. */
final class AnimationSmoke {
    private final boolean layoutsOnly;
    private final DepositVisualSmoke deposits = new DepositVisualSmoke();
    private TableView fixture;
    private int ticks;
    private int windowWidth, windowHeight, guiScale;
    private boolean originalHighlight;
    private final List<TableSettings.Information> hidden = new ArrayList<>();

    AnimationSmoke(boolean layoutsOnly) { this.layoutsOnly = layoutsOnly; }

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) {
        ticks++;
        if (fixture == null) {
            TableView base = table.clientView();
            var seats = new ArrayList<TableView.Seat>();
            for (int seat = 0; seat < base.rules().players(); seat++) seats.add(seat(seat == 0
                ? IntStream.range(0, 14).boxed().toList() : Collections.nCopies(13, Tile.HIDDEN), List.of(), List.of(), false));
            var wall = new ArrayList<>(Collections.nCopies(136, Tile.HIDDEN));
            for (int i = 0; i < 53; i++) wall.set(i, Tile.ABSENT);
            // Display-only fixtures must not be replaced by live game heartbeats during the captures.
            fixture = new TableView(base.tableId(), Long.MAX_VALUE / 2, base.decision() + 1, base.handNumber() + 1,
                base.rules(), Game.Phase.TURN, 0, 0, 0, 0, 0, 0, 70, 12, wall, null, seats, List.of(), List.of(),
                "playing", List.of(), List.of(), base.timeControl(), base.clocks(), List.of(), top.skyeyefast.mchjong.engine.HandVisibility.SELF, null, null, base.autoPlay(), false, 1);
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
            if (layoutsOnly) {
                hidden.forEach(TableSettings.get()::toggle);
                hidden.clear();
                ticks = 115;
            }
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
        // Repeat the same zero-to-four-meld fixtures at both accepted GUI sizes.
        int layoutStart = ticks >= 256 ? 256 : 116;
        if (ticks >= layoutStart && ticks <= layoutStart + 56 && (ticks - layoutStart) % 14 == 0) {
            int count = (ticks - layoutStart) / 14;
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
        int captureStart = layoutStart + 10;
        if (ticks >= captureStart && ticks <= captureStart + 56 && (ticks - captureStart) % 14 == 0) {
            boolean small = layoutStart == 256;
            if (client.screen.width != (small ? 320 : 640) || client.screen.height != (small ? 240 : 400))
                throw new IllegalStateException("Unexpected viewport for the corner layout matrix");
            verifyCornerVisible(client, table);
            capture(client, output, (small ? "45-layout-" : "40-layout-") + (ticks - captureStart) / 14
                + (small ? "-melds-320x240.png" : "-melds.png"));
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
            client.getWindow().setWindowed(960, 720);
            client.options.guiScale().set(3);
            client.resizeDisplay();
        }
        if (ticks == 322) {
            var seats = new ArrayList<>(fixture.seats());
            for (int side = 0; side < seats.size(); side++) {
                var player = seats.get(side);
                int first = 40 + side * 20;
                var river = IntStream.range(first, first + (side % 2 == 0 ? 24 : 12))
                    .mapToObj(tile -> new Discard(tile, tile == first + 2, false, false)).toList();
                seats.set(side, new TableView.Seat(player.name(), player.occupied(), player.bot(), player.ready(),
                    player.points(), player.hand(), player.drawn(), player.melds(), river, player.norths(),
                    player.riichi(), player.exposed()));
            }
            var right = seats.get(1);
            var cornerKans = IntStream.range(0, 4).mapToObj(i -> new Meld(Meld.Type.OPEN_KAN,
                List.of(16 + i * 4, 17 + i * 4, 18 + i * 4, 19 + i * 4), 2, 16 + i * 4)).toList();
            seats.set(1, new TableView.Seat(right.name(), right.occupied(), right.bot(), right.ready(), right.points(),
                List.of(Tile.HIDDEN, Tile.HIDDEN), Tile.ABSENT, cornerKans, right.river(), right.norths(), right.riichi(), false));
            update(table, seats, fixture.wall(), 2);
            client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
            if (!((TableScreen) client.screen).immersive()) throw new IllegalStateException("320x240 disabled the fixed immersive canvas");
            String label = net.minecraft.network.chat.Component.translatable("ui.mchjong.automation_show").getString();
            AutomationControlsSmoke.click(client, label);
        }
        if (ticks == 324) {
            capture(client, output, "57-immersive-rivers-melds-320x240-letterbox.png");
            client.getWindow().setWindowed(960, 600);
            client.options.guiScale().set(2);
            client.resizeDisplay();
            if (!((TableScreen) client.screen).immersive()) throw new IllegalStateException("Resize disabled immersive canvas");
        }
        if (ticks == 327) {
            capture(client, output, "57-immersive-rivers-melds-480x300.png");
            client.getWindow().setWindowed(960, 720);
            client.options.guiScale().set(3);
            client.resizeDisplay();
            if (!((TableScreen) client.screen).immersive()) throw new IllegalStateException("GUI scale change disabled immersive canvas");
            capture(client, output, "57-immersive-rivers-melds-320x240-gui3-letterbox.png");
            client.getWindow().setWindowed(windowWidth, windowHeight);
            client.options.guiScale().set(guiScale);
            client.resizeDisplay();
            if (!((TableScreen) client.screen).immersive()) throw new IllegalStateException("Restoring viewport disabled immersive canvas");
        }
        if (ticks == 328) {
            capture(client, output, "57-immersive-rivers-melds-640x400.png");
            client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
        }
        if (layoutsOnly && ticks == 328) { ticks = 359; return false; }
        if (ticks == 328) originalHighlight = TableSettings.get().highlightTiles;
        if (ticks >= 328 && ticks <= 352 && (ticks - 328) % 12 == 0) {
            var calls = new Action.Type[]{Action.Type.CHI, Action.Type.PON, Action.Type.OPEN_KAN};
            InputSmoke.verifyCallFocus(client, table, calls[(ticks - 328) / 12]);
        }
        if (ticks >= 334 && ticks <= 358 && (ticks - 334) % 12 == 0)
            capture(client, output, "56-highlight-" + new String[]{"chi", "pon", "kan"}[(ticks - 334) / 12] + "-focus.png");
        if (ticks == 359) TableSettings.get().highlightTiles = originalHighlight;
        if (ticks == 360) {
            var seats = new ArrayList<>(fixture.seats());
            for (int seat = 0; seat < seats.size(); seat++) seats.set(seat, seat(seat == 0
                ? IntStream.range(0, 14).boxed().toList() : Collections.nCopies(14, Tile.HIDDEN), List.of(), List.of(), false));
            update(table, seats, fixture.wall());
            TableSettings.get().animations = true;
            client.setScreen(new TableScreen(table.getBlockPos()));
            client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
        }
        if (ticks == 364 || ticks == 384 || ticks == 404) {
            int owner = ticks == 404 ? 1 : 0;
            boolean tsumogiri = ticks != 364;
            int tile = ticks == 364 ? 4 : ticks == 384 ? 14 : 60;
            var seats = new ArrayList<>(fixture.seats());
            var player = seats.get(owner);
            var hand = new ArrayList<>(player.hand());
            if (owner == 0) hand.remove(Integer.valueOf(tile));
            else hand.removeLast();
            var river = new ArrayList<>(player.river());
            river.add(new Discard(tile, ticks == 364, false, tsumogiri));
            seats.set(owner, seat(hand, player.melds(), river, player.riichi() || ticks == 364));
            update(table, seats, fixture.wall(), owner);
        }
        if (ticks == 367 || ticks == 387 || ticks == 407)
            capture(client, output, "58-immersive-" + (ticks == 367 ? "tedashi-riichi" : ticks == 387 ? "tsumogiri" : "opponent-tsumogiri") + "-moving.png");
        if (ticks == 378 || ticks == 398 || ticks == 418)
            capture(client, output, "58-immersive-" + (ticks == 378 ? "tedashi-riichi" : ticks == 398 ? "tsumogiri" : "opponent-tsumogiri") + "-landed.png");
        if (ticks == 380) {
            var seats = new ArrayList<>(fixture.seats());
            var player = seats.getFirst();
            var hand = new ArrayList<>(player.hand());
            hand.add(14);
            seats.set(0, seat(hand, player.melds(), player.river(), player.riichi()));
            update(table, seats, fixture.wall());
        }
        if (ticks == 420) client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
        if (layoutsOnly && ticks >= 420) return true;
        if (ticks >= 422) return deposits.tick(client, table, output);
        return false;
    }

    private void update(MahjongTableBlockEntity table, List<TableView.Seat> seats, List<Integer> wall) {
        update(table, seats, wall, 0);
    }

    private void update(MahjongTableBlockEntity table, List<TableView.Seat> seats, List<Integer> wall, int turn) {
        fixture = new TableView(fixture.tableId(), fixture.revision() + 1, fixture.decision() + 1, fixture.handNumber(), fixture.rules(),
            Game.Phase.TURN, 0, 0, 0, 0, seats.getFirst().riichi() ? 1 : 0, turn, 70, fixture.wallBreak(), wall, null,
            seats, List.of(), List.of(), "playing", List.of(), List.of(), fixture.timeControl(), fixture.clocks(), List.of(), top.skyeyefast.mchjong.engine.HandVisibility.SELF, null, null, fixture.autoPlay(), false, 1);
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
        double fov = ((GameRendererAccessor) client.gameRenderer).mchjong$getFov(camera, 1, true);
        double focal = client.screen.height / (2 * Math.tan(Math.toRadians(fov) / 2));
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
                    throw new IllegalStateException("Hand or right-corner meld is clipped or covered by HUD: " + piece
                        + " screen=" + screenX + "," + screenY + " depth=" + depth + " viewport="
                        + client.screen.width + "x" + client.screen.height + " fov=" + fov);
            }
        }
    }

    private static void capture(Minecraft client, Path output, String name) {
        Screenshot.grab(output.toFile(), name, client.getMainRenderTarget(), ignored -> {});
    }
}
