package top.skyeyefast.mchjong.smoke;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
import top.skyeyefast.mchjong.client.SichuanScreen;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.engine.RoomSeating;
import top.skyeyefast.mchjong.engine.SichuanGame;
import top.skyeyefast.mchjong.engine.SichuanSession;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.SichuanActionPayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.TableRoomActionPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

/** Real packets, three-way lobby navigation, private declarations and NBT restoration. */
final class SichuanTableSmoke {
    private static final List<MahjongVariant> CHOICES = List.of(MahjongVariant.SICHUAN, MahjongVariant.MCR,
        MahjongVariant.RIICHI, MahjongVariant.SICHUAN);
    private final List<ServerPlayer> guests = new ArrayList<>();
    private CompletableFuture<?> task;
    private int stage, choice, ticks;
    private UUID incarnation;

    boolean tick(Minecraft client, BlockPos pos) {
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
                    case SICHUAN -> client.screen instanceof SichuanScreen;
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
                require(client.screen instanceof SichuanScreen && view.game().phase() == SichuanGame.Phase.VOIDING, "Sichuan did not enter declaration phase");
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
                return true;
            }
            default -> throw new IllegalStateException("Unknown Sichuan smoke stage");
        }
        return false;
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
