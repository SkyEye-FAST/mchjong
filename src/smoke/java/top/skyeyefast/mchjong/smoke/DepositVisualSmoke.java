package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import top.skyeyefast.mchjong.client.TableDeposits;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.engine.Discard;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Display-only public fixtures on a real seated table, after its live input tests.
 * No action from these snapshots is ever sent to the server. */
final class DepositVisualSmoke {
    private int sample, ticks;
    private int windowWidth, windowHeight, guiScale;
    private TableView fixture;

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) {
        if (sample == 8) return true;
        if (ticks == 0) {
            if (sample == 0) {
                windowWidth = client.getWindow().getScreenWidth(); windowHeight = client.getWindow().getScreenHeight();
                guiScale = client.options.guiScale().get();
            }
            if (sample == 0 || sample == 4) {
                client.getWindow().setWindowed(sample == 0 ? 1280 : 960, sample == 0 ? 800 : 720);
                client.options.guiScale().set(sample == 0 ? 2 : 3); client.resizeDisplay();
            }
            var base = table.clientView();
            var rules = sample < 4 ? RuleSet.MAHJONG_SOUL_4 : RuleSet.MAHJONG_SOUL_3;
            int phase = sample % 4;
            int count = phase == 0 ? 8 : phase == 3 ? 0 : 8 + rules.players();
            boolean declared = phase == 1 || phase == 3;
            var seats = new ArrayList<TableView.Seat>();
            for (int seat = 0; seat < rules.players(); seat++) {
                var hand = seat == 0 ? IntStream.range(80, 93).boxed().toList() : Collections.nCopies(13, Tile.HIDDEN);
                var river = IntStream.range(seat * 12, seat * 12 + 12)
                    .mapToObj(tile -> new Discard(tile, tile % 12 == 0 && declared, false, false)).toList();
                seats.add(new TableView.Seat("Player " + (seat + 1), true, false, false, 25000,
                    hand, Tile.ABSENT, List.of(), river, List.of(), declared, false));
            }
            fixture = new TableView(base.tableId(), base.revision() + 1_000_000, base.decision(),
                base.handNumber() + (phase == 2 ? 1 : 0), rules, Game.Phase.TURN, 0, 0, 0, 0, count,
                0, 0, 0, List.of(), null, seats, List.of(), List.of(), "playing", List.of(), List.of(),
                base.timeControl(), List.of(), List.of(), false, null,
                table.automatic() ? null : new TableView.Handling(15, -1, 0), base.autoPlay());
            TableSettings.get().animations = phase == 1;
            table.acceptView(fixture);
            var screen = new TableScreen(table.getBlockPos());
            client.setScreen(screen);
            screen.resetView();
        }
        table.acceptView(fixture);
        ticks++;
        if (ticks == 4 && sample % 4 == 1) capture(client, table, output, "moving");
        if (ticks == 5 && sample % 4 == 1) TableSettings.get().animations = false;
        if (ticks == 7 && sample % 4 == 1) capture(client, table, output, "animation-disabled");
        if (ticks == 8 && sample % 4 == 1) TableSettings.get().animations = true;
        if (ticks == 20) {
            if (TableDeposits.sticks(fixture).size() != fixture.riichiSticks())
                throw new IllegalStateException("Rendered deposit count differs from the public pot");
            capture(client, table, output, switch (sample % 4) {
                case 0 -> "carried";
                case 1 -> "multiple-riichi";
                case 2 -> "next-hand-interrupted";
                default -> "awarded";
            });
            sample++;
            ticks = 0;
            if (sample == 8) {
                TableSettings.get().animations = true;
                client.getWindow().setWindowed(windowWidth, windowHeight);
                client.options.guiScale().set(guiScale); client.resizeDisplay();
                client.setScreen(null);
                return true;
            }
        }
        return false;
    }

    private void capture(Minecraft client, MahjongTableBlockEntity table, Path output, String state) {
        Screenshot.grab(output.toFile(), "55-deposits-" + (table.automatic() ? "automatic" : "ordinary")
            + "-" + fixture.rules().players() + "p-" + state + ".png", client.getMainRenderTarget(), ignored -> {});
    }
}
