package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import top.skyeyefast.mchjong.client.McrTableScreen;
import top.skyeyefast.mchjong.client.SichuanTableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.engine.*;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Display-only full walls in the real seated world renderer, including the active center panel. */
public final class WallSeatedSmoke {
    public static boolean fullMcrWall;
    private int sample = -1, ticks;
    private boolean finished, animations;
    private TableRoomView room;
    private McrSession.View mcr;
    private SichuanSession.View sichuan;
    private SichuanRoomSettings settings;

    boolean finished() { return finished; }

    void tick(Minecraft client, MahjongTableBlockEntity table, Path output, boolean mcrRule) {
        if (sample < 0) {
            if (!(client.player.getVehicle() instanceof SeatEntity) || !table.automatic())
                throw new IllegalStateException("Wall capture requires a real automatic-table seat");
            room = table.clientTableRoom(); mcr = table.clientMcrView(); sichuan = table.clientSichuanView();
            settings = table.clientSichuanSettings();
            animations = TableSettings.get().animations;
            TableSettings.get().animations = false;
            TableSettings.get().camera().reset(TableGeometry.STOOL_DISTANCE, 2.10);
            sample = 0;
            show(client, table, mcrRule);
            return;
        }
        if (++ticks < 12) return;
        var seat = (SeatEntity) client.player.getVehicle();
        var expected = TableSettings.get().cameraPosition(seat);
        if (client.gameRenderer.getMainCamera().getPosition().distanceTo(expected) > .01)
            throw new IllegalStateException("Wall capture is not using the seated world camera");
        String name = mcrRule ? "mcr-wall-seated.png" : sample == 0
            ? "sichuan-wall-east-west-seated.png" : "sichuan-wall-north-south-seated.png";
        SmokeScreenshots.grab(output.toFile(), name, client.getMainRenderTarget(), ignored -> {});
        if (!mcrRule && ++sample < 2) { show(client, table, false); return; }
        table.acceptRoom(room);
        fullMcrWall = false;
        if (mcrRule) {
            table.acceptMcrView(mcr, room, table.clientMcrDeck(), table.clientMcrCloth(), table.clientMcrTimeControl());
            client.setScreen(new McrTableScreen(table.getBlockPos()));
        } else {
            table.acceptSichuanView(sichuan, room, table.clientSichuanDeck(), table.clientSichuanCloth(), settings);
            client.setScreen(new SichuanTableScreen(table.getBlockPos()));
        }
        TableSettings.get().animations = animations;
        finished = true;
    }

    private void show(Minecraft client, MahjongTableBlockEntity table, boolean mcrRule) {
        ticks = 0;
        var fixtureRoom = new TableRoomView(room.tableId(), room.incarnation(), Long.MAX_VALUE / 4 + sample,
            room.decision(), room.variant(), room.lifecycle(), room.host(), room.viewerSeat(), false, true, false,
            room.seating(), List.of(), room.seats(), List.of(), null, false, false, true, room.automation());
        var clocks = Collections.nCopies(4, new TimeControl.Clock(0, 0, false));
        if (mcrRule) {
            var base = mcr.game();
            // McrView represents post-deal state only. The smoke renderer supplies the unopened wall.
            fullMcrWall = true;
            var seats = base.seats().stream().map(player -> new McrView.Seat(player.wind(), 0,
                List.<Integer>of(), Tile.ABSENT, List.<Meld>of(), List.<McrDiscard>of(), List.<Integer>of(), false)).toList();
            var game = new McrView(1, 1, base.handNumber(), McrGame.Phase.TURN, room.viewerSeat(), base.dealer(),
                base.roundWind(), room.viewerSeat(), base.remaining(), base.opening(), base.wall(),
                null, seats, List.of(), false, false, null, List.of());
            var view = new McrSession.View(mcr.tableId(), mcr.incarnation(), fixtureRoom.revision(), mcr.participants(),
                15, 0, false, clocks, 0, game);
            table.acceptMcrView(view, fixtureRoom, table.clientMcrDeck(), table.clientMcrCloth(), table.clientMcrTimeControl());
            client.setScreen(new McrTableScreen(table.getBlockPos()));
        } else {
            var base = sichuan.game();
            var rules = (sample == 0 ? SichuanPreset.SBR_2025 : SichuanPreset.TFMJ_2024).config();
            var wall = new SichuanView.Wall(Collections.nCopies(108, Tile.HIDDEN), base.dealer(), 1, 1, rules.eastWestLongWall());
            var seats = Collections.nCopies(4, new SichuanView.Seat(List.of(), List.of(), List.of(),
                0, false, Tile.ABSENT, Tile.ABSENT));
            var game = new SichuanView(1, 1, rules, SichuanGame.Phase.TURN, base.handNumber(), base.dealer(), base.scores(),
                room.viewerSeat(), room.viewerSeat(), wall, seats, Tile.ABSENT, -1, false, false,
                List.of(), List.of(), List.of(), null, -1);
            var view = new SichuanSession.View(sichuan.tableId(), sichuan.incarnation(), fixtureRoom.revision(),
                false, clocks, 0, 0, game);
            table.acceptSichuanView(view, fixtureRoom, table.clientSichuanDeck(), table.clientSichuanCloth(),
                new SichuanRoomSettings(rules, settings.timeControl(), settings.rulesEditable()));
            client.setScreen(new SichuanTableScreen(table.getBlockPos()));
        }
    }
}
