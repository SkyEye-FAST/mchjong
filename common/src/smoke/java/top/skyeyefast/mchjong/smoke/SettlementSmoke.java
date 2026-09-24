package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import top.skyeyefast.mchjong.client.TableResults;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.HandScore;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Visual fixtures only; never replace the authoritative game or send fabricated actions. */
final class SettlementSmoke {
    private TableView fixture;
    private int ticks;
    private boolean animations;

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) {
        if (fixture == null) {
            animations = top.skyeyefast.mchjong.client.TableSettings.get().animations;
            top.skyeyefast.mchjong.client.TableSettings.get().animations = true;
            fixture = fixture(table.clientView());
            acceptFixture(table, fixture);
            client.setScreen(new TableScreen(table.getBlockPos()));
        }
        acceptFixture(table, fixture);
        ticks++;
        if (ticks == 10) {
            checkBounds(client);
            capture(client, output, "08-settlement.png");
            TableResults panel = panel(client);
            int span = panel.getWidth() - 20 - (panel.getWidth() >= 500 ? 156 : 0);
            client.screen.mouseClicked(panel.getX() + 10 + span * 3 / 4, panel.getY() + 25, 0);
        } else if (ticks == 20) {
            if (panel(client).selectedWinner() != 1) throw new IllegalStateException("Second winner was not selectable");
            capture(client, output, "09-settlement-details.png");
            client.options.guiScale().set(3);
            client.resizeDisplay();
        } else if (ticks == 30) {
            checkBounds(client);
            capture(client, output, "10-settlement-small.png");
            click(client, "View table");
        } else if (ticks == 35) {
            if (client.screen.children().stream().anyMatch(TableResults.class::isInstance))
                throw new IllegalStateException("Settlement could not be collapsed");
            click(client, "Show results");
        } else if (ticks == 40) {
            TableResults panel = panel(client);
            client.screen.mouseClicked(panel.getX() + 20, panel.getY() + 60, 0);
            client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0, 0);
        } else if (ticks == 45) {
            if (panel(client).selectedWinner() != 1) throw new IllegalStateException("Keyboard winner selection failed");
            capture(client, output, "11-settlement-keyboard.png");
            client.options.guiScale().set(4);
            client.resizeDisplay();
        } else if (ticks == 50) {
            checkBounds(client);
            capture(client, output, "15-settlement-smallest.png");
            click(client, "Point changes");
            checkSettledPoints(client);
            click(client, "Point changes");
            checkSettledPoints(client);
            ((TableScreen) client.screen).receivedView();
        } else if (ticks == 55) {
            checkBounds(client);
            checkSettledPoints(client);
            capture(client, output, "16-settlement-smallest-points.png");
            click(client, "Final standings");
        } else if (ticks == 60) {
            checkBounds(client);
            capture(client, output, "17-settlement-smallest-ranking.png");
            client.options.guiScale().set(2);
            client.resizeDisplay();
            click(client, "Point changes");
            checkSettledPoints(client);
        } else if (ticks == 70) {
            checkBounds(client);
            capture(client, output, "12-settlement-points.png");
            click(client, "Final standings");
        } else if (ticks == 95) {
            checkBounds(client);
            capture(client, output, "13-settlement-ranking.png");
            fixture = new TableView(fixture.tableId(), fixture.revision() + 1, fixture.decision() + 1,
                fixture.handNumber(), fixture.rules(), Game.Phase.HAND_END, fixture.viewerSeat(), fixture.dealer(),
                fixture.round(), fixture.honba(), fixture.riichiSticks(), fixture.turn(), fixture.remaining(),
                fixture.wallBreak(), fixture.wall(), null, fixture.seats(), List.of(),
                List.of(), "exhaustive", List.of(1500,1500,-1500,-1500), List.of(),
                fixture.timeControl(), fixture.clocks(), List.of(), top.skyeyefast.mchjong.engine.HandVisibility.SELF, null, null, fixture.autoPlay(), false, 1);
            acceptFixture(table, fixture);
            client.setScreen(new TableScreen(table.getBlockPos()));
        } else if (ticks == 100) {
            client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
            if (!((TableScreen) client.screen).immersive()) throw new IllegalStateException("Settlement did not enter immersive view");
        } else if (ticks == 105) {
            capture(client, output, "14-settlement-draw-immersive.png");
            client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
        } else if (ticks == 110) {
            checkBounds(client);
            capture(client, output, "14-settlement-draw.png");
            top.skyeyefast.mchjong.client.TableSettings.get().animations = animations;
            return true;
        }
        return false;
    }

    private static TableResults panel(Minecraft client) {
        return client.screen.children().stream().filter(TableResults.class::isInstance).map(TableResults.class::cast)
            .findFirst().orElseThrow(() -> new IllegalStateException("Missing settlement panel"));
    }

    private void checkSettledPoints(Minecraft client) {
        for (int seat = 0; seat < fixture.seats().size(); seat++)
            if (panel(client).displayedPoints(seat) != fixture.seats().get(seat).points())
                throw new IllegalStateException("Settlement score animation restarted after navigation or refresh");
    }

    private static void checkBounds(Minecraft client) {
        for (var child : client.screen.children()) if (child instanceof AbstractWidget widget)
            if (widget.getX() < 0 || widget.getY() < 0 || (widget.getX() + widget.getWidth()) > client.screen.width
                    || (widget.getY() + widget.getHeight()) > client.screen.height)
                throw new IllegalStateException("Widget outside resized settlement: " + widget.getMessage().getString());
    }

    private static void click(Minecraft client, String label) {
        var button = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
        client.screen.mouseClicked(button.getX() + 5, button.getY() + 5, 0);
    }

    private static void capture(Minecraft client, Path output, String name) {
        Screenshot.grab(output.toFile(), name, client.getMainRenderTarget(), ignored -> {});
    }

    static void acceptFixture(MahjongTableBlockEntity table, TableView view) {
        table.acceptView(view);
        var room = table.clientRoom();
        table.acceptRoom(new top.skyeyefast.mchjong.engine.RoomView(room.host(), room.invitationTeleport(),
            room.seating(), room.availableWinds(), room.seats(), view.phase() == Game.Phase.MATCH_END
                ? Game.SETTLEMENT_TICKS * 2 : view.phase() == Game.Phase.HAND_END ? Game.SETTLEMENT_TICKS : 0));
    }

    static TableView fixture(TableView base) {
        var seats = new ArrayList<TableView.Seat>();
        int[] points = {49000, 33000, -7000, 25000};
        for (int seat = 0; seat < 4; seat++) {
            var original = base.seats().get(seat);
            List<Integer> hand = seat < 2 ? List.of(0, 4, 8, 36, 40, 44, 72, 76, 80, 108, 109, 124, 125)
                : java.util.Collections.nCopies(13, Tile.HIDDEN);
            List<Meld> melds = switch (seat) {
                case 0 -> List.of(new Meld(Meld.Type.PON, List.of(16, 17, 18), 1, 16));
                case 1 -> List.of(new Meld(Meld.Type.OPEN_KAN, List.of(52, 53, 54, 55), 2, 52));
                case 2 -> List.of(new Meld(Meld.Type.CHI, List.of(88, 92, 96), 1, 88));
                default -> List.of();
            };
            seats.add(new TableView.Seat(false, seat == 0 ? "A player with a long display name" : "Player " + (seat + 1),
                true, false, false, points[seat], hand, Tile.ABSENT, melds, original.river(), List.of(), seat < 2, seat < 2));
        }
        var wins = List.of(new TableView.Win(0, 2, 126, new HandScore(8, 40, 0, 24000, 8000, 8000,
                List.of("Richi", "Ippatsu", "Chanta", "Sanshoku", "Haku", "SelfWind", "RoundWind"), 1)),
            new TableView.Win(1, 2, 126, new HandScore(5, 40, 0, 8000, 4000, 2000, List.of("Richi", "Chanta", "Haku"), 1)));
        return new TableView(base.tableId(), base.revision() + 10000, base.decision() + 10000, base.handNumber(), base.rules(), Game.Phase.MATCH_END,
            0, 0, 7, 0, 0, 2, base.remaining(), base.wallBreak(), base.wall(), null, seats,
            List.of(), wins, "ron",
            List.of(24000, 8000, -32000, 0), List.of(69.0, 13.0, -57.0, -25.0), base.timeControl(), base.clocks(), List.of(1, 2, 4, 3), top.skyeyefast.mchjong.engine.HandVisibility.SELF, null, null, base.autoPlay(), false, 1);
    }
}
