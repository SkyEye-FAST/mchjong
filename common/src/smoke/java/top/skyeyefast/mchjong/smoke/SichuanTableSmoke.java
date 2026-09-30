package top.skyeyefast.mchjong.smoke;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import top.skyeyefast.mchjong.client.MahjongButton;
import top.skyeyefast.mchjong.client.McrLobbyScreen;
import top.skyeyefast.mchjong.client.RiichiTableScreen;
import top.skyeyefast.mchjong.client.SichuanLobbyScreen;
import top.skyeyefast.mchjong.client.SichuanTableScreen;
import top.skyeyefast.mchjong.client.SichuanResultsScreen;
import top.skyeyefast.mchjong.engine.SichuanAction;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.engine.RoomSeating;
import top.skyeyefast.mchjong.engine.SichuanGame;
import top.skyeyefast.mchjong.engine.SichuanSession;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.SichuanActionPayload;
import top.skyeyefast.mchjong.network.SichuanNextHandPayload;
import top.skyeyefast.mchjong.network.TableSessionControlPayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.TableRoomActionPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

/** Real packets, recipient-safe scenes, exit voting and the eight-hand client lifecycle. */
final class SichuanTableSmoke {
    private static final List<MahjongVariant> CHOICES = List.of(MahjongVariant.SICHUAN, MahjongVariant.MCR,
        MahjongVariant.RIICHI, MahjongVariant.SICHUAN);
    private final List<ServerPlayer> guests = new ArrayList<>();
    private CompletableFuture<?> task;
    private int stage, choice, ticks, settled;
    private UUID incarnation;
    private boolean picked;
    private int selectedTile;
    private long discardDecision;

