package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import top.skyeyefast.mchjong.client.MahjongButton;
import top.skyeyefast.mchjong.client.McrTableScreen;
import top.skyeyefast.mchjong.client.SichuanTableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.engine.McrAction;
import top.skyeyefast.mchjong.engine.McrGame;
import top.skyeyefast.mchjong.engine.McrSession;
import top.skyeyefast.mchjong.engine.McrView;
import top.skyeyefast.mchjong.engine.SichuanGame;
import top.skyeyefast.mchjong.engine.SichuanSession;
import top.skyeyefast.mchjong.engine.SichuanView;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.engine.TimeControl;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Display-only scorer fixtures exercise small seated and fixed immersive hint geometry. */
final class ConvenienceHintsSmoke {
    private int sample = -1, ticks, width, height, guiScale;
    private boolean finished;
    private TableRoomView room;
    private McrSession.View mcr;
    private SichuanSession.View sichuan;
    private TableSettings.DiscardMode discardMode;

    boolean finished() { return finished; }

    void tick(Minecraft client, MahjongTableBlockEntity table, Path output, boolean mcrRule) {
        if (sample < 0) {
            width = client.getWindow().getScreenWidth(); height = client.getWindow().getScreenHeight();
            guiScale = client.options.guiScale().get(); discardMode = TableSettings.get().discardMode;
            TableSettings.get().discardMode = TableSettings.DiscardMode.CONFIRM;
            room = table.clientTableRoom(); mcr = table.clientMcrView(); sichuan = table.clientSichuanView();
            if (!room.convenienceHints()) throw new IllegalStateException("Shared hint setting was not synchronized");
            sample = 0; show(client, table, mcrRule);
            return;
        }
        if (++ticks == 3) {
            var hint = hint(client);
            if (!hint.visible || !hint.active) throw new IllegalStateException("Scored hint fixture is unavailable: " + mcrRule + "/" + sample);
            client.screen.setFocused(hint);
        }
        if (ticks < 6) return;
        var hint = hint(client);
        if (!hint.isFocused()) throw new IllegalStateException("Scored hint fixture lost keyboard focus");
        String label = hint.getMessage().getString();
        String key = mcrRule ? "hints.mchjong.mcr.fan_basis" : "hints.mchjong.sichuan.passed_fan";
        if (!label.contains(net.minecraft.network.chat.Component.translatable(key, 3).getString()))
            throw new IllegalStateException("Scored hint narration is incomplete: " + label);
        checkHintBounds(client, hint);
        SmokeScreenshots.grab(output.toFile(), (mcrRule ? "mcr" : "sichuan") + "-scored-hints-"
            + (sample == 0 ? "seated.png" : "immersive.png"), client.getMainRenderTarget(), 1, ignored -> {});
        if (++sample < 2) { show(client, table, mcrRule); return; }
        client.getWindow().setWindowed(width, height); client.options.guiScale().set(guiScale); client.resizeGui();
        TableSettings.get().discardMode = discardMode;
        table.acceptRoom(room);
        if (mcrRule) {
            table.acceptMcrView(mcr, room, table.clientMcrDeck(), table.clientMcrCloth(), table.clientMcrTimeControl());
            client.setScreen(new McrTableScreen(table.getBlockPos()));
        } else {
            table.acceptSichuanView(sichuan, room, table.clientSichuanDeck(), table.clientSichuanCloth(), table.clientSichuanSettings());
            client.setScreen(new SichuanTableScreen(table.getBlockPos()));
        }
        finished = true;
    }

