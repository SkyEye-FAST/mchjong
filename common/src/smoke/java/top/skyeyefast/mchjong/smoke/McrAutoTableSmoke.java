package top.skyeyefast.mchjong.smoke;

import com.mojang.authlib.GameProfile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import top.skyeyefast.mchjong.client.MahjongButton;
import top.skyeyefast.mchjong.client.McrResultsScreen;
import top.skyeyefast.mchjong.client.McrLobbyScreen;
import top.skyeyefast.mchjong.client.McrTableScreen;
import top.skyeyefast.mchjong.client.TableLeaveScreen;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.engine.McrAction;
import top.skyeyefast.mchjong.engine.McrGame;
import top.skyeyefast.mchjong.engine.McrSession;
import top.skyeyefast.mchjong.engine.RoomSeating;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.network.McrActionPayload;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.TableVariantPayload;
import top.skyeyefast.mchjong.network.TableSessionControlPayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

/** One real-client, four-identity automatic-table path through packets, views and MCR screens. */
final class McrAutoTableSmoke {
    private final List<Guest> guests = new ArrayList<>();
    private CompletableFuture<?> task;
    private int stage;
    private int ticks;
    private long firstDecision;
    private int responses;
    private boolean voteRequested;
    private boolean voteAnswered;
    private Map<UUID, Integer> assignedSeats;
    private long leaveRevision;
    private int presentationStep;