    boolean tick(Minecraft client, BlockPos pos, Path output) {
        require(++ticks < 1400, "Sichuan smoke timed out at " + stage);
        if (task != null && !task.isDone()) return false;
        if (task != null) { task.join(); task = null; }
        var table = (MahjongTableBlockEntity) client.level.getBlockEntity(pos);
        var room = table.clientTableRoom();
        var server = client.getSingleplayerServer();
        UUID mainId = client.player.getUUID();
        switch (stage) {
            case 0 -> {
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    target.sit(main, 0); target.open(main);
                });
                stage++;
            }
            case 1 -> {
                if (room == null || room.viewerSeat() < 0 || client.screen == null) break;
                var buttons = client.screen.children().stream().filter(MahjongButton.class::isInstance)
                    .map(MahjongButton.class::cast).toList();
                for (var variant : MahjongVariant.values()) {
                    var label = Component.translatable("variant.mchjong." + variant.name().toLowerCase(java.util.Locale.ROOT)).getString();
                    var button = buttons.stream().filter(candidate -> candidate.getMessage().getString().equals(label)).findFirst().orElseThrow();
                    require(button.getX() >= 0 && button.getX() + button.getWidth() <= client.screen.width, "Variant button outside screen");
                    if (variant == CHOICES.get(choice)) button.onPress();
                }
                stage++;
            }
            case 2 -> {
                if (room == null || room.variant() != CHOICES.get(choice)) break;
                require(switch (room.variant()) {
                    case RIICHI -> client.screen instanceof RiichiTableScreen;
                    case MCR -> client.screen instanceof McrLobbyScreen;
                    case SICHUAN -> client.screen instanceof SichuanLobbyScreen;
                }, "Variant did not open its own screen");
                if (++choice < CHOICES.size()) { stage = 1; break; }
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    require(target.participantRoom(main) instanceof SichuanSession, "Wrong runtime for Sichuan");
                    require(target.equipment().sichuanStock().tiles().equals(Tile.sichuanSet()), "Wrong physical Sichuan stock");
                    for (int seat = 1; seat < 4; seat++) {
                        var guest = new Guest(main, seat); guests.add(guest);
                        guest.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
                        main.serverLevel().addNewPlayer(guest); server.getPlayerList().getPlayers().add(guest);
                        target.sit(guest, seat);
                    }
                    target.open(main);
                });
                stage++;
            }
            case 3 -> {
                if (room.seats().stream().anyMatch(seat -> seat.participant().id() == null)) break;
                int index = room.actions().indexOf(new RoomAction(RoomAction.Type.BEGIN_SEATING));
                require(index >= 0, "Sichuan room has no seating action");
                client.getConnection().send(PayloadPackets.serverbound(new TableRoomActionPayload(pos, room.tableId(),
                    room.incarnation(), room.decision(), index)));
                stage++;
            }
            case 4 -> {
                if (room.seating() != RoomSeating.Stage.POSITIONING) break;
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    var everyone = new ArrayList<>(guests); everyone.add(main);
                    var assigned = target.roomView(main).seats();
                    for (var player : everyone) if (player.getVehicle() instanceof SeatEntity mount) { player.stopRiding(); mount.discard(); }
                    for (var player : everyone) for (int seat = 0; seat < 4; seat++)
                        if (player.getUUID().equals(assigned.get(seat).participant().id())) target.sit(player, seat);
                    for (var guest : guests) {
                        var offered = target.roomView(guest);
                        int index = offered.actions().indexOf(new RoomAction(RoomAction.Type.READY));
                        require(index >= 0, "Sichuan guest has no ready action");
                        TableNetworking.receive(guest, new TableRoomActionPayload(pos, offered.tableId(), offered.incarnation(), offered.decision(), index));
                    }
                    target.open(main);
                });
                stage++;
            }
            case 5 -> {
                if (room.seats().stream().filter(seat -> seat.participant().ready()).count() != 3) break;
                int index = room.actions().indexOf(new RoomAction(RoomAction.Type.READY));
                require(index >= 0, "Sichuan host has no ready action");
                client.getConnection().send(PayloadPackets.serverbound(new TableRoomActionPayload(pos, room.tableId(),
                    room.incarnation(), room.decision(), index)));
                stage++;
            }
            case 6 -> {
                var view = table.clientSichuanView();
                if (view == null) break;
                require(client.screen instanceof top.skyeyefast.mchjong.client.SichuanTableScreen && view.game().phase() == SichuanGame.Phase.VOIDING, "Sichuan did not enter declaration phase");
                require(view.game().wall().remaining() == 55 && view.game().seats().stream().filter(seat -> seat.hand().stream()
                    .anyMatch(tile -> tile != Tile.HIDDEN)).count() == 1, "Sichuan private deal leaked");
                incarnation = view.incarnation();
                client.getConnection().send(PayloadPackets.serverbound(new SichuanActionPayload(pos, view.tableId(),
                    view.incarnation(), view.game().decision(), 0)));
                stage++;
            }
            case 7 -> {
                var view = table.clientSichuanView();
                if (!view.game().actions().isEmpty()) break;
                require(view.game().seats().stream().filter(seat -> seat.voidSuit() >= 0).count() == 1, "Private void choice leaked");
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    var saved = target.saveWithoutMetadata(main.registryAccess());
                    target.loadWithComponents(saved, main.registryAccess());
                    for (var guest : guests) {
                        var session = (SichuanSession) target.participantRoom(guest);
                        require(session != null, "Restored Sichuan seat lost authorization");
                        var offered = session.view(guest.getUUID());
                        TableNetworking.receive(guest, new SichuanActionPayload(pos, offered.tableId(), offered.incarnation(), offered.game().decision(), 0));
                    }
                    target.open(main);
                });
                stage++;
            }
            case 8 -> {
                var view = table.clientSichuanView();
                if (view == null || view.game().phase() != SichuanGame.Phase.TURN) break;
                require(!incarnation.equals(view.incarnation()), "Restored Sichuan incarnation was reused");
                require(view.game().seats().stream().allMatch(seat -> seat.voidSuit() == 0), "Sichuan declarations did not complete");
                press(client, "ui.mchjong.exit");
                stage++;
            }
            case 9 -> {
                if (room.exitVote() == null) break;
                require(table.clientSichuanView().paused(), "Exit vote did not pause Sichuan");
                require(client.screen.children().stream().filter(MahjongButton.class::isInstance).count() >= 2, "Exit vote has no controls");
                task = server.submit(() -> {
                    var guest = guests.getFirst();
                    var target = (MahjongTableBlockEntity) guest.serverLevel().getBlockEntity(pos);
                    var offered = target.roomView(guest);
                    TableNetworking.receive(guest, new TableSessionControlPayload(pos, offered.tableId(),
                        TableSessionControlPayload.Operation.ANSWER_EXIT, offered.exitVote().id(), false));
                });
                stage++;
            }
            case 10 -> {
                if (room.exitVote() != null || table.clientSichuanView().paused()) break;
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    var session = (SichuanSession) target.participantRoom(main);
                    for (int move = 0; move < 40; move++) {
                        if (session.view(mainId).game().actions().stream().anyMatch(action -> action.type() == SichuanAction.Type.DISCARD)) {
                            target.open(main); return;
                        }
                        advanceOne(session);
                    }
                    throw new IllegalStateException("Sichuan client discard turn not reached");
                });
                settled = 0; stage++;
            }
            case 11 -> {
                var view = table.clientSichuanView();
                if (view.game().actions().stream().noneMatch(action -> action.type() == SichuanAction.Type.DISCARD)) break;
                if (++settled < 10) break;
                var screen = (SichuanTableScreen) client.screen;
                if (!picked) {
                    top.skyeyefast.mchjong.client.TableSettings.get().discardMode = top.skyeyefast.mchjong.client.TableSettings.DiscardMode.CONFIRM;
                    selectedTile = view.game().actions().stream().filter(action -> action.type() == SichuanAction.Type.DISCARD)
                        .findFirst().orElseThrow().tiles().getFirst();
                    var piece = top.skyeyefast.mchjong.client.SichuanTableScene.build(view.game()).stream()
                        .filter(candidate -> candidate.area() == top.skyeyefast.mchjong.client.SichuanTableScene.Area.HAND
                            && candidate.seat() == view.game().viewerSeat() && candidate.tile() == selectedTile).findFirst().orElseThrow();
                    var pointer = project(client, pos, piece.position());
                    require(screen.mouseClicked(pointer.x, pointer.y, 0) && screen.selected(piece), "Seated Sichuan picking failed");
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                    require(screen.immersive(), "Sichuan cannot enter immersive view");
                    var player = view.game().seats().get(view.game().viewerSeat());
                    var tiles = new ArrayList<>(player.hand());
                    if (player.drawn() >= 0 && tiles.remove(Integer.valueOf(player.drawn()))) tiles.add(player.drawn());
                    double scale = Math.min(screen.width / 1280.0, screen.height / 800.0);
                    double horizontal = (screen.width - 1280 * scale) / 2
                        + (289 + tiles.indexOf(selectedTile) * 52 + (selectedTile == player.drawn() ? 26 : 0)) * scale;
                    double vertical = (screen.height - 800 * scale) / 2 + 726 * scale;
                    require(screen.mouseClicked(horizontal, vertical, 0), "Immersive Sichuan picking failed");
                    picked = true; settled = 0; break;
                }
                SmokeScreenshots.grab(output.toFile(), "sichuan-table.png", client.getMainRenderTarget(), ignored -> {});
                discardDecision = view.game().decision();
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
                stage = 17;
            }
            case 17 -> {
                var view = table.clientSichuanView();
                if (view.game().decision() == discardDecision) break;
                require(view.game().seats().get(view.game().viewerSeat()).river().stream().anyMatch(discard -> discard.tile() == selectedTile),
                    "Sichuan selection did not discard the physical tile");
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    finishHand((SichuanSession) target.participantRoom(main));
                    target.open(main);
                });
                stage = 12;
            }
            case 12 -> {
                if (!(client.screen instanceof SichuanResultsScreen results)) break;
                var view = table.clientSichuanView();
                require(view.game().phase() == SichuanGame.Phase.HAND_END && view.game().result() != null, "No hand ledger in results");
                require(results.immersive(), "Results lost the selected table view");
                if (++settled < 20) break;
                SmokeScreenshots.grab(output.toFile(), "sichuan-results.png", client.getMainRenderTarget(), ignored -> {});
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    for (var guest : guests) {
                        var session = (SichuanSession) target.participantRoom(guest);
                        var offered = session.view(guest.getUUID());
                        var payload = new SichuanNextHandPayload(pos, offered.tableId(), offered.incarnation(), offered.game().decision());
                        TableNetworking.receive(guest, payload);
                        int confirmed = session.view(guest.getUUID()).confirmedCount();
                        TableNetworking.receive(guest, payload);
                        require(confirmed == session.view(guest.getUUID()).confirmedCount(), "Duplicate next-hand confirmation counted twice");
                    }
                    target.open(main);
                });
                stage++;
            }
            case 13 -> {
                if (table.clientSichuanView().confirmedCount() != 3) break;
                press(client, "sichuan.mchjong.next_hand");
                stage++;
            }
            case 14 -> {
                var view = table.clientSichuanView();
                if (view.game().handNumber() != 2 || view.game().phase() != SichuanGame.Phase.VOIDING) break;
                require(client.screen instanceof SichuanTableScreen screen && screen.immersive(), "Next hand did not return to table");
                require(view.confirmedCount() == 0 && view.game().result() == null, "Next hand retained settlement state");
                for (int seat = 0; seat < 4; seat++) if (seat != view.game().viewerSeat()) {
                    require(view.game().seats().get(seat).voidSuit() == -1, "Next hand leaked a void choice");
                    require(view.game().seats().get(seat).hand().stream().allMatch(tile -> tile == Tile.HIDDEN), "Next hand leaked an opponent hand");
                }
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    var session = (SichuanSession) target.participantRoom(main);
                    while (session.game().phase() != SichuanGame.Phase.MATCH_END) {
                        finishHand(session);
                        if (session.game().phase() == SichuanGame.Phase.MATCH_END) break;
                        var end = session.view(mainId);
                        for (var seat : session.roomView(mainId).seats()) require(session.confirmNextHand(seat.participant().id(),
                            end.tableId(), end.incarnation(), end.game().decision()), "Eight-hand progression rejected confirmation");
                    }
                    target.open(main);
                });
                stage++;
            }
            case 15 -> {
                var view = table.clientSichuanView();
                if (!(client.screen instanceof SichuanResultsScreen) || view.game().phase() != SichuanGame.Phase.MATCH_END) break;
                require(view.game().handNumber() == 8 && !view.canConfirmNextHand(), "Final results allow a ninth hand");
                require(client.screen.children().stream().filter(MahjongButton.class::isInstance).map(MahjongButton.class::cast)
                    .noneMatch(button -> button.getMessage().getString().equals(Component.translatable("sichuan.mchjong.next_hand").getString())),
                    "Match results contain a next-hand control");
                press(client, "action.mchjong.return_to_lobby");
                stage++;
            }
            case 16 -> {
                if (!room.lobby() || !(client.screen instanceof SichuanLobbyScreen)) break;
                require(table.clientSichuanView() == null, "Lobby retained finished Sichuan decisions");
                return true;
            }
            default -> throw new IllegalStateException("Unknown Sichuan smoke stage");
        }
        return false;
    }
    private static void press(Minecraft client, String key) {
        var label = Component.translatable(key).getString();
        var button = client.screen.children().stream().filter(MahjongButton.class::isInstance).map(MahjongButton.class::cast)
            .filter(candidate -> candidate.getMessage().getString().equals(label)).findFirst().orElseThrow();
        require(button.active, "Disabled Sichuan control: " + key);
        button.onPress();
    }
    private static void finishHand(SichuanSession session) {
        for (int move = 0; move < 2000 && !session.game().ended(); move++) advanceOne(session);
        require(session.game().ended(), "Sichuan hand exceeded action bound");
    }
    private static void advanceOne(SichuanSession session) {
        for (var seat : session.roomView(null).seats()) {
            var actor = seat.participant().id();
            var offered = session.view(actor);
            if (offered.game().actions().isEmpty()) continue;
            int index = 0;
            for (int action = 0; action < offered.game().actions().size(); action++) {
                var type = offered.game().actions().get(action).type();
                if (type == SichuanAction.Type.WIN || type == SichuanAction.Type.PASS) { index = action; break; }
            }
            require(session.act(actor, offered.tableId(), offered.incarnation(), offered.game().decision(), index), "Sichuan issued action rejected");
            return;
        }
        throw new IllegalStateException("Sichuan hand stalled");
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
            super(main.server, main.serverLevel(), new GameProfile(UUID.randomUUID(), "SichuanGuest" + number), ClientInformation.createDefault());
            connection = new ServerGamePacketListenerImpl(main.server, new Connection(PacketFlow.SERVERBOUND), this,
                CommonListenerCookie.createInitial(getGameProfile(), false)) {
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            };
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
