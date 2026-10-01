package top.skyeyefast.mchjong.world;

import java.security.SecureRandom;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import top.skyeyefast.mchjong.network.PayloadPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.RiichiSession;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.engine.McrCodec;
import top.skyeyefast.mchjong.engine.McrSession;
import top.skyeyefast.mchjong.engine.SichuanSession;
import top.skyeyefast.mchjong.engine.SichuanCodec;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.engine.TableSession;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.network.RiichiActionPayload;
import top.skyeyefast.mchjong.network.McrActionPayload;
import top.skyeyefast.mchjong.network.McrViewPayload;
import top.skyeyefast.mchjong.network.SichuanActionPayload;
import top.skyeyefast.mchjong.network.SichuanViewPayload;
import top.skyeyefast.mchjong.network.TableRoomActionPayload;
import top.skyeyefast.mchjong.network.McrNextHandPayload;
import top.skyeyefast.mchjong.network.RiichiControlPayload;
import top.skyeyefast.mchjong.network.TableSessionControlPayload;
import top.skyeyefast.mchjong.network.TableVariantPayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.TableSeatPayload;
import top.skyeyefast.mchjong.network.RiichiViewPayload;

public final class MahjongTableBlockEntity extends FurnitureBlockEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger("mchjong");
    private static final SecureRandom SEEDS = new SecureRandom();
    private TableHost host;
    private byte[] unreadableSave;
    private int ticks;
    private long sentRevision = -1;
    private WorldSettings.Policy sentWorldPolicy;
    private RiichiView clientView;
    private top.skyeyefast.mchjong.engine.RiichiRoomSettings clientRiichiSettings;
    private McrSession.View clientMcrView;
    private SichuanSession.View clientSichuanView;
    private top.skyeyefast.mchjong.item.SichuanDeck clientSichuanDeck;
    private net.minecraft.world.item.DyeColor clientSichuanCloth;
    private top.skyeyefast.mchjong.engine.SichuanRoomSettings clientSichuanSettings;
    private top.skyeyefast.mchjong.engine.TimeControl clientMcrTimeControl;
    private top.skyeyefast.mchjong.engine.TableRoomView clientTableRoom;
    private top.skyeyefast.mchjong.item.McrDeck clientMcrDeck;
    private net.minecraft.world.item.DyeColor clientMcrCloth;
    private MahjongVariant clientVariant = MahjongVariant.RIICHI;
    private BotServiceState clientBotService;
    private WorldSettings.Policy clientWorldPolicy;
    private int clientRedOptions;
    private long clientViewReceivedNanos;
    private long nextArchiveRetry;
    private final TableEquipment equipment = new TableEquipment(this::equipmentChanged);
    private boolean syncingEquipment;

    public TableEquipment equipment() { return equipment; }
    public boolean automatic() { return getBlockState().is(MahjongContent.AUTO_TABLE); }

    public MahjongTableBlockEntity(BlockPos pos, BlockState state) { super(MahjongContent.TABLE_ENTITY, pos, state); }

    private TableHost serverHost() {
        if (level == null || level.isClientSide) throw new IllegalStateException("Private state accessed outside server");
        if (unreadableSave != null) return null;
        if (host == null) host = new TableHost(UUID.randomUUID(), SEEDS.nextLong());
        var policy = WorldSettings.of(level.getServer()).policy();
        if (host.prepare(equipment, automatic(), policy.gamePolicy())) appearanceChanged();
        synchronizeEquipment();
        if (!host.available(equipment)) return null;
        synchronizeSeats();
        return host;
    }

    private RiichiGame serverGame() {
        var current = serverHost();
        return current == null ? null : current.riichiGame();
    }

    private RiichiSession serverRiichiSession() {
        var current = serverHost();
        return current == null ? null : current.riichi();
    }

    private TableSession serverSession() {
        var current = serverHost();
        return current == null ? null : current.session();
    }

    private void synchronizeEquipment() {
        boolean active = host != null && host.riichi() != null && !host.session().lobby()
            && host.session().lifecycle() != TableSession.Lifecycle.FINISHED;
        if (automatic() || syncingEquipment || equipment.matchActive() == active) return;
        syncingEquipment = true;
        try {
            for (var player : ((ServerLevel) level).players())
                if (player.containerMenu instanceof top.skyeyefast.mchjong.item.PointStickMenu menu && menu.belongsTo(this)) player.closeContainer();
            if (active) {
                if (!equipment.prepareMatch()) throw new IllegalStateException("Starting match without its reserved supplies");
            }
            else equipment.endMatch();
            setChanged();
        } finally { syncingEquipment = false; }
    }

    private void synchronizeSeats() {
        TableSession session = host.session();
        var mounted = new java.util.HashMap<UUID, Integer>();
        var connected = new java.util.HashSet<UUID>();
        for (ServerPlayer player : ((ServerLevel) level).getServer().getPlayerList().getPlayers()) connected.add(player.getUUID());
        for (SeatEntity seat : level.getEntitiesOfClass(SeatEntity.class, new AABB(worldPosition).inflate(4))) {
            if (seat.tablePos().equals(worldPosition) && !seat.isRemoved()
                && seat.getFirstPassenger() instanceof net.minecraft.world.entity.TamableAnimal companion) {
                int assigned = session.entityBot(companion.getUUID()) ? session.seatOf(companion.getUUID()) : -1;
                if (assigned < 0 || !companion.isAlive()
                    || !level.getBlockState(TableGeometry.stool(worldPosition, assigned)).is(MahjongContent.STOOL)) {
                    companion.stopRiding();
                    seat.discard();
                    session.leaveEntityBot(companion.getUUID());
                } else {
                    if (seat.seat() != assigned) {
                        seat.initialize(worldPosition, assigned, companion.getUUID());
                        companion.setYRot(TableGeometry.yaw(assigned));
                    }
                    mounted.put(companion.getUUID(), assigned);
                }
                continue;
            }
            if (!seat.tablePos().equals(worldPosition) || seat.isRemoved()
                || !(seat.getFirstPassenger() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) continue;
            int assigned = session.seatOf(player.getUUID());
            if (assigned != seat.seat()) {
                player.stopRiding();
                // Reassignment is not leaving the room; retire the old vehicle before its tick calls stoodUp.
                seat.discard();
                if (assigned >= 0) {
                    BlockPos destination = TableGeometry.stool(worldPosition, assigned);
                    player.sendSystemMessage(Component.translatable("message.mchjong.assigned_seat",
                        Component.translatable("wind.mchjong." + new String[]{"east", "south", "west", "north"}[assigned]),
                        destination.getX(), destination.getY(), destination.getZ()));
                }
            } else if (level.getBlockState(TableGeometry.stool(worldPosition, seat.seat())).is(MahjongContent.STOOL))
                mounted.put(player.getUUID(), seat.seat());
        }
        session.synchronizeSeats(mounted, connected);
    }

    public RiichiView clientView() { return clientView; }
    public top.skyeyefast.mchjong.engine.RiichiRoomSettings clientRiichiSettings() { return clientRiichiSettings; }
    public void acceptRiichiSettings(top.skyeyefast.mchjong.engine.RiichiRoomSettings settings) {
        if (level == null || !level.isClientSide) throw new IllegalStateException("Client Riichi settings on server");
        clientRiichiSettings = java.util.Objects.requireNonNull(settings);
    }
    public MahjongVariant clientVariant() { return clientVariant; }
    public void acceptVariant(MahjongVariant value) { clientVariant = java.util.Objects.requireNonNull(value); }
    public McrSession.View clientMcrView() { return clientMcrView; }
    public top.skyeyefast.mchjong.engine.TimeControl clientMcrTimeControl() { return clientMcrTimeControl; }
    public top.skyeyefast.mchjong.engine.TableRoomView clientTableRoom() { return clientTableRoom; }
    public top.skyeyefast.mchjong.item.McrDeck clientMcrDeck() { return clientMcrDeck; }
    public net.minecraft.world.item.DyeColor clientMcrCloth() { return clientMcrCloth; }
    public void acceptMcrView(McrSession.View view, top.skyeyefast.mchjong.engine.TableRoomView room,
                              top.skyeyefast.mchjong.item.McrDeck deck,
                              net.minecraft.world.item.DyeColor cloth, top.skyeyefast.mchjong.engine.TimeControl timeControl) {
        if (level == null || !level.isClientSide) throw new IllegalStateException("Client MCR snapshot on server");
        if (clientTableRoom != null && clientTableRoom.tableId().equals(room.tableId())
            && clientTableRoom.incarnation().equals(room.incarnation()) && room.revision() < clientTableRoom.revision()) return;
        clientMcrView = view;
        clientMcrTimeControl = timeControl;
        clientViewReceivedNanos = System.nanoTime();
        clientTableRoom = room;
        clientMcrDeck = deck;
        clientMcrCloth = cloth;
        clientVariant = MahjongVariant.MCR;
    }
    public top.skyeyefast.mchjong.engine.TableRoomView clientRoom() { return clientTableRoom; }
    public SichuanSession.View clientSichuanView() { return clientSichuanView; }
    public top.skyeyefast.mchjong.item.SichuanDeck clientSichuanDeck() { return clientSichuanDeck; }
    public net.minecraft.world.item.DyeColor clientSichuanCloth() { return clientSichuanCloth; }
    public top.skyeyefast.mchjong.engine.SichuanRoomSettings clientSichuanSettings() { return clientSichuanSettings; }
    public void acceptSichuanView(SichuanSession.View view, top.skyeyefast.mchjong.engine.TableRoomView room,
                                  top.skyeyefast.mchjong.item.SichuanDeck deck, net.minecraft.world.item.DyeColor cloth,
                                  top.skyeyefast.mchjong.engine.SichuanRoomSettings settings) {
        if (level == null || !level.isClientSide) throw new IllegalStateException("Client Sichuan snapshot on server");
        if (clientTableRoom != null && clientTableRoom.tableId().equals(room.tableId())
            && clientTableRoom.incarnation().equals(room.incarnation()) && room.revision() < clientTableRoom.revision()) return;
        clientSichuanView = view;
        clientSichuanDeck = deck;
        clientSichuanCloth = cloth;
        clientSichuanSettings = java.util.Objects.requireNonNull(settings);
        clientTableRoom = room;
        clientViewReceivedNanos = System.nanoTime();
        clientVariant = MahjongVariant.SICHUAN;
    }
    public BotServiceState clientBotService() { return clientBotService; }
    public void acceptBotService(BotServiceState state) {
        if (level == null || !level.isClientSide) throw new IllegalStateException("Client bot service state on server");
        clientBotService = java.util.Objects.requireNonNull(state);
    }
    public void acceptRoom(top.skyeyefast.mchjong.engine.TableRoomView room) {
        if (level == null || !level.isClientSide) throw new IllegalStateException("Client room state on server");
        clientTableRoom = java.util.Objects.requireNonNull(room);
    }
    public WorldSettings.Policy clientWorldPolicy() { return clientWorldPolicy; }
    public void acceptWorldPolicy(WorldSettings.Policy policy) {
        if (level == null || !level.isClientSide) throw new IllegalStateException("Client world policy on server");
        clientWorldPolicy = java.util.Objects.requireNonNull(policy);
    }
    public int clientRedOptions() { return clientRedOptions; }
    public void acceptRedOptions(int options) {
        if (level == null || !level.isClientSide) throw new IllegalStateException("Client supply state on server");
        clientRedOptions = options & 63;
    }
    public long clientViewAgeMillis() { return Math.max(0, System.nanoTime() - clientViewReceivedNanos) / 1_000_000L; }
    public void acceptView(RiichiView view) {
        if (level == null || !level.isClientSide) throw new IllegalStateException("Client snapshot on server");
        if (view != null && clientView != null && clientView.tableId().equals(view.tableId()) && view.revision() < clientView.revision()) return;
        clientView = view;
        clientViewReceivedNanos = System.nanoTime();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MahjongTableBlockEntity table) {
        TableHost host = table.serverHost();
        if (host == null) return;
        host.tick();
        table.flushExperience();
        table.synchronizeEquipment();
        table.ticks++;
        table.flushReplays();
        var worldPolicy = WorldSettings.of(level.getServer()).policy();
        if (host.session().revision() != table.sentRevision || !worldPolicy.equals(table.sentWorldPolicy) || table.ticks % 40 == 0) {
            table.setChanged();
            for (ServerPlayer player : ((ServerLevel) level).players()) {
                if (player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= 24 * 24)
                    table.sendView(player, false, false);
            }
            table.sentRevision = host.session().revision();
            table.sentWorldPolicy = worldPolicy;
        }
    }

    private void flushExperience() {
        RiichiSession game = host == null ? null : host.riichi();
        if (game == null || game.pendingExperience().isEmpty()) return;
        for (var entry : game.pendingExperience().entrySet()) {
            ServerPlayer player = ((ServerLevel) level).getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            player.giveExperiencePoints(game.takeExperience(entry.getKey()));
            setChanged();
        }
    }

    private UUID authorizedViewer(ServerPlayer player) {
        TableSession current = serverSession();
        if (current == null || player.serverLevel() != level || !player.isAlive() || player.isSpectator()) return null;
        if (current.lobby() && current.seatOf(player.getUUID()) >= 0
            && player.distanceToSqr(worldPosition.getCenter()) <= 64) return player.getUUID();
        return seatedViewer(player);
    }

    private UUID seatedViewer(ServerPlayer player) {
        TableSession session = host == null ? null : host.session();
        if (player.getVehicle() instanceof SeatEntity seat && seat.tablePos().equals(worldPosition)
            && seat.getFirstPassenger() == player && session != null && session.seatOf(player.getUUID()) == seat.seat()) return player.getUUID();
        return null;
    }

    public RiichiGame participantGame(ServerPlayer player) {
        return player.serverLevel() == level && authorizedViewer(player) != null ? serverGame() : null;
    }

    public RiichiSession participantSession(ServerPlayer player) {
        return player.serverLevel() == level && authorizedViewer(player) != null ? serverRiichiSession() : null;
    }

    public TableSession participantRoom(ServerPlayer player) {
        return player.serverLevel() == level && authorizedViewer(player) != null ? serverSession() : null;
    }

    private void sendView(ServerPlayer player, boolean open, boolean controlReply) {
        TableSession current = serverSession();
        if (current != null) switch (current.variant()) {
            case MCR -> { sendMcrView(player, open, controlReply); return; }
            case SICHUAN -> { sendSichuanView(player, open, controlReply); return; }
            case RIICHI -> { }
        }
        RiichiSession session = serverRiichiSession();
        if (session == null) {
            if (open) player.displayClientMessage(Component.translatable("message.mchjong.corrupt"), false);
            return;
        }
        var policy = WorldSettings.of(level.getServer()).policy();
        UUID viewer = authorizedViewer(player);
        RiichiView snapshot = viewer == null ? session.spectatorView(policy.spectatorHandVisibility()) : session.view(viewer);
        player.connection.send(PayloadPackets.clientbound(
            new RiichiViewPayload(worldPosition, snapshot == null ? "" : TableNetworking.JSON.toJson(snapshot), open, controlReply,
                session.leaveDecision(player.getUUID()), equipment.redOptions(), session.roomView(viewer),
                session.roomSettings(), host.botState(), policy,
                session.variant())));
    }

    private void sendMcrView(ServerPlayer player, boolean open, boolean controlReply) {
        McrSession session = host == null ? null : host.mcr();
        if (session == null || player.serverLevel() != level) return;
        var stock = equipment.mcrStock();
        synchronizeSeats();
        var snapshot = session.view(player.getUUID());
        player.connection.send(PayloadPackets.clientbound(new McrViewPayload(worldPosition,
            snapshot == null ? "" : McrCodec.encodeSessionView(snapshot),
            session.roomView(authorizedViewer(player)),
            stock == null ? null : stock.deck(), equipment.clothColor(), open, controlReply, session.leaveDecision(player.getUUID()), session.timeControl(), WorldSettings.of(level.getServer()).policy())));
    }

    private void sendSichuanView(ServerPlayer player, boolean open, boolean controlReply) {
        SichuanSession session = host == null ? null : host.sichuan();
        if (session == null || player.serverLevel() != level) return;
        synchronizeSeats();
        UUID viewer = authorizedViewer(player);
        var snapshot = session.view(viewer);
        player.connection.send(PayloadPackets.clientbound(new SichuanViewPayload(worldPosition,
            snapshot == null ? "" : SichuanCodec.encodeSessionView(snapshot), session.roomView(viewer),
            equipment.sichuanStock(), equipment.clothColor(), open, controlReply, session.leaveDecision(player.getUUID()), session.roomSettings(), WorldSettings.of(level.getServer()).policy())));
    }

    public void open(ServerPlayer player) {
        if (unreadableSave != null) {
            player.displayClientMessage(Component.translatable("message.mchjong.corrupt"), false);
            return;
        }
        TableSession session = serverSession();
        if (session != null && !session.lobby() && authorizedViewer(player) == null
            && !WorldSettings.of(level.getServer()).policy().spectatingEnabled()) {
            player.displayClientMessage(Component.translatable("message.mchjong.spectating_disabled"), true);
            return;
        }
        sendView(player, true, false);
    }

    public boolean equipmentEditable() { return unreadableSave == null && (host == null || host.session().lobby()); }

    private void equipmentChanged() {
        appearanceChanged();
        sentRevision = -1;
    }

    public void openStorage(ServerPlayer player) {
        if (player.serverLevel() != level || isRemoved() || player.isSpectator() || !equipmentEditable()
            || !player.isAlive() || player.distanceToSqr(worldPosition.getCenter()) > 64) {
            player.displayClientMessage(Component.translatable("message.mchjong.equipment_locked"), true);
            return;
        }
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
            (id, inventory, owner) -> new top.skyeyefast.mchjong.item.MahjongTableMenu(id, inventory, this),
            Component.translatable("storage.mchjong.title")));
    }

    public int drawerAt(net.minecraft.world.phys.BlockHitResult hit) {
        return automatic() ? -1 : TableGeometry.drawerSide(hit.getLocation().subtract(TableGeometry.world(worldPosition, net.minecraft.world.phys.Vec3.ZERO)));
    }

    public boolean canUseSticks(net.minecraft.world.entity.player.Player player, int side) {
        return side >= 0 && side < 4 && !automatic() && unreadableSave == null && player.level() == level
            && !isRemoved() && player.isAlive() && !player.isRemoved() && !player.isSpectator()
            && player.distanceToSqr(worldPosition.getCenter()) <= 64 && level.getBlockEntity(worldPosition) == this
            && (equipmentEditable() || player instanceof ServerPlayer server && participantGame(server) != null);
    }

    public boolean canWithdrawSticks(net.minecraft.world.entity.player.Player player, int side) {
        if (!canUseSticks(player, side)) return false;
        if (equipmentEditable()) return true;
        RiichiSession game = participantSession((ServerPlayer) player);
        return game != null && (game.seatOf(player.getUUID()) == side || game.isHost(player.getUUID()) && game.trainingSeat(side));
    }

    public boolean canReceiveSticks(int side) {
        RiichiSession game = serverRiichiSession();
        return side >= 0 && side < (game == null ? 4 : game.rules().players());
    }

    public int pointScore(int side) {
        RiichiGame game = serverGame();
        return game == null || side >= game.rules().players() ? 0 : game.points(side);
    }

    public void openSticks(ServerPlayer player, int side) {
        if (!canUseSticks(player, side)) return;
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
            (id, inventory, owner) -> new top.skyeyefast.mchjong.item.PointStickMenu(id, inventory, this, side),
            Component.translatable("sticks.mchjong.title", side + 1)));
    }

    public void use(ServerPlayer player, net.minecraft.world.phys.BlockHitResult hit) {
        int side = drawerAt(hit);
        if (side >= 0) openSticks(player, side);
        else if (!removeEquipment(player, hit.getDirection())) {
            if (player.isShiftKeyDown() || !equipmentEditable()) open(player);
            else openStorage(player);
        }
    }

    /** Empty-hand sneaking on the top collects the cloth between matches. */
    public boolean removeEquipment(ServerPlayer player, net.minecraft.core.Direction face) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()
            || player.isSpectator()) return false;
        if (!equipmentEditable() || face != net.minecraft.core.Direction.UP) return false;
        var removed = equipment.removeCloth();
        if (removed.isEmpty()) return false;
        give(player, removed);
        appearanceChanged();
        sentRevision = -1;
        return true;
    }

    public boolean useEquipment(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        if (!stack.is(MahjongContent.CLOTH_ITEM)) return false;
        if (player.isSpectator() || !equipmentEditable()) {
            player.displayClientMessage(Component.translatable("message.mchjong.equipment_locked"), true);
            return true;
        }
        var previous = equipment.installCloth(stack);
        if (!player.isCreative()) stack.shrink(1);
        give(player, previous);
        appearanceChanged();
        sentRevision = -1;
        return true;
    }

    private static void give(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
    }

    public void dropEquipment() {
        if (level == null || level.isClientSide) return;
        for (var player : ((ServerLevel) level).players())
            if (player.containerMenu instanceof top.skyeyefast.mchjong.item.PointStickMenu menu && menu.belongsTo(this)) player.closeContainer();
        equipment.abandonMatch();
        // Clear before spawning: neighbor removal and explosions must never duplicate a loaded set.
        var boxes = java.util.stream.IntStream.range(0, TableEquipment.BOX_SLOTS)
            .mapToObj(slot -> equipment.boxes().removeItemNoUpdate(slot)).toList();
        equipment.boxes().setChanged();
        var cloth = equipment.removeCloth();
        var sticks = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
        for (int side = 0; side < 4; side++) for (int slot = 0; slot < TableEquipment.STICK_SLOTS; slot++)
            sticks.add(equipment.drawer(side).removeItemNoUpdate(slot));
        boxes.forEach(stack -> net.minecraft.world.level.block.Block.popResource(level, worldPosition, stack));
        net.minecraft.world.level.block.Block.popResource(level, worldPosition, cloth);
        sticks.forEach(stack -> net.minecraft.world.level.block.Block.popResource(level, worldPosition, stack));
        flushReplays();
    }

    private void flushReplays() {
        TableSession game = host == null ? null : host.session();
        boolean pending = game instanceof RiichiSession riichi && !riichi.pendingReplays().isEmpty()
            || game instanceof McrSession mcr && !mcr.pendingReplays().isEmpty()
            || game instanceof SichuanSession sichuan && !sichuan.pendingReplays().isEmpty();
        if (!(level instanceof ServerLevel server) || !pending
            || server.getGameTime() < nextArchiveRetry) return;
        try {
            if (top.skyeyefast.mchjong.replay.ReplayServer.flush(server.getServer(), game)) setChanged();
        } catch (java.io.IOException | RuntimeException failure) {
            nextArchiveRetry = server.getGameTime() + 20 * 60;
            LOGGER.error("Cannot archive completed mahjong hands at {}; they remain in the table save", worldPosition, failure);
            setChanged();
        }
    }

    public void sit(ServerPlayer player, int seat) {
        TableSession session = serverSession();
        if (session == null) { open(player); return; }
        if (seatedViewer(player) != null) { open(player); return; }
        if (seat < 0 || seat >= session.capacity()) {
            player.displayClientMessage(Component.translatable("message.mchjong.inactive_seat"), true);
            return;
        }
        if (player.isSpectator() || player.isPassenger()) return;
        BlockPos stool = TableGeometry.stool(worldPosition, seat);
        if (!level.getBlockState(stool).is(MahjongContent.STOOL)) {
            player.displayClientMessage(Component.translatable("message.mchjong.missing_stool"), true);
            return;
        }
        int membership = session.seatOf(player.getUUID());
        if (membership >= 0 && membership != seat || membership < 0 && session.occupied(seat)
            || !level.getEntitiesOfClass(SeatEntity.class, new AABB(stool).inflate(0.1), e -> !e.isRemoved() && e.isVehicle()).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.mchjong.occupied"), true);
            return;
        }
        SeatEntity mount = mount(player, seat);
        if (mount == null) return;
        if (!session.join(player.getUUID(), player.getGameProfile().getName(), seat)) {
            player.stopRiding();
            mount.discard();
            player.displayClientMessage(Component.translatable("message.mchjong.occupied"), true);
            return;
        }
        setChanged();
        sendView(player, true, false);
    }

    /** Moves a room member to the seat assigned by the authoritative game state. */
    public void autoSeat(ServerPlayer player, TableSeatPayload payload) {
        TableSession session = serverSession();
        if (session == null || !worldPosition.equals(payload.pos()) || !session.tableId().equals(payload.tableId())
            || player.serverLevel() != level || isRemoved() || level.getBlockEntity(worldPosition) != this
            || !player.isAlive() || player.isRemoved() || player.isSpectator()
            || player.distanceToSqr(worldPosition.getCenter()) > 36) return;
        int seat = session.seatOf(player.getUUID());
        if (seat < 0 || seat >= session.capacity()) return;
        BlockPos stool = TableGeometry.stool(worldPosition, seat);
        if (!level.getBlockState(stool).is(MahjongContent.STOOL)) return;
        if (player.getVehicle() instanceof SeatEntity current) {
            if (current.isRemoved() || current.getFirstPassenger() != player || !current.tablePos().equals(worldPosition)) return;
            if (current.seat() == seat) {
                if (session.join(player.getUUID(), player.getGameProfile().getName(), seat)) {
                    setChanged();
                    sentRevision = -1;
                    sendView(player, false, false);
                }
                return;
            }
        } else if (player.isPassenger()) return;

        if (!level.getEntitiesOfClass(SeatEntity.class, new AABB(stool).inflate(0.1),
                entity -> !entity.isRemoved() && entity.isVehicle()).isEmpty()) return;
        player.stopRiding();
        SeatEntity mount = mount(player, seat);
        if (mount == null) return;
        if (!session.join(player.getUUID(), player.getGameProfile().getName(), seat)) {
            player.stopRiding();
            mount.discard();
            return;
        }
        setChanged();
        sentRevision = -1;
        sendView(player, false, false);
    }

    /** Existing companions retain membership; new ones need a nearby living owner. */
    public int companionSeat(net.minecraft.world.entity.TamableAnimal companion) {
        RiichiSession current = serverRiichiSession();
        if (current == null || !WorldSettings.of(level.getServer()).policy().allowCompanionPlayers()
            || companion.level() != level || !companion.isAlive() || companion.isRemoved()) return -1;
        if (current.entityBot(companion.getUUID())) return current.seatOf(companion.getUUID());
        if (!(companion.getOwner() instanceof ServerPlayer owner) || owner.serverLevel() != level
            || !owner.isAlive() || owner.isSpectator() || owner.distanceToSqr(worldPosition.getCenter()) > 64
            || !current.lobby()) return -1;
        var seats = current.roomView(null).seats();
        for (int seat = 0; seat < current.rules().players(); seat++) {
            BlockPos stool = TableGeometry.stool(worldPosition, seat);
            if (seats.get(seat).participant().id() == null && level.getBlockState(stool).is(MahjongContent.STOOL)
                && level.getEntitiesOfClass(SeatEntity.class, new AABB(stool).inflate(0.1), e -> !e.isRemoved() && e.isVehicle()).isEmpty())
                return seat;
        }
        return -1;
    }

    public UUID companionTableId(UUID companion) {
        RiichiSession current = serverRiichiSession();
        return current != null && current.entityBot(companion) ? current.tableId() : null;
    }

    public boolean sitCompanion(net.minecraft.world.entity.TamableAnimal companion) {
        RiichiSession game = serverRiichiSession();
        if (game == null) return false;
        int seat = companionSeat(companion);
        if (seat < 0 || companion.isPassenger()) return false;
        BlockPos stool = TableGeometry.stool(worldPosition, seat);
        if (companion.distanceToSqr(stool.getCenter()) > 4 || !level.getBlockState(stool).is(MahjongContent.STOOL)
            || !level.getEntitiesOfClass(SeatEntity.class, new AABB(stool).inflate(0.1), e -> !e.isRemoved() && e.isVehicle()).isEmpty()) return false;
        SeatEntity mount = mount(companion, seat);
        if (mount == null) return false;
        Component name = companion.getName();
        String displayName = name.getContents() instanceof TranslatableContents translated
            ? translated.getKey() : name.getString();
        if (!game.entityBot(companion.getUUID())
            && !game.joinEntityBot(companion.getOwnerUUID(), companion.getUUID(), displayName, seat)) {
            companion.stopRiding();
            mount.discard();
            return false;
        }
        setChanged();
        sentRevision = -1;
        return true;
    }

    public void leaveCompanion(UUID tableId, UUID companion) {
        RiichiSession current = serverRiichiSession();
        if (current != null && current.tableId().equals(tableId)) {
            current.leaveEntityBot(companion);
            setChanged();
            sentRevision = -1;
        }
    }

    private SeatEntity mount(net.minecraft.world.entity.LivingEntity player, int seat) {
        SeatEntity mount = new SeatEntity(MahjongContent.SEAT_ENTITY, level);
        mount.initialize(worldPosition, seat, player.getUUID());
        level.addFreshEntity(mount);
        if (!player.startRiding(mount, true)) {
            mount.discard();
            return null;
        }
        player.setYRot(TableGeometry.yaw(seat));
        player.setXRot(30);
        return mount;
    }

    public void stoodUp(UUID player) {
        TableSession session = serverSession();
        var current = ((ServerLevel) level).getServer().getPlayerList().getPlayer(player);
        if (current != null && current.serverLevel() == level && seatedViewer(current) != null) return;
        if (session != null) {
            int assigned = session.seatOf(player);
            boolean voluntary = current != null && current.isAlive() && !current.isSpectator()
                && assigned >= 0 && level.getBlockState(TableGeometry.stool(worldPosition, assigned)).is(MahjongContent.STOOL);
            session.unseat(player, voluntary);
            setChanged();
            sentRevision = -1;
        }
    }

    public void act(ServerPlayer player, RiichiActionPayload payload) {
        RiichiSession session = serverRiichiSession();
        if (session == null || !session.tableId().equals(payload.tableId()) || authorizedViewer(player) == null) return;
        boolean changed = session.game() != null
            && session.game().act(player.getUUID(), payload.decision(), payload.action());
        if (changed) {
            flushExperience();
            setChanged();
            flushReplays();
        }
        sendView(player, false, false);
    }

    public void mcrAction(ServerPlayer player, McrActionPayload payload) {
        McrSession session = serverSession() instanceof McrSession mcr ? mcr : null;
        if (session == null || player.serverLevel() != level || !player.isAlive() || player.isSpectator()
            || authorizedViewer(player) == null) return;
        boolean changed = session.act(player.getUUID(), payload.tableId(), payload.incarnation(),
            payload.decision(), payload.actionIndex());
        if (changed) {
            sentRevision = -1;
            setChanged();
        }
        sendMcrView(player, false, false);
    }

    public void actRoom(ServerPlayer player, TableRoomActionPayload payload) {
        TableSession session = serverSession();
        if (session == null || player.serverLevel() != level || !player.isAlive() || player.isSpectator()
            || authorizedViewer(player) == null) return;
        var offered = session.roomView(player.getUUID()).actions();
        boolean leaving = payload.actionIndex() < offered.size()
            && offered.get(payload.actionIndex()).type() == top.skyeyefast.mchjong.engine.RoomAction.Type.LEAVE_ROOM;
        if (session.actRoom(player.getUUID(), payload.tableId(), payload.incarnation(), payload.decision(), payload.actionIndex())) {
            sentRevision = -1;
            setChanged();
            if (leaving) refreshParticipants(false);
        }
        sendView(player, false, false);
    }

    public void sichuanAction(ServerPlayer player, SichuanActionPayload payload) {
        SichuanSession session = serverSession() instanceof SichuanSession sichuan ? sichuan : null;
        if (session == null || player.serverLevel() != level || !player.isAlive() || player.isSpectator()
            || authorizedViewer(player) == null) return;
        if (session.act(player.getUUID(), payload.tableId(), payload.incarnation(), payload.decision(), payload.actionIndex())) {
            sentRevision = -1;
            setChanged();
        }
        sendSichuanView(player, false, false);
    }

    public void confirmSichuanNextHand(ServerPlayer player, top.skyeyefast.mchjong.network.SichuanNextHandPayload payload) {
        SichuanSession session = serverSession() instanceof SichuanSession sichuan ? sichuan : null;
        if (session == null || player.serverLevel() != level || !player.isAlive() || player.isSpectator()
            || authorizedViewer(player) == null) return;
        if (session.confirmNextHand(player.getUUID(), payload.tableId(), payload.incarnation(), payload.decision())) {
            sentRevision = -1;
            setChanged();
        }
        sendSichuanView(player, false, false);
    }

    public void confirmMcrNextHand(ServerPlayer player, McrNextHandPayload payload) {
        TableSession session = serverSession();
        if (!(session instanceof McrSession mcr) || player.serverLevel() != level || !player.isAlive()
            || player.isSpectator() || authorizedViewer(player) == null) return;
        if (mcr.confirmNextHand(player.getUUID(), payload.tableId(), payload.incarnation(), payload.decision())) {
            sentRevision = -1;
            setChanged();
        }
        sendMcrView(player, false, false);
    }

    /** Server-side recipient projection for world interactions and focused integration checks. */
    public McrSession.View mcrView(ServerPlayer player) {
        McrSession session = serverSession() instanceof McrSession mcr ? mcr : null;
        return session == null || player.serverLevel() != level ? null : session.view(player.getUUID());
    }

    public top.skyeyefast.mchjong.engine.TableRoomView roomView(ServerPlayer player) {
        TableSession session = serverSession();
        return session == null || player.serverLevel() != level ? null : session.roomView(authorizedViewer(player));
    }

    public void configureVariant(ServerPlayer player, TableVariantPayload payload) {
        TableSession session = serverSession();
        if (session == null || !automatic() || !session.tableId().equals(payload.tableId())
            || authorizedViewer(player) == null
            || session.roomView(player.getUUID()).seating() != top.skyeyefast.mchjong.engine.RoomSeating.Stage.GATHERING
            || !host.selectVariant(player.getUUID(), payload.decision(), payload.variant())) return;
        serverHost();
        sentRevision = -1;
        setChanged();
        sendView(player, false, true);
    }

    public void configureRules(ServerPlayer player, top.skyeyefast.mchjong.network.RiichiRulesPayload payload) {
        var current = participantSession(player);
        if (current != null && current.tableId().equals(payload.tableId())
            && equipment.canSupplyReds(payload.rules().sanma(), payload.rules().redFives())
            && current.configureRules(player.getUUID(), payload.decision(), payload.rules())) {
            serverRiichiSession(); // Recheck both physical boxes against the accepted rules before publishing readiness.
            setChanged();
            sentRevision = -1;
            refreshParticipants(false);
        }
        sendView(player, false, true);
    }

    public void configureVisibility(ServerPlayer player, top.skyeyefast.mchjong.network.RiichiVisibilityPayload payload) {
        var current = participantSession(player);
        if (current != null && current.tableId().equals(payload.tableId())
            && current.configureHandVisibility(player.getUUID(), payload.decision(), payload.visibility())) {
            setChanged();
            sentRevision = -1;
            refreshParticipants(false);
        }
        sendView(player, false, true);
    }

    public void configureSichuanRules(ServerPlayer player, top.skyeyefast.mchjong.network.SichuanRulesPayload payload) {
        var session = serverSession() instanceof SichuanSession sichuan ? sichuan : null;
        if (session == null || player.serverLevel() != level || !player.isAlive() || player.isSpectator()
            || authorizedViewer(player) == null) return;
        if (session.tableId().equals(payload.tableId()) && session.incarnation().equals(payload.incarnation())
            && session.configureRules(player.getUUID(), payload.decision(), payload.rules())) {
            setChanged();
            sentRevision = -1;
            refreshParticipants(false);
        }
        sendSichuanView(player, false, true);
    }

    public void reorderHand(ServerPlayer player, top.skyeyefast.mchjong.network.RiichiHandOrderPayload payload) {
        RiichiGame current = participantGame(player);
        if (current != null && current.tableId().equals(payload.tableId())
            && current.reorderHand(player.getUUID(), payload.decision(), payload.source(), payload.target(), payload.after())) {
            setChanged();
            sentRevision = -1;
        }
        sendView(player, false, false);
    }

    public void sessionControl(ServerPlayer player, TableSessionControlPayload payload) {
        TableSession session = player.serverLevel() == level && player.isAlive() && !player.isSpectator()
            && (payload.operation() == TableSessionControlPayload.Operation.RESOLVE_LEAVE
                || authorizedViewer(player) != null) ? serverSession() : null;
        if (session == null || !session.tableId().equals(payload.tableId())) {
            sendView(player, false, true);
            return;
        }
        boolean changed = switch (payload.operation()) {
            case CONVENIENCE_HINTS -> session.configureConvenienceHints(player.getUUID(), payload.token(), payload.enabled());
            case REQUEST_EXIT -> payload.token() == session.decision() && session.requestExit(player.getUUID());
            case ANSWER_EXIT -> session.answerExit(player.getUUID(), payload.token(), payload.enabled());
            case RESOLVE_LEAVE -> payload.token() == session.decision()
                && session.resolveLeave(player.getUUID(), payload.enabled());
        };
        if (changed) {
            refreshParticipants(payload.operation() == TableSessionControlPayload.Operation.REQUEST_EXIT);
            setChanged();
            sentRevision = -1;
            flushReplays();
        } else if (payload.operation() == TableSessionControlPayload.Operation.REQUEST_EXIT) {
            player.displayClientMessage(Component.translatable("message.mchjong.exit_unavailable"), false);
        }
        sendView(player, false, true);
    }

    public void matchAutomation(ServerPlayer player, top.skyeyefast.mchjong.network.MatchAutomationPayload payload) {
        TableSession session = serverSession();
        if (session == null || player.serverLevel() != level || !player.isAlive() || player.isSpectator()
            || authorizedViewer(player) == null) {
            sendView(player, false, true);
            return;
        }
        boolean changed = session.configureAutomation(player.getUUID(), payload.tableId(), payload.incarnation(),
            payload.decision(), payload.option(), payload.enabled());
        if (changed) {
            refreshParticipants(false);
            setChanged();
            sentRevision = -1;
        }
        sendView(player, false, true);
    }

    public void riichiControl(ServerPlayer player, RiichiControlPayload payload) {
        RiichiSession session = participantSession(player);
        if (session == null || !session.tableId().equals(payload.tableId())) {
            sendView(player, false, true);
            return;
        }
        RiichiGame game = session.game();
        boolean changed = switch (payload.operation()) {
            case AUTO_SORT -> game != null && game.configureAutoPlay(player.getUUID(), payload.token(), top.skyeyefast.mchjong.engine.RiichiAutoPlay.Option.SORT, payload.enabled());
            case AUTO_KITA -> game != null && game.configureAutoPlay(player.getUUID(), payload.token(), top.skyeyefast.mchjong.engine.RiichiAutoPlay.Option.KITA, payload.enabled());
            case OPEN_HANDS -> session.configureOpenHands(player.getUUID(), payload.token(), payload.enabled());
        };
        if (changed) {
            refreshParticipants(false);
            setChanged();
            sentRevision = -1;
            flushReplays();
        }
        sendView(player, false, true);
    }

    private void refreshParticipants(boolean openVote) {
        TableSession session = host.session();
        for (ServerPlayer participant : ((ServerLevel) level).players()) {
            if (!(participant.getVehicle() instanceof SeatEntity seat) || !seat.tablePos().equals(worldPosition)) continue;
            if (session.seatOf(participant.getUUID()) < 0) participant.stopRiding();
            sendView(participant, openVote && session.roomView(participant.getUUID()).exitVote() != null, false);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        equipment.save(tag, registries);
        if (unreadableSave != null) tag.putByteArray("session", unreadableSave);
        else if (host != null) tag.putByteArray("session", host.save().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        equipment.load(tag, registries);
        if (tag.contains("boxes")) {
            host = null;
            unreadableSave = null;
            sentRevision = -1;
        }
        if (tag.contains("session")) {
            byte[] saved = tag.getByteArray("session");
            try {
                String encoded = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(saved)).toString();
                host = TableHost.restore(encoded);
                unreadableSave = null;
            } catch (RuntimeException | java.nio.charset.CharacterCodingException error) {
                unreadableSave = saved;
                host = null;
                LOGGER.error("Cannot load mahjong table at {}. Original save retained.", worldPosition, error);
            }
        }
    }

    @Override protected void writeAppearance(CompoundTag tag) {
        super.writeAppearance(tag);
        equipment.writeAppearance(tag);
    }

}
