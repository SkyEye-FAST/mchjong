package top.skyeyefast.mchjong.smoke;

import com.mojang.authlib.GameProfile;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import top.skyeyefast.mchjong.client.*;
import top.skyeyefast.mchjong.engine.*;
import top.skyeyefast.mchjong.network.*;
import top.skyeyefast.mchjong.world.*;

/** Real client packets, mounted human fixtures and a complete built-in Bot room. */
final class TaiwanTableSmoke {
    private final List<Guest> guests = new ArrayList<>();
    private final List<MahjongVariant> variants = List.of(MahjongVariant.MCR, MahjongVariant.SICHUAN, MahjongVariant.TAIWAN, MahjongVariant.RIICHI, MahjongVariant.TAIWAN);
    private CompletableFuture<?> task;
    private int stage, ticks, choice, captures;
    private boolean called;
    private long sentDecision = -1;
    private List<TaiwanGameState.Action> sentActions = List.of();
    private int selectedTile;
    private UUID incarnation;
    private UUID replayId;

    boolean tick(Minecraft client, BlockPos pos, Path output) {
        check(++ticks < 6600, "Taiwan smoke timed out at " + stage);
        if (task != null && !task.isDone()) return false;
        if (task != null) { task.join(); task = null; }
        var table = (MahjongTableBlockEntity) client.level.getBlockEntity(pos);
        var server = client.getSingleplayerServer();
        var room = table.clientTableRoom(); var view = table.clientTaiwanView();
        switch (stage) {
            case 0 -> { task = server.submit(() -> target(client, pos).sit(main(client), 0)); stage++; }
            case 1 -> {
                if (room == null || room.viewerSeat() < 0) break;
                if (!room.convenienceHints()) {
                    client.getConnection().send(PayloadPackets.serverbound(new TableSessionControlPayload(pos, room.tableId(),
                        TableSessionControlPayload.Operation.CONVENIENCE_HINTS, room.decision(), true)));
                    break;
                }
                if (choice == variants.size()) { stage = 3; break; }
                client.getConnection().send(PayloadPackets.serverbound(new TableVariantPayload(pos, room.tableId(), room.incarnation(), room.decision(), variants.get(choice))));
                stage++;
            }
            case 2 -> {
                if (room == null || room.variant() != variants.get(choice)) break;
                check(switch (room.variant()) {
                    case RIICHI -> client.screen instanceof RiichiTableScreen;
                    case MCR -> client.screen instanceof McrLobbyScreen;
                    case SICHUAN -> client.screen instanceof SichuanLobbyScreen;
                    case TAIWAN -> client.screen instanceof TaiwanLobbyScreen;
                }, "Variant switch did not route its lobby");
                check(room.convenienceHints(), "Variant switch lost shared hints");
                choice++; stage = 1;
            }
            case 3 -> {
                check(client.screen instanceof TaiwanLobbyScreen, "Missing Taiwan lobby");
                client.getWindow().setWindowed(960, 720); client.options.guiScale().set(3); client.resizeDisplay();
                stage++;
            }
            case 4 -> {
                if (++captures < 12) break;
                check(client.screen.width == 320 && client.screen.height == 240, "Taiwan lobby minimum viewport missing");
                AutomationControlsSmoke.checkBounds(client);
                client.setScreen(new TaiwanRulesScreen(client.screen, pos));
                captures = 0; stage = 30;
            }
            case 30 -> {
                if (++captures < 12) break;
                SmokeScreenshots.grab(output.toFile(), "taiwan-rules.png", client.getMainRenderTarget(), ignored -> {});
                RuleExplanationSmoke.check(client, net.minecraft.network.chat.Component.translatable("taiwan.mchjong.preset.pocket_common"));
                press(client, "taiwan.mchjong.payment_rules");
                var payment = client.screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
                payment.setValue("");
                RuleExplanationSmoke.check(client, net.minecraft.network.chat.Component.translatable("taiwan.mchjong.payment.base"));
                payment = client.screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
                check(payment.getValue().isEmpty(), "Explanation lost incomplete payment input");
                payment.setValue("1"); press(client, "taiwan.mchjong.presets");
                press(client, "taiwan.mchjong.preset.southern_common"); press(client, "gui.done"); stage = 5;
            }
            case 5 -> {
                if (!(client.screen instanceof TaiwanLobbyScreen) || table.clientTaiwanSettings().rules().flowers() != TaiwanRules.Flowers.NONE) break;
                task = server.submit(() -> {
                    var t = target(client, pos); var player = main(client);
                    var box = MahjongSuppliesBox.withoutFlowers(t.equipment().boxes().getItem(0));
                    t.equipment().boxes().setItem(0, box);
                    check(t.equipment().taiwanStock(TaiwanPreset.SOUTHERN_COMMON.rules()).tiles().size() == 136, "Southern stock rejected");
                    check(t.equipment().taiwanStock(TaiwanPreset.POCKET_COMMON.rules()) == null, "Missing flowers admitted to Pocket");
                    t.equipment().boxes().setItem(0, top.skyeyefast.mchjong.item.MahjongSupplies.stockedBox(RedFives.NONE));
                    check(t.equipment().taiwanStock(TaiwanPreset.POCKET_COMMON.rules()).tiles().size() == 144, "Pocket stock rejected");
                    var before = t.roomView(player);
                    for (var payload : List.of(new TaiwanRulesPayload(pos, before.tableId(), UUID.randomUUID(), before.decision(), TaiwanGameState.Rules.of(TaiwanPreset.POCKET_COMMON.rules())),
                        new TaiwanRulesPayload(pos, before.tableId(), before.incarnation(), before.decision() - 1, TaiwanGameState.Rules.of(TaiwanPreset.POCKET_COMMON.rules())))) TableNetworking.receive(player, payload);
                    check(t.roomView(player).revision() == before.revision(), "Stale rules changed Taiwan");
                    TableNetworking.receive(player, new TableVariantPayload(pos, before.tableId(), UUID.randomUUID(), before.decision(), MahjongVariant.MCR));
                    check(t.roomView(player).variant() == MahjongVariant.TAIWAN, "Stale variant changed Taiwan");
                    for (int s = 1; s < 4; s++) {
                        var guest = new Guest(player, s); guests.add(guest); guest.setPos(pos.getCenter());
                        player.serverLevel().addNewPlayer(guest); server.getPlayerList().getPlayers().add(guest); t.sit(guest, s);
                    }
                    // Deterministic future seed only, never manufactured hands or settlements.
                    var saved = t.saveWithoutMetadata(player.registryAccess());
                    var envelope = com.google.gson.JsonParser.parseString(new String(saved.getByteArray("session"), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    envelope.getAsJsonObject("state").getAsJsonObject("room").addProperty("seed", 4);
                    saved.putByteArray("session", envelope.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    t.loadWithComponents(saved, player.registryAccess()); t.open(player);
                });
                stage++;
            }
            case 6 -> {
                if (room == null || room.seats().stream().anyMatch(s -> s.participant().id() == null)) break;
                client.setScreen(new TaiwanRulesScreen(client.screen, pos));
                press(client, "taiwan.mchjong.preset.pocket_common"); press(client, "gui.done"); stage++;
            }
            case 7 -> {
                if (!(client.screen instanceof TaiwanLobbyScreen) || table.clientTaiwanSettings().rules().flowers() == TaiwanRules.Flowers.NONE) break;
                client.getConnection().send(PayloadPackets.serverbound(new TaiwanClockPayload(pos, room.tableId(), room.incarnation(), room.decision(), new TimeControl(120, 30)))); stage++;
            }
            case 8 -> {
                if (!table.clientTaiwanSettings().timeControl().equals(new TimeControl(120, 30))) break;
                int action = room.actions().indexOf(new RoomAction(RoomAction.Type.BEGIN_SEATING)); check(action >= 0, "Taiwan cannot prepare four humans");
                client.getConnection().send(PayloadPackets.serverbound(new TableRoomActionPayload(pos, room.tableId(), room.incarnation(), room.decision(), action))); stage++;
            }
            case 9 -> {
                if (room.seating() != RoomSeating.Stage.POSITIONING) break;
                task = server.submit(() -> {
                    var t = target(client, pos); var player = main(client);
                    var players = players(player);
                    for (var p : players) if (p.getVehicle() instanceof SeatEntity seat) { p.stopRiding(); seat.discard(); }
                    for (var p : players) t.sit(p, t.participantRoom(player).seatOf(p.getUUID()));
                    for (var guest : guests) {
                        var r = t.roomView(guest); int action = r.actions().indexOf(new RoomAction(RoomAction.Type.READY)); check(action >= 0, "Guest cannot ready");
                        TableNetworking.receive(guest, new TableRoomActionPayload(pos, r.tableId(), r.incarnation(), r.decision(), action));
                    }
                    t.open(player);
                }); stage++;
            }
            case 10 -> {
                if (room.seats().stream().filter(s -> s.participant().ready()).count() != 3 || room.viewerSeat() < 0) break;
                int action = room.actions().indexOf(new RoomAction(RoomAction.Type.READY)); check(action >= 0, "Real client cannot ready");
                client.getConnection().send(PayloadPackets.serverbound(new TableRoomActionPayload(pos, room.tableId(), room.incarnation(), room.decision(), action))); stage++;
            }
            case 11 -> {
                if (!(client.screen instanceof TaiwanTableScreen) || view == null) break;
                check(view.game().wall().size() == 144 && room.seats().stream().allMatch(s -> s.presence() == PlayerPresence.SEATED), "Taiwan match missing mounted humans");
                check(view.game().seats().stream().mapToInt(s -> s.flowers().size()).sum() > 0, "Initial Taiwan flowers were not replaced");
                task = server.submit(() -> {
                    var t = target(client, pos); var player = main(client); var before = t.taiwanView(player); incarnation = before.incarnation();
                    checkPrivacy(t, player);
                    for (var rejected : List.of(new TaiwanActionPayload(pos, before.tableId(), UUID.randomUUID(), before.game().decision(), 0),
                        new TaiwanActionPayload(pos, before.tableId(), before.incarnation(), before.game().decision() - 1, 0))) TableNetworking.receive(player, rejected);
                    check(t.taiwanView(player).revision() == before.revision(), "Stale action changed Taiwan");
                    var r = t.roomView(player);
                    TableNetworking.receive(player, new TableSessionControlPayload(pos, r.tableId(), TableSessionControlPayload.Operation.REQUEST_EXIT, r.decision(), false));
                    check(t.taiwanView(player).paused() && t.taiwanView(player).game().actions().isEmpty(), "Exit vote did not pause Taiwan");
                    var vote = t.roomView(guests.getFirst()).exitVote();
                    TableNetworking.receive(guests.getFirst(), new TableSessionControlPayload(pos, r.tableId(), TableSessionControlPayload.Operation.ANSWER_EXIT, vote.id(), false));
                    for (int i = 0; i < 50 && t.taiwanView(player).game().actions().stream().noneMatch(a -> a.type() == TaiwanAction.Type.DISCARD); i++) driveOne(t, player, pos);
                    t.open(player);
                }); stage++;
            }
            case 12 -> {
                if (!(client.screen instanceof TaiwanTableScreen screen) || view.game().actions().stream().noneMatch(a -> a.type() == TaiwanAction.Type.DISCARD)) break;
                if (++captures < 24) break;
                SmokeScreenshots.grab(output.toFile(), "taiwan-table.png", client.getMainRenderTarget(), ignored -> {});
                client.getWindow().setWindowed(1280, 800); client.options.guiScale().set(2); client.resizeDisplay();
                TableSettings.get().discardMode = TableSettings.DiscardMode.CONFIRM;
                selectedTile = view.game().actions().stream().filter(a -> a.type() == TaiwanAction.Type.DISCARD).findFirst().orElseThrow().tiles().getFirst();
                var piece = TaiwanTableScene.build(view.game()).stream().filter(p -> p.area() == TaiwanTableScene.Area.HAND && p.seat() == view.game().recipient() && p.tile() == selectedTile).findFirst().orElseThrow();
                var pointer = project(client, pos, piece.position());
                check(screen.mouseClicked(pointer.x, pointer.y, 0) && screen.selected(piece), "Taiwan seated picking failed");
                captures = 0; stage = 51;
            }
            case 51 -> {
                var screen = (TaiwanTableScreen) client.screen;
                if (++captures < 6) break;
                var hint = hint(screen);
                checkHint(hint, screen);
                screen.setFocused(hint); captures = 0; stage = 52;
            }
            case 52 -> {
                var screen = (TaiwanTableScreen) client.screen;
                if (++captures < 6) break;
                check(hint(screen).isFocused(), "Taiwan seated hints lost focus");
                SmokeScreenshots.grab(output.toFile(), "taiwan-hints-seated.png", client.getMainRenderTarget(), ignored -> {});
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0); captures = 0; stage = 13;
            }
            case 13 -> {
                if (!(client.screen instanceof TaiwanTableScreen screen) || !screen.immersive()) break;
                SmokeScreenshots.grab(output.toFile(), "taiwan-immersive.png", client.getMainRenderTarget(), ignored -> {});
                if (++captures < 12) break;
                var hint = hint(screen);
                checkHint(hint, screen);
                screen.setFocused(hint);
                if (captures < 18) break;
                check(hint.isFocused(), "Taiwan immersive hints lost focus");
                SmokeScreenshots.grab(output.toFile(), "taiwan-hints-immersive.png", client.getMainRenderTarget(), ignored -> {});
                screen.setFocused(null);
                var own = view.game().seats().get(view.game().recipient());
                var tiles = new ArrayList<>(own.concealed());
                if (own.drawn() >= 0 && tiles.remove(Integer.valueOf(own.drawn()))) tiles.add(own.drawn());
                int w = Math.min(58, 1244 / Math.max(17, tiles.size())), gap = own.drawn() < 0 ? 0 : Math.max(18, w / 2);
                int left = (1280 - tiles.size() * w - gap) / 2;
                double scale = Math.min(screen.width / 1280.0, screen.height / 800.0);
                double x = (screen.width - 1280 * scale) / 2 + (left + tiles.indexOf(selectedTile) * w + (selectedTile == own.drawn() ? gap : 0) + w / 2.0) * scale;
                double y = (screen.height - 800 * scale) / 2 + (725 - Math.round(w * top.skyeyefast.mchjong.client.TileDimensions.SMALL.height() / top.skyeyefast.mchjong.client.TileDimensions.SMALL.width()) / 2.0) * scale;
                check(screen.mouseClicked(x, y, 0), "Taiwan immersive picking failed");
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                var selectedPiece = TaiwanTableScene.build(view.game()).stream().filter(p -> p.area() == TaiwanTableScene.Area.HAND && p.seat() == view.game().recipient() && p.tile() == selectedTile).findFirst().orElseThrow();
                check(screen.selected(selectedPiece), "Taiwan immersive selected wrong tile");
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                task = server.submit(() -> setHintsPolicy(server, false)); stage = 53;
            }
            case 53 -> {
                if (table.clientWorldPolicy().allowConvenienceHints() || room.convenienceHints()) break;
                var screen = (TaiwanTableScreen) client.screen;
                check(!hint(screen).visible && !hint(screen).active && !hint(screen).isFocused(), "World policy left Taiwan hints visible");
                task = server.submit(() -> setHintsPolicy(server, true)); stage = 54;
            }
            case 54 -> {
                if (!table.clientWorldPolicy().allowConvenienceHints()) break;
                var screen = (TaiwanTableScreen) client.screen;
                check(!room.convenienceHints() && !hint(screen).visible, "Disabled room hints reappeared after world policy restored");
                screen.setFocused(null);
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0); sentDecision = view.game().decision(); stage = 14;
            }
            case 14 -> {
                if (view.game().decision() == sentDecision) break;
                task = server.submit(() -> {
                    var t = target(client, pos); var player = main(client); var before = t.taiwanView(player);
                    var saved = t.saveWithoutMetadata(player.registryAccess()); t.loadWithComponents(saved, player.registryAccess());
                    var after = t.taiwanView(player); check(!after.incarnation().equals(incarnation), "NBT restored old incarnation");
                    check(after.scores().equals(before.scores()) && after.game().wall().equals(before.game().wall()), "NBT changed Taiwan position");
                    TableNetworking.receive(player, new TaiwanActionPayload(pos, before.tableId(), before.incarnation(), before.game().decision(), 0));
                    check(t.taiwanView(player).revision() == after.revision(), "Pre-restore request accepted");
                    checkPrivacy(t, player); t.open(player);
                }); stage++;
            }
            case 15 -> {
                if (view == null) break;
                if (view.game().result() != null) { captures = 0; stage++; break; }
                if (!view.game().actions().isEmpty()) {
                    // Every real player's first-hand choice crosses the registered Minecraft wire.
                    if (sentDecision != view.game().decision() || !sentActions.equals(view.game().actions())) {
                        int action = choose(view.game()); send(client, pos, view, action); sentDecision = view.game().decision(); sentActions = view.game().actions();
                    }
                } else task = server.submit(() -> driveOne(target(client, pos), main(client), pos));
            }
            case 16 -> {
                if (!(client.screen instanceof TaiwanResultsScreen)) break;
                if (++captures == 1) press(client, "ui.mchjong.result_page.0");
                if (captures == 12) SmokeScreenshots.grab(output.toFile(), "taiwan-results.png", client.getMainRenderTarget(), ignored -> {});
                if (captures == 13 && !view.game().result().transfers().isEmpty()) press(client, "ui.mchjong.result_page.3");
                if (captures < 24) break;
                SmokeScreenshots.grab(output.toFile(), "taiwan-payments.png", client.getMainRenderTarget(), ignored -> {});
                press(client, "taiwan.mchjong.next_hand");
                var payload = new TaiwanNextHandPayload(pos, view.tableId(), view.incarnation(), view.game().decision());
                client.getConnection().send(PayloadPackets.serverbound(payload)); stage++;
            }
            case 17 -> {
                if (view.handNumber() != 1 || (view.confirmed() & 1 << view.game().recipient()) == 0) break;
                task = server.submit(() -> {
                    var t = target(client, pos); var player = main(client);
                    for (var guest : guests) confirm(t, guest, pos);
                    check(t.taiwanView(player).handNumber() == 2, "Confirmations did not advance Taiwan once");
                    checkPrivacy(t, player);
                    // Complete all sixteen dealer positions via the same authorized handlers.
                    for (int moves = 0; moves < 60000; moves++) {
                        var current = t.taiwanView(player);
                        if (current.lifecycle() == TableSession.Lifecycle.FINISHED) break;
                        if (current.game().result() != null) for (var p : players(player)) confirm(t, p, pos);
                        else driveOne(t, player, pos);
                    }
                    var end = t.taiwanView(player);
                    check(end.lifecycle() == TableSession.Lifecycle.FINISHED && end.game().roundWind() == Tile.NORTH && end.game().opening().dealer() == 3, "Taiwan four-wind match did not finish");
                    check(called, "Taiwan smoke never completed a call/kong");
                    var saved = t.saveWithoutMetadata(player.registryAccess()); t.loadWithComponents(saved, player.registryAccess());
                    check(t.taiwanView(player).scores().equals(end.scores()), "Final save repaid Taiwan");
                    var session = (TaiwanSession) t.participantRoom(player);
                    replayId = session.save().replay().id();
                    try { top.skyeyefast.mchjong.replay.ReplayServer.flush(server,session); }
                    catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                    check(session.pendingReplays().isEmpty(),"Taiwan replay archive was not acknowledged");
                    t.open(player);
                }); captures = 0; stage++;
            }
            case 18 -> {
                if (view == null || view.lifecycle() != TableSession.Lifecycle.FINISHED || !(client.screen instanceof TaiwanResultsScreen)) break;
                if (++captures == 1) press(client, "ui.mchjong.result_page.2");
                if (captures < 12) break;
                SmokeScreenshots.grab(output.toFile(), "taiwan-match-end.png", client.getMainRenderTarget(), ignored -> {});
                ClientReplays.list(0,replayId.toString(),false); captures = 0; stage = 40;
            }
            case 40 -> {
                if (!(client.screen instanceof ReplayBrowserScreen)) break;
                if (++captures < 12) break;
                press(client,"replay.mchjong.open"); stage++;
            }
            case 41 -> {
                if (!(client.screen instanceof ReplayScreen replay)) break;
                check(replay.match().id().equals(replayId) && replay.match().variant() == MahjongVariant.TAIWAN
                    && replay.match().complete() && replay.match().header().taiwanScores().size() == 4,"Browser did not retrieve native Taiwan match");
                check(replay.cursor() == 0,"Taiwan replay did not start at opening");
                replay.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT,0,0);
                replay.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT,0,0);
                check(replay.cursor() == 2,"Taiwan replay event stepping failed");
                int viewer = replay.viewerSeat();
                replay.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V,0,0);
                replay.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V,0,0);
                check(replay.viewerSeat() == (viewer+2)%4,"Taiwan replay viewpoint switching failed");
                var frames = TaiwanReplayPlayback.timeline(replay.match(),0).frames();
                int flower = -1;
                for (int i = 0; i < frames.size(); i++) if (frames.get(i).event().kind() == TaiwanReplayHand.Kind.FLOWER) { flower = i; break; }
                check(flower >= 0,"Taiwan opening flower missing from playback");
                replay.seek(flower);
                var event = frames.get(flower).event();
                check(frames.get(flower).state().players().get(event.seat()).flowers().stream().anyMatch(f -> f.id() == event.tile()),
                    "Taiwan playback flower did not reach public rail");
                captures = 0; stage++;
            }
            case 42 -> {
                if (!(client.screen instanceof ReplayScreen replay)) break;
                if (++captures < 12) break;
                SmokeScreenshots.grab(output.toFile(),"taiwan-replay.png",client.getMainRenderTarget(),ignored -> {});
                replay.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_END,0,0);
                press(client,"ui.mchjong.result_page.0"); captures = 0; stage++;
            }
            case 43 -> {
                if (!(client.screen instanceof ReplayScreen replay)) break;
                if (++captures < 12) break;
                check(replay.cursor() == TaiwanReplayPlayback.timeline(replay.match(),0).frames().size()-1,"Taiwan replay settlement seek failed");
                SmokeScreenshots.grab(output.toFile(),"taiwan-replay-settlement.png",client.getMainRenderTarget(),ignored -> {});
                replay.onClose(); check(client.screen instanceof ReplayBrowserScreen,"Taiwan replay did not return to browser");
                client.screen.onClose();
                task = server.submit(() -> botMatch(target(client,pos),main(client),pos)); stage = 19;
            }
            case 19 -> {
                if (view == null || view.lifecycle() != TableSession.Lifecycle.FINISHED || !(client.screen instanceof TaiwanResultsScreen)) break;
                check(room.seats().stream().filter(s -> s.participant().bot()).count() == 3, "Taiwan final Bot roster missing");
                check(view.game().roundWind() == Tile.NORTH && view.game().opening().dealer() == 3, "Taiwan Bot match did not complete four winds");
                return true;
            }
            default -> throw new IllegalStateException("Bad Taiwan smoke stage");
        }
        return false;
    }

    private static MahjongButton hint(TaiwanTableScreen screen) {
        return screen.children().stream().filter(child -> child instanceof MahjongButton
            && child.getClass().getSimpleName().equals("TableHints")).map(child -> (MahjongButton) child).findFirst().orElseThrow();
    }
    private static void checkHint(MahjongButton hint, TaiwanTableScreen screen) {
        check(hint.visible && hint.active, "Taiwan selected discard has no convenience preview");
        check(hint.getMessage().getString().contains(net.minecraft.network.chat.Component.translatable(
            "hints.mchjong.after_discard", "").getString()), "Taiwan hints lost discard preview narration");
        int width = screen.immersive() ? TableCanvas.WIDTH : screen.width;
        int height = screen.immersive() ? TableCanvas.HEIGHT : screen.height;
        check(hint.getX() >= 0 && hint.getY() >= 0 && hint.getRight() <= width && hint.getBottom() <= height,
            "Taiwan hint icon exceeds its canvas");
    }
    private static void setHintsPolicy(net.minecraft.server.MinecraftServer server, boolean allowed) {
        try { WorldSettings.of(server).set("allowConvenienceHints", allowed); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    private ServerPlayer main(Minecraft client) { return client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID()); }
    private MahjongTableBlockEntity target(Minecraft client, BlockPos pos) { return (MahjongTableBlockEntity) main(client).serverLevel().getBlockEntity(pos); }
    private List<ServerPlayer> players(ServerPlayer main) { var all = new ArrayList<ServerPlayer>(guests); all.add(main); return all; }
    private static void roomAction(MahjongTableBlockEntity table, ServerPlayer player, BlockPos pos, RoomAction action) {
        var room = table.roomView(player);
        int index = room.actions().indexOf(action); check(index >= 0, "Missing Taiwan room action " + action);
        TableNetworking.receive(player, new TableRoomActionPayload(pos, room.tableId(), room.incarnation(), room.decision(), index));
    }
    private void botMatch(MahjongTableBlockEntity table, ServerPlayer player, BlockPos pos) {
        roomAction(table, player, pos, new RoomAction(RoomAction.Type.RETURN_TO_LOBBY));
        for (var guest : guests) {
            roomAction(table, guest, pos, new RoomAction(RoomAction.Type.LEAVE_ROOM));
            if (guest.getVehicle() instanceof SeatEntity seat) { guest.stopRiding(); seat.discard(); }
            player.server.getPlayerList().getPlayers().remove(guest); guest.discard();
        }
        guests.clear();
        var room = table.roomView(player);
        int target = (room.viewerSeat() + 1) % 4;
        roomAction(table, player, pos, new RoomAction(RoomAction.Type.SET_BOT, List.of(target, 0)));
        check(table.roomView(player).seats().get(target).participant().difficulty() == BotDifficulty.EASY, "Taiwan built-in Bot packet rejected");
        check(table.roomView(player).actions().stream().filter(a -> a.type() == RoomAction.Type.SET_BOT).allMatch(a -> a.arguments().get(1) == 0), "Taiwan exposed an unsupported Bot choice");
        roomAction(table, player, pos, new RoomAction(RoomAction.Type.REMOVE_BOT, target));
        roomAction(table, player, pos, new RoomAction(RoomAction.Type.FILL_BOTS));
        var roster = table.roomView(player).seats().stream().map(TableRoomView.Seat::participant).toList();
        check(roster.stream().filter(TableParticipant::bot).count() == 3 && roster.stream().filter(TableParticipant::bot).allMatch(TableParticipant::ready), "Taiwan Bots not automatically ready");
        var saved = table.saveWithoutMetadata(player.registryAccess()); table.loadWithComponents(saved, player.registryAccess());
        check(table.roomView(player).seats().stream().map(TableRoomView.Seat::participant).toList().equals(roster), "Taiwan NBT lost Bot roster");
        room = table.roomView(player);
        TableNetworking.receive(player, new TaiwanRulesPayload(pos, room.tableId(), room.incarnation(), room.decision(), TaiwanGameState.Rules.of(TaiwanPreset.SOUTHERN_COMMON.rules())));
        roomAction(table, player, pos, new RoomAction(RoomAction.Type.BEGIN_SEATING));
        if (player.getVehicle() instanceof SeatEntity seat) { player.stopRiding(); seat.discard(); }
        table.sit(player, table.participantRoom(player).seatOf(player.getUUID()));
        roomAction(table, player, pos, new RoomAction(RoomAction.Type.READY));
        int hands = 0;
        for (int ticks = 0; ticks < 200_000; ticks++) {
            var view = table.taiwanView(player);
            if (view.lifecycle() == TableSession.Lifecycle.FINISHED) break;
            var session = (TaiwanSession) table.participantRoom(player);
            session.game().checkConservation();
            if (view.game().result() != null) {
                check(++hands < 250, "Taiwan Bot match did not progress");
                for (int seat = 0; seat < 4; seat++) if (session.trainingSeat(seat)) check((view.confirmed() & 1 << seat) != 0, "Taiwan Bot result unconfirmed");
                var before = view.scores();
                saved = table.saveWithoutMetadata(player.registryAccess()); table.loadWithComponents(saved, player.registryAccess());
                check(table.taiwanView(player).scores().equals(before), "Taiwan Bot restore repaid result");
                confirm(table, player, pos);
            } else {
                if (!view.game().actions().isEmpty()) TableNetworking.receive(player,
                    new TaiwanActionPayload(pos, view.tableId(), view.incarnation(), view.game().decision(), TaiwanBot.choose(view.game())));
                ((TaiwanSession) table.participantRoom(player)).tick();
            }
        }
        var end = table.taiwanView(player);
        check(end.lifecycle() == TableSession.Lifecycle.FINISHED && end.scores().stream().mapToLong(Long::longValue).sum() == 0, "Taiwan Bot match did not finish with balanced scores");
        var session = (TaiwanSession) table.participantRoom(player); var replay = session.save().replay();
        check(replay.complete() && replay.participants().stream().filter(ReplayMatch.Participant::bot).count() == 3
            && replay.header().taiwanScores().equals(end.scores()),"Taiwan Bot replay lost roster or final standings");
        ReplayCodec.validate(replay);
        checkPrivacy(table, player); table.open(player);
    }
    private void driveOne(MahjongTableBlockEntity table, ServerPlayer main, BlockPos pos) {
        // The queued client request can finish the hand before this server task runs.
        if (table.taiwanView(main).game().result() != null) return;
        for (var player : players(main)) {
            var view = table.taiwanView(player); if (view.game().actions().isEmpty()) continue;
            int action = choose(view.game());
            if (!called) for (int i = 0; i < view.game().actions().size(); i++) {
                var a = view.game().actions().get(i).type();
                if (a == TaiwanAction.Type.CHOW || a == TaiwanAction.Type.PONG || a == TaiwanAction.Type.OPEN_KONG || a == TaiwanAction.Type.CONCEALED_KONG || a == TaiwanAction.Type.ADDED_KONG) { action = i; break; }
            }
            long revision = view.revision();
            TableNetworking.receive(player, new TaiwanActionPayload(pos, view.tableId(), view.incarnation(), view.game().decision(), action));
            var after = table.taiwanView(player);
            called |= after.game().seats().stream().anyMatch(s -> !s.melds().isEmpty());
            check(after.revision() > revision, "Issued Taiwan action rejected"); return;
        }
        throw new IllegalStateException("Taiwan decision stalled");
    }
    private static void confirm(MahjongTableBlockEntity table, ServerPlayer player, BlockPos pos) {
        var view = table.taiwanView(player);
        TableNetworking.receive(player, new TaiwanNextHandPayload(pos, view.tableId(), view.incarnation(), view.game().decision()));
    }
    private void checkPrivacy(MahjongTableBlockEntity table, ServerPlayer main) {
        for (var player : players(main)) {
            var view = table.taiwanView(player); int own = view.game().recipient();
            for (int seat = 0; seat < 4; seat++) if (seat != own) check(view.game().seats().get(seat).concealed().isEmpty() && view.game().seats().get(seat).drawn() == Tile.ABSENT, "Taiwan opponent private tiles leaked");
        }
        var spectator = table.taiwanView(new Guest(main, 9));
        check(spectator.game().recipient() == -1 && spectator.game().actions().isEmpty() && spectator.clock() == null && spectator.game().seats().stream().allMatch(s -> s.concealed().isEmpty()), "Taiwan spectator privacy failed");
        check(spectator.game().wall().stream().allMatch(t -> t == Tile.HIDDEN || t == Tile.ABSENT), "Taiwan wall identity leaked");
    }
    private static int choose(TaiwanView view) {
        var actions = view.actions();
        for (int i = 0; i < actions.size(); i++) if (actions.get(i).type() == TaiwanAction.Type.WIN) return i;
        if (view.phase() == TaiwanGame.Phase.REACTION) for (int i = 0; i < actions.size(); i++) if (actions.get(i).type() == TaiwanAction.Type.PASS) return i;
        for (int i = 0; i < actions.size(); i++) if (actions.get(i).type() == TaiwanAction.Type.READY_DISCARD) return i;
        var own = view.seats().get(view.recipient()); int best = -1, distance = Integer.MAX_VALUE, support = Integer.MAX_VALUE;
        var kinds = new HashSet<Integer>();
        for (int i = 0; i < actions.size(); i++) if (actions.get(i).type() == TaiwanAction.Type.DISCARD) {
            int tile = actions.get(i).tiles().getFirst(), kind = Tile.kind(tile); if (!kinds.add(kind)) continue;
            var hand = new ArrayList<>(own.concealed()); hand.remove(Integer.valueOf(tile));
            int d = TaiwanHandAnalyzer.shanten(hand, own.melds(), view.recipient());
            int s = 2 * (int) hand.stream().filter(t -> Tile.kind(t) == kind).count();
            if (kind < 27) s += (int) hand.stream().filter(t -> Tile.kind(t) / 9 == kind / 9 && Math.abs(Tile.kind(t) - kind) <= 2 && Tile.kind(t) != kind).count();
            if (d < distance || d == distance && s < support) { best = i; distance = d; support = s; }
        }
        check(best >= 0, "No Taiwan legal discard"); return best;
    }
    private static void send(Minecraft client, BlockPos pos, TaiwanSession.View view, int action) {
        client.getConnection().send(PayloadPackets.serverbound(new TaiwanActionPayload(pos, view.tableId(), view.incarnation(), view.game().decision(), action)));
    }
    private static void press(Minecraft client, String key) {
        var text = net.minecraft.network.chat.Component.translatable(key).getString();
        client.screen.children().stream().filter(c -> c instanceof MahjongButton b && b.getMessage().getString().equals(text))
            .map(c -> (MahjongButton) c).findFirst().orElseThrow().onPress();
    }
    private static net.minecraft.world.phys.Vec3 project(Minecraft client, BlockPos pos, net.minecraft.world.phys.Vec3 point) {
        var camera = client.gameRenderer.getMainCamera();
        double yaw = Math.toRadians(camera.getYRot()), pitch = Math.toRadians(camera.getXRot());
        var forward = new net.minecraft.world.phys.Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        var right = new net.minecraft.world.phys.Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        var delta = top.skyeyefast.mchjong.world.TableGeometry.world(pos, point).subtract(camera.getPosition());
        double fov = ((top.skyeyefast.mchjong.mixin.GameRendererAccessor) client.gameRenderer).mchjong$getFov(camera, 1, true);
        double focal = client.screen.height / (2 * Math.tan(Math.toRadians(fov) / 2));
        return new net.minecraft.world.phys.Vec3(client.screen.width / 2.0 + delta.dot(right) * focal / delta.dot(forward),
            client.screen.height / 2.0 - delta.dot(right.cross(forward)) * focal / delta.dot(forward), 0);
    }
    private static final class Guest extends ServerPlayer {
        Guest(ServerPlayer main, int number) {
            super(main.server, main.serverLevel(), new GameProfile(UUID.randomUUID(), "TaiwanGuest" + number), ClientInformation.createDefault());
            connection = new ServerGamePacketListenerImpl(main.server, new Connection(PacketFlow.SERVERBOUND), this, CommonListenerCookie.createInitial(getGameProfile(), false)) {
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            };
        }
    }
    private static final class MahjongSuppliesBox {
        static net.minecraft.world.item.ItemStack withoutFlowers(net.minecraft.world.item.ItemStack source) {
            var box = source.copy(); var contents = top.skyeyefast.mchjong.item.MahjongSupplies.contents(box);
            for (int i = 0; i < top.skyeyefast.mchjong.item.MahjongSupplies.TILE_SLOTS; i++) if (!contents.get(i).isEmpty() && top.skyeyefast.mchjong.item.MahjongSupplies.tile(contents.get(i)).face() >= 34) contents.set(i, net.minecraft.world.item.ItemStack.EMPTY);
            box.remove(top.skyeyefast.mchjong.item.MahjongComponents.BOX_PRESET);
            box.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(contents)); return box;
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