    private void show(Minecraft client, MahjongTableBlockEntity table, boolean mcrRule) {
        client.getWindow().setWindowed(sample == 0 ? 960 : 1280, sample == 0 ? 720 : 800);
        client.options.guiScale().set(sample == 0 ? 3 : 2); client.resizeGui(); ticks = 0;
        var fixtureRoom = new TableRoomView(room.tableId(), room.incarnation(), Long.MAX_VALUE / 4 + sample,
            room.decision(), room.variant(), room.lifecycle(), room.host(), room.viewerSeat(), false, true, false,
            room.seating(), List.of(), room.seats(), List.of(), null, false, true, true, room.automation());
        int viewer = room.viewerSeat();
        if (mcrRule) {
            var base = mcr.game();
            var hand = tiles("445566m2277779s"); hand.add(36);
            var seats = new ArrayList<McrView.Seat>();
            for (int seat = 0; seat < 4; seat++) seats.add(new McrView.Seat(base.seats().get(seat).wind(), 0,
                seat == viewer ? hand : Collections.nCopies(13, Tile.HIDDEN), Tile.ABSENT, List.of(), List.of(), List.of(), false));
            var game = new McrView(1, 1, base.handNumber(), McrGame.Phase.TURN, viewer, base.dealer(), base.roundWind(), viewer,
                base.remaining(), base.opening(), base.wall(), null, seats, List.of(new McrAction(McrAction.Type.DISCARD, 36)),
                false, false, null, List.of());
            var view = new McrSession.View(mcr.tableId(), mcr.incarnation(), fixtureRoom.revision(), mcr.participants(), 15, 0, false,
                Collections.nCopies(4, new TimeControl.Clock(0, 0, false)), 0, game);
            table.acceptMcrView(view, fixtureRoom, table.clientMcrDeck(), table.clientMcrCloth(), table.clientMcrTimeControl());
            client.setScreen(new McrTableScreen(table.getBlockPos(), sample == 1));
            var tileLabel = table.clientMcrDeck().tile(36).label(TableSettings.get().tileLabels == TableSettings.TileLabels.MPSZ, table.clientMcrDeck().preset());
            client.screen.children().stream().filter(MahjongButton.class::isInstance).map(MahjongButton.class::cast)
                .filter(button -> button.getClass().getSimpleName().equals("HandTarget") && button.getMessage().equals(tileLabel))
                .findFirst().orElseThrow().onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
        } else {
            var base = sichuan.game(); var seats = new ArrayList<SichuanView.Seat>();
            for (int seat = 0; seat < 4; seat++) seats.add(new SichuanView.Seat(seat == viewer ? tiles("1111222233334m")
                : Collections.nCopies(13, Tile.HIDDEN), List.of(), List.of(), 2, false, Tile.ABSENT, Tile.ABSENT));
            var game = new SichuanView(1, 1, base.rules(), SichuanGame.Phase.TURN, base.handNumber(), base.dealer(), base.scores(),
                viewer, (viewer + 1) % 4, base.wall(), seats, Tile.ABSENT, -1, false, false, List.of(), List.of(), List.of(), null, 3);
            var view = new SichuanSession.View(sichuan.tableId(), sichuan.incarnation(), fixtureRoom.revision(), false,
                Collections.nCopies(4, new TimeControl.Clock(0, 0, false)), 0, 0, game);
            table.acceptSichuanView(view, fixtureRoom, table.clientSichuanDeck(), table.clientSichuanCloth(), table.clientSichuanSettings());
            client.setScreen(new SichuanTableScreen(table.getBlockPos(), sample == 1));
        }
    }

    private static MahjongButton hint(Minecraft client) {
        return client.screen.children().stream().filter(MahjongButton.class::isInstance).map(MahjongButton.class::cast)
            .filter(button -> button.getClass().getSimpleName().equals("TableHints")).findFirst().orElseThrow();
    }

    private static void checkHintBounds(Minecraft client, MahjongButton hint) {
        boolean immersive = client.screen instanceof McrTableScreen screen ? screen.immersive()
            : ((SichuanTableScreen) client.screen).immersive();
        int width = immersive ? top.skyeyefast.mchjong.client.TableCanvas.WIDTH : client.screen.width;
        int height = immersive ? top.skyeyefast.mchjong.client.TableCanvas.HEIGHT : client.screen.height;
        if (hint.getX() < 0 || hint.getY() < 0 || hint.getRight() > width || hint.getBottom() > height)
            throw new IllegalStateException("Hint icon exceeds its layout canvas");
        for (var child : client.screen.children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
            && widget != hint && widget.visible && !widget.getClass().getSimpleName().equals("HandTarget")
            && hint.getRight() > widget.getX() && widget.getRight() > hint.getX()
            && hint.getBottom() > widget.getY() && widget.getBottom() > hint.getY())
            throw new IllegalStateException("Hint icon overlaps " + widget.getMessage().getString());
    }

    private static ArrayList<Integer> tiles(String notation) {
        var result = new ArrayList<Integer>(); var counts = new int[34]; var digits = new ArrayList<Integer>();
        for (char character : notation.toCharArray()) {
            if (Character.isDigit(character)) digits.add(character - '0');
            else {
                for (int digit : digits) {
                    int kind = Tile.parseKind("" + digit + character); result.add(kind * 4 + counts[kind]++);
                }
                digits.clear();
            }
        }
        return result;
    }
}