    boolean tick(Minecraft client, BlockPos pos, Path output) {
        check(++ticks < 2200, "MCR automatic-table smoke timed out at " + stage);
        var clientTable = (MahjongTableBlockEntity) client.level.getBlockEntity(pos);
        if (task != null && !task.isDone()) return false;
        if (task != null) { task.join(); task = null; }
        var server = client.getSingleplayerServer();
        UUID mainId = client.player.getUUID();
        switch (stage) {
            case 0 -> {
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    ((MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos)).sit(main, 0);
                });
                stage++;
            }
            case 1 -> {
                var lobby = clientTable.clientRoom();
                if (lobby == null || lobby.viewerSeat() < 0 || client.getConnection() == null) break;
                client.getConnection().send(PayloadPackets.serverbound(new TableVariantPayload(pos, lobby.tableId(),
                    lobby.decision(), MahjongVariant.MCR)));
                stage++;
            }
            case 2 -> {
                if (clientTable.clientVariant() != MahjongVariant.MCR) break;
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var level = main.serverLevel();
                    var table = (MahjongTableBlockEntity) level.getBlockEntity(pos);
                    check(table.equipment().mcrStock() != null, "144-tile MCR case was rejected");
                    check(table.equipment().mcrStock().deck().tiles().size() == 144, "MCR case lost physical tiles");
                    for (int seat = 1; seat < 4; seat++) {
                        var guest = new Guest(main, seat);
                        guests.add(guest);
                        guest.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
                        level.addNewPlayer(guest);
                        server.getPlayerList().getPlayers().add(guest);
                        table.sit(guest, seat);
                    }
                    table.open(main);
                });
                stage++;
            }
            case 3 -> {
                var lobby = clientTable.clientTableRoom();
                if (lobby == null || lobby.seats().stream().anyMatch(seat -> seat.participant().id() == null)) break;
                check(client.screen instanceof McrLobbyScreen, "MCR preparation did not open its room screen");
                SmokeScreenshots.grab(output.toFile(), "mcr-auto-lobby.png", client.getMainRenderTarget(), message -> {});
                int index = lobby.actions().indexOf(new RoomAction(RoomAction.Type.BEGIN_SEATING));
                check(index >= 0, "MCR lobby cannot assign four seats");
                client.getConnection().send(PayloadPackets.serverbound(new top.skyeyefast.mchjong.network.TableRoomActionPayload(pos, lobby.tableId(),
                    lobby.incarnation(), lobby.decision(), index)));
                stage++;
            }
            case 4 -> {
                var lobby = clientTable.clientTableRoom();
                if (lobby == null || lobby.seating() != RoomSeating.Stage.POSITIONING) break;
                var assigned = new java.util.HashMap<UUID, Integer>();
                for (int seat = 0; seat < 4; seat++) {
                    String name = lobby.seats().get(seat).participant().name();
                    if (name.equals(client.player.getGameProfile().getName())) assigned.put(mainId, seat);
                    else for (var guest : guests) if (name.equals(guest.getGameProfile().getName())) assigned.put(guest.getUUID(), seat);
                }
                check(assigned.size() == 4, "MCR seat assignment omitted a player");
                assignedSeats = Map.copyOf(assigned);
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var table = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    var everyone = new ArrayList<ServerPlayer>(guests);
                    everyone.add(main);
                    for (var player : everyone) {
                        if (player.getVehicle() instanceof SeatEntity mount) {
                            player.stopRiding();
                            mount.discard();
                        }
                    }
                    for (var player : everyone) table.sit(player, assigned.get(player.getUUID()));
                    for (var guest : guests) {
                        var view = table.roomView(guest);
                        int index = view.actions().indexOf(new RoomAction(RoomAction.Type.READY));
                        check(index >= 0, "Guest has no Ready action");
                        TableNetworking.receive(guest, new top.skyeyefast.mchjong.network.TableRoomActionPayload(pos, view.tableId(), view.incarnation(), view.decision(), index));
                    }
                    table.open(main);
                });
                stage++;
            }
            case 5 -> {
                var lobby = clientTable.clientTableRoom();
                if (lobby == null || lobby.viewerSeat() < 0 || lobby.seats().stream()
                    .filter(seat -> seat.participant().ready()).count() != 3) break;
                int index = lobby.actions().indexOf(new RoomAction(RoomAction.Type.READY));
                check(index >= 0, "Last player has no Ready action");
                client.getConnection().send(PayloadPackets.serverbound(new top.skyeyefast.mchjong.network.TableRoomActionPayload(pos, lobby.tableId(),
                    lobby.incarnation(), lobby.decision(), index)));
                stage++;
            }
            case 6 -> {
                if (!(client.screen instanceof McrTableScreen screen)) break;
                var view = clientTable.clientMcrView();
                check(view != null && view.seated() == 15 && view.game().wall().size() == 144,
                    "MCR screen lacks the complete seated wall");
                if (!voteRequested) {
                    task = server.submit(() -> {
                        var main = server.getPlayerList().getPlayer(mainId);
                        var table = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                        var room = table.roomView(main);
                        TableNetworking.receive(main, new TableSessionControlPayload(pos, room.tableId(),
                            TableSessionControlPayload.Operation.REQUEST_EXIT, room.decision(), false));
                        check(table.roomView(main).exitVote() != null, "MCR exit request did not open a shared vote");
                        check(table.mcrView(main).game().actions().isEmpty(), "MCR actions remained available during the vote");
                    });
                    voteRequested = true;
                    break;
                }
                if (!voteAnswered) {
                    var room = clientTable.clientTableRoom();
                    if (room.exitVote() == null) break;
                    check(view.game().actions().isEmpty(), "MCR client received actions during the vote");
                    check(screen.children().stream().filter(child -> child instanceof MahjongButton).count() == 2,
                        "MCR vote controls were not shown");
                    SmokeScreenshots.grab(output.toFile(), "mcr-auto-vote.png", client.getMainRenderTarget(), message -> {});
                    task = server.submit(() -> {
                        var guest = guests.getFirst();
                        var table = (MahjongTableBlockEntity) guest.serverLevel().getBlockEntity(pos);
                        var offered = table.roomView(guest);
                        TableNetworking.receive(guest, new TableSessionControlPayload(pos, offered.tableId(),
                            TableSessionControlPayload.Operation.ANSWER_EXIT, offered.exitVote().id(), false));
                        check(table.roomView(guest).exitVote() == null, "MCR vote rejection did not resume the room");
                    });
                    voteAnswered = true;
                    break;
                }
                if (clientTable.clientTableRoom().exitVote() != null) break;
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var table = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    var offered = table.mcrView(main);
                    var spectator = table.mcrView(new Guest(main, 4));
                    check(spectator.game().viewerSeat() == -1 && spectator.game().actions().isEmpty()
                        && spectator.game().seats().stream().allMatch(seat -> seat.hand().stream()
                            .allMatch(tile -> tile == top.skyeyefast.mchjong.engine.Tile.HIDDEN)),
                        "Spectator received MCR actions or concealed tiles");
                    for (var rejected : List.of(
                        new McrActionPayload(pos, offered.tableId(), UUID.randomUUID(), offered.game().decision(), 0),
                        new McrActionPayload(pos, offered.tableId(), offered.incarnation(), offered.game().decision() - 1, 0),
                        new McrActionPayload(pos, offered.tableId(), offered.incarnation(), offered.game().decision(), 999)))
                        TableNetworking.receive(main, rejected);
                    check(table.mcrView(main).revision() == offered.revision(), "Stale MCR request changed the session");
                    for (int step = 0; step < 30; step++) {
                        var view0 = table.mcrView(main);
                        if (view0.game().phase() == McrGame.Phase.TURN && !view0.game().actions().isEmpty()) return;
                        check(driveOne(table, main, pos), "Cannot reach the client's discard turn");
                    }
                    throw new IllegalStateException("Client discard turn was not reached");
                });
                stage++;
            }
            case 7 -> {
                if (!(client.screen instanceof McrTableScreen screen)) break;
                var view = clientTable.clientMcrView();
                if (view == null || view.game().phase() != McrGame.Phase.TURN || view.game().actions().isEmpty()) break;
                int index = first(view, McrAction.Type.DISCARD);
                check(index >= 0, "Client has no discard action");
                if (presentationStep == 0) {
                    check(!screen.immersive(), "MCR play must open in the seated world view");
                    SmokeScreenshots.grab(output.toFile(), "mcr-auto-play.png", client.getMainRenderTarget(), message -> {});
                    top.skyeyefast.mchjong.client.TableSettings.get().discardMode = top.skyeyefast.mchjong.client.TableSettings.DiscardMode.CONFIRM;
                    var piece = top.skyeyefast.mchjong.client.McrTableScene.build(view.game()).stream()
                        .filter(p -> p.area() == top.skyeyefast.mchjong.client.McrTableScene.Area.HAND
                            && p.seat() == view.game().viewerSeat() && p.tile() == view.game().actions().get(index).tiles().getFirst())
                        .findFirst().orElseThrow();
                    var pointer = project(client, pos, piece.position());
                    check(screen.mouseClicked(pointer.x, pointer.y, 0) && screen.selected(piece), "Seated MCR hand picking failed");
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                    check(screen.immersive(), "MCR view binding did not open the immersive canvas");
                    presentationStep++;
                    break;
                }
                SmokeScreenshots.grab(output.toFile(), "mcr-auto-immersive.png", client.getMainRenderTarget(), message -> {});
                double scale = Math.min(screen.width / 1280.0, screen.height / 800.0);
                double x = (screen.width - 1280 * scale) / 2 + 289 * scale;
                double y = (screen.height - 800 * scale) / 2 + 726 * scale;
                firstDecision = view.game().decision();
                check(screen.mouseClicked(x, y, 0), "Immersive hand did not accept canvas coordinates");
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
                stage++;
            }
            case 8 -> {
                var view = clientTable.clientMcrView();
                if (view == null || view.game().decision() == firstDecision) break;
                check(view.game().phase() == McrGame.Phase.REACTION || view.game().phase() == McrGame.Phase.DRAW,
                    "Discard did not advance to response or draw");
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var table = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    for (int step = 0; step < 650; step++) {
                        if (table.mcrView(main).game().phase() == McrGame.Phase.HAND_END) return;
                        check(driveOne(table, main, pos), "Cannot advance MCR response or draw");
                    }
                    throw new IllegalStateException("MCR hand did not settle");
                });
                stage++;
            }
            case 9 -> {
                if (!(client.screen instanceof McrResultsScreen)) break;
                var view = clientTable.clientMcrView();
                check(responses > 0, "No MCR response traveled through the action handler");
                check(view.game().phase() == McrGame.Phase.HAND_END && view.game().result() != null,
                    "MCR result was not delivered to the settlement screen");
                SmokeScreenshots.grab(output.toFile(), "mcr-auto-results.png", client.getMainRenderTarget(), message -> {});
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var table = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    for (var guest : guests) {
                        var result = table.mcrView(guest);
                        TableNetworking.receive(guest, new top.skyeyefast.mchjong.network.McrNextHandPayload(pos,
                            result.tableId(), result.incarnation(), result.game().decision()));
                    }
                });
                stage++;
            }
            case 10 -> {
                if (!(client.screen instanceof McrResultsScreen screen)) break;
                var view = clientTable.clientMcrView();
                if (view == null || view.confirmed() != 15 - (1 << view.game().viewerSeat())) break;
                check(view.canConfirmNextHand(), "Last player cannot confirm next hand");
                var confirm = screen.children().stream().filter(child -> child instanceof MahjongButton button
                    && button.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable(
                        "mcr.mchjong.next_hand").getString())).map(child -> (MahjongButton) child).findFirst()
                    .orElseThrow(() -> new IllegalStateException("MCR confirmation button is missing"));
                confirm.onPress();
                stage++;
            }
            case 11 -> {
                if (!(client.screen instanceof McrTableScreen)) break;
                var view = clientTable.clientMcrView();
                if (view == null || view.game().handNumber() != 2) break;
                check(view.game().phase() == McrGame.Phase.TURN || view.game().phase() == McrGame.Phase.INITIAL_FLOWERS,
                    "Second MCR hand did not start");
                check(((McrTableScreen) client.screen).immersive(), "Next hand lost the selected MCR view");
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var table = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    for (var guest : guests) {
                        guest.stopRiding();
                        table.stoodUp(guest.getUUID());
                    }
                    main.stopRiding();
                    table.stoodUp(mainId);
                    check(table.mcrView(main).paused(), "MCR match did not pause after the last dismount");
                    table.open(main);
                });
                stage++;
            }
            case 12 -> {
                if (!(client.screen instanceof TableLeaveScreen leave)) break;
                check(clientTable.clientMcrView().paused(), "MCR leave prompt has an active match");
                leaveRevision = clientTable.clientTableRoom().revision();
                ((MahjongButton) leave.children().getFirst()).onPress();
                stage++;
            }
            case 13 -> {
                var room = clientTable.clientTableRoom();
                if (room == null || room.revision() <= leaveRevision) break;
                check(room.paused(), "Keeping the MCR match did not retain its paused state");
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var table = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    for (var guest : guests) table.sit(guest, assignedSeats.get(guest.getUUID()));
                    table.sit(main, assignedSeats.get(mainId));
                });
                stage++;
            }
            case 14 -> {
                if (!(client.screen instanceof McrTableScreen)) break;
                var view = clientTable.clientMcrView();
                if (view == null || view.seated() != 15) break;
                check(!view.paused() && view.game().handNumber() == 2,
                    "MCR match did not resume from the retained hand");
                return true;
            }
            default -> throw new IllegalStateException("Invalid MCR smoke stage");
        }
        return false;
    }

    private boolean driveOne(MahjongTableBlockEntity table, ServerPlayer main, BlockPos pos) {
        var players = new ArrayList<ServerPlayer>(guests);
        players.add(main);
        var phase = table.mcrView(main).game().phase();
        if (phase == McrGame.Phase.HAND_END || phase == McrGame.Phase.MATCH_END) return false;
        McrAction.Type target = switch (phase) {
            case INITIAL_FLOWERS, REPLACE_FLOWER -> McrAction.Type.REPLACE_FLOWER;
            case DRAW -> McrAction.Type.DRAW;
            case TURN -> McrAction.Type.DISCARD;
            case REACTION -> McrAction.Type.PASS;
            case HAND_END, MATCH_END -> throw new IllegalStateException("Finished hand has no action");
        };
        for (var player : players) {
            var view = table.mcrView(player);
            int index = first(view, target);
            if (index < 0) continue;
            TableNetworking.receive(player, new McrActionPayload(pos, view.tableId(), view.incarnation(), view.game().decision(), index));
            if (target == McrAction.Type.PASS) responses++;
            return true;
        }
        return false;
    }

    private static int first(McrSession.View view, McrAction.Type type) {
        for (int i = 0; i < view.game().actions().size(); i++) if (view.game().actions().get(i).type() == type) return i;
        return -1;
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
            super(main.server, main.serverLevel(), new GameProfile(UUID.randomUUID(), "McrGuest" + number), ClientInformation.createDefault());
            connection = new ServerGamePacketListenerImpl(main.server, new Connection(PacketFlow.SERVERBOUND), this,
                CommonListenerCookie.createInitial(getGameProfile(), false)) {
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            };
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
