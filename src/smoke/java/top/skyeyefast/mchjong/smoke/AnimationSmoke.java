package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import top.skyeyefast.mchjong.client.TableAnimation;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.engine.Discard;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Real renderer, synthetic public snapshots: no fixture action is sent to the live server. */
final class AnimationSmoke {
    private TableView fixture;
    private int ticks;
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
        if (ticks == 116) {
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

    private static void capture(Minecraft client, Path output, String name) {
        Screenshot.grab(output.toFile(), name, client.getMainRenderTarget(), ignored -> {});
    }
}
