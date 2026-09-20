package top.skyeyefast.mchjong.world;

import java.security.SecureRandom;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.network.TableActionPayload;
import top.skyeyefast.mchjong.network.TableControlPayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.TableViewPayload;

public final class MahjongTableBlockEntity extends FurnitureBlockEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger("mchjong");
    private static final SecureRandom SEEDS = new SecureRandom();
    private Game game;
    private String unreadableSave;
    private int ticks;
    private long sentRevision = -1;
    private TableView clientView;
    private top.skyeyefast.mchjong.engine.RoomView clientRoom;
    private int clientRedOptions;
    private long clientViewReceivedNanos;
    private long nextArchiveRetry;
    private final TableEquipment equipment = new TableEquipment(this::equipmentChanged);
    private boolean syncingEquipment;

    public TableEquipment equipment() { return equipment; }
    public boolean automatic() { return getBlockState().is(MahjongContent.AUTO_TABLE); }

    public MahjongTableBlockEntity(BlockPos pos, BlockState state) { super(MahjongContent.TABLE_ENTITY, pos, state); }

    private Game serverGame() {
        if (level == null || level.isClientSide) throw new IllegalStateException("Private state accessed outside server");
        if (unreadableSave != null) return null;
        if (game == null) game = new Game(UUID.randomUUID(), RuleSet.MAHJONG_SOUL_4.config()
            .with(top.skyeyefast.mchjong.engine.RuleOption.RED_FIVES, top.skyeyefast.mchjong.engine.RedFives.NONE.ordinal()), SEEDS.nextLong());
        var policy = WorldSettings.of(level.getServer()).policy();
        game.configureWorld(policy.openHands(), policy.invitationTeleport());
        synchronizeEquipment();
        if (equipment.selectRules(game.rules())) appearanceChanged();
        if (game.phase() == Game.Phase.LOBBY)
            game.configureEquipment(!automatic(), !equipment.hasCloth() || equipment.deck() == null
                || !automatic() && !equipment.manualSuppliesReady() ? java.util.List.of() : equipment.deck().tiles());
        else if (!equipment.hasCloth() || equipment.deck() == null) return null;
        synchronizeSeats();
        return game;
    }

    private void synchronizeEquipment() {
        boolean active = game.phase() != Game.Phase.LOBBY && game.phase() != Game.Phase.MATCH_END;
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
        var mounted = new java.util.HashMap<UUID, Integer>();
        for (SeatEntity seat : level.getEntitiesOfClass(SeatEntity.class, new AABB(worldPosition).inflate(4))) {
            if (!seat.tablePos().equals(worldPosition) || seat.isRemoved()
                || !(seat.getFirstPassenger() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) continue;
            int assigned = game.seatOf(player.getUUID());
            if (assigned != seat.seat()) {
                player.stopRiding();
                if (assigned >= 0) {
                    BlockPos destination = TableGeometry.stool(worldPosition, assigned);
                    player.sendSystemMessage(Component.translatable("message.mchjong.assigned_seat",
                        Component.translatable("wind.mchjong." + new String[]{"east", "south", "west", "north"}[assigned]),
                        destination.getX(), destination.getY(), destination.getZ()));
                }
            } else if (level.getBlockState(TableGeometry.stool(worldPosition, seat.seat())).is(MahjongContent.STOOL))
                mounted.put(player.getUUID(), seat.seat());
        }
        game.synchronizeSeats(mounted);
    }

    public TableView clientView() { return clientView; }
    public top.skyeyefast.mchjong.engine.RoomView clientRoom() { return clientRoom; }
    public void acceptRoom(top.skyeyefast.mchjong.engine.RoomView room) {
        if (level == null || !level.isClientSide) throw new IllegalStateException("Client room state on server");
        clientRoom = java.util.Objects.requireNonNull(room);
    }
    public int clientRedOptions() { return clientRedOptions; }
    public void acceptRedOptions(int options) {
        if (level == null || !level.isClientSide) throw new IllegalStateException("Client supply state on server");
        clientRedOptions = options & 63;
    }
    public long clientViewAgeMillis() { return Math.max(0, System.nanoTime() - clientViewReceivedNanos) / 1_000_000L; }
    public void acceptView(TableView view) {
        if (level == null || !level.isClientSide) throw new IllegalStateException("Client snapshot on server");
        if (clientView != null && clientView.tableId().equals(view.tableId()) && view.revision() < clientView.revision()) return;
        clientView = view;
        clientViewReceivedNanos = System.nanoTime();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MahjongTableBlockEntity table) {
        Game game = table.serverGame();
        if (game == null) return;
        game.tick();
        table.synchronizeEquipment();
        table.ticks++;
        table.flushReplays();
        if (game.revision() != table.sentRevision || table.ticks % 40 == 0) {
            table.setChanged();
            for (ServerPlayer player : ((ServerLevel) level).players()) {
                if (player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= 24 * 24)
                    table.sendView(player, false, false);
            }
            table.sentRevision = game.revision();
        }
    }

    private UUID authorizedViewer(ServerPlayer player) {
        Game current = serverGame();
        if (current == null || player.serverLevel() != level || !player.isAlive() || player.isSpectator()) return null;
        if (current.phase() == Game.Phase.LOBBY && current.seatOf(player.getUUID()) >= 0
            && player.distanceToSqr(worldPosition.getCenter()) <= 64) return player.getUUID();
        return seatedViewer(player);
    }

    private UUID seatedViewer(ServerPlayer player) {
        if (player.getVehicle() instanceof SeatEntity seat && seat.tablePos().equals(worldPosition)
            && seat.getFirstPassenger() == player && game != null && game.seatOf(player.getUUID()) == seat.seat()) return player.getUUID();
        return null;
    }

    public Game participantGame(ServerPlayer player) {
        return player.serverLevel() == level && authorizedViewer(player) != null ? serverGame() : null;
    }

    private void sendView(ServerPlayer player, boolean open, boolean controlReply) {
        Game game = serverGame();
        if (game == null) {
            if (open) player.displayClientMessage(Component.translatable("message.mchjong.corrupt"), false);
            return;
        }
        TableView snapshot = game.view(authorizedViewer(player));
        player.connection.send(new ClientboundCustomPayloadPacket(
            new TableViewPayload(worldPosition, TableNetworking.JSON.toJson(snapshot), open, controlReply, equipment.redOptions(), game.roomView())));
    }

    public void open(ServerPlayer player) { sendView(player, true, false); }

    public boolean equipmentEditable() { return unreadableSave == null && (game == null || game.phase() == Game.Phase.LOBBY); }

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
        Game game = participantGame((ServerPlayer) player);
        return game != null && (game.seatOf(player.getUUID()) == side || game.isHost(player.getUUID()) && game.trainingSeat(side));
    }

    public int pointScore(int side) {
        Game game = serverGame();
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
            if (player.isShiftKeyDown()) open(player);
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
        if (!(level instanceof ServerLevel server) || game == null || game.pendingReplays().isEmpty()
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
        Game game = serverGame();
        if (game == null) { open(player); return; }
        if (seatedViewer(player) != null) { open(player); return; }
        if (seat < 0 || seat >= game.rules().players()) {
            player.displayClientMessage(Component.translatable("message.mchjong.inactive_seat"), true);
            return;
        }
        if (player.isSpectator() || player.isPassenger()) return;
        BlockPos stool = TableGeometry.stool(worldPosition, seat);
        if (!level.getBlockState(stool).is(MahjongContent.STOOL)) {
            player.displayClientMessage(Component.translatable("message.mchjong.missing_stool"), true);
            return;
        }
        if (!level.getEntitiesOfClass(SeatEntity.class, new AABB(stool).inflate(0.1), e -> !e.isRemoved() && e.isVehicle()).isEmpty()
            || !game.join(player.getUUID(), player.getGameProfile().getName(), seat)) {
            player.displayClientMessage(Component.translatable("message.mchjong.occupied"), true);
            return;
        }
        SeatEntity mount = new SeatEntity(MahjongContent.SEAT_ENTITY, level);
        mount.initialize(worldPosition, seat, player.getUUID());
        level.addFreshEntity(mount);
        if (!player.startRiding(mount, true)) {
            mount.discard();
            game.leave(player.getUUID());
            return;
        }
        player.setYRot(TableGeometry.yaw(seat));
        player.setXRot(30);
        setChanged();
        sendView(player, true, false);
    }

    public void stoodUp(UUID player) {
        Game game = serverGame();
        var current = ((ServerLevel) level).getServer().getPlayerList().getPlayer(player);
        if (current != null && current.serverLevel() == level && seatedViewer(current) != null) return;
        if (game != null) { game.leave(player); setChanged(); sentRevision = -1; }
    }

    public void act(ServerPlayer player, TableActionPayload payload) {
        Game game = serverGame();
        if (game == null || !game.tableId().equals(payload.tableId()) || authorizedViewer(player) == null) return;
        var actions = game.view(player.getUUID()).actions();
        if (payload.action() >= 0 && payload.action() < actions.size()) {
            var action = actions.get(payload.action());
            if (action.type() == top.skyeyefast.mchjong.engine.Action.Type.CHANGE_RULE) {
                var proposed = game.rules().withPreset(RuleSet.values()[action.tiles().getFirst()]);
                if (!equipment.canSupplyReds(proposed.sanma(), proposed.redFives())) {
                    sendView(player, false, false);
                    return;
                }
            }
        }
        if (game.act(player.getUUID(), payload.decision(), payload.action())) {
            setChanged();
            flushReplays();
        }
        sendView(player, false, false);
    }

    public void configureRules(ServerPlayer player, top.skyeyefast.mchjong.network.TableRulesPayload payload) {
        var current = participantGame(player);
        if (current != null && current.tableId().equals(payload.tableId())
            && equipment.canSupplyReds(payload.rules().sanma(), payload.rules().redFives())
            && current.configureRules(player.getUUID(), payload.decision(), payload.rules())) {
            serverGame(); // Recheck both physical boxes against the accepted rules before publishing readiness.
            setChanged();
            sentRevision = -1;
            refreshParticipants(false);
        }
        sendView(player, false, true);
    }

    public void control(ServerPlayer player, TableControlPayload payload) {
        Game game = participantGame(player);
        if (game == null || !game.tableId().equals(payload.tableId())) {
            sendView(player, false, true);
            return;
        }
        boolean changed = switch (payload.operation()) {
            case REQUEST_EXIT -> payload.token() == game.view(player.getUUID()).decision() && game.requestExit(player.getUUID());
            case ANSWER_EXIT -> game.answerExit(player.getUUID(), payload.token(), payload.enabled());
            case AUTO_SORT -> game.configureAutoPlay(player.getUUID(), payload.token(), top.skyeyefast.mchjong.engine.AutoPlay.Option.SORT, payload.enabled());
            case AUTO_WIN -> game.configureAutoPlay(player.getUUID(), payload.token(), top.skyeyefast.mchjong.engine.AutoPlay.Option.WIN, payload.enabled());
            case NO_CALLS -> game.configureAutoPlay(player.getUUID(), payload.token(), top.skyeyefast.mchjong.engine.AutoPlay.Option.NO_CALLS, payload.enabled());
            case AUTO_DISCARD -> game.configureAutoPlay(player.getUUID(), payload.token(), top.skyeyefast.mchjong.engine.AutoPlay.Option.DISCARD, payload.enabled());
            case AUTO_KITA -> game.configureAutoPlay(player.getUUID(), payload.token(), top.skyeyefast.mchjong.engine.AutoPlay.Option.KITA, payload.enabled());
        };
        if (changed) {
            refreshParticipants(payload.operation() == TableControlPayload.Operation.REQUEST_EXIT);
            setChanged();
            sentRevision = -1;
            flushReplays();
        } else if (payload.operation() == TableControlPayload.Operation.REQUEST_EXIT) {
            player.displayClientMessage(Component.translatable("message.mchjong.exit_unavailable"), false);
        }
        sendView(player, false, true);
    }

    private void refreshParticipants(boolean openVote) {
        for (ServerPlayer participant : ((ServerLevel) level).players()) {
            if (!(participant.getVehicle() instanceof SeatEntity seat) || !seat.tablePos().equals(worldPosition)) continue;
            if (game.seatOf(participant.getUUID()) < 0) participant.stopRiding();
            sendView(participant, openVote && game.view(participant.getUUID()).exitVote() != null, false);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        equipment.save(tag, registries);
        if (unreadableSave != null) tag.putString("game", unreadableSave);
        else if (game != null) tag.putString("game", TableNetworking.JSON.toJson(game));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        equipment.load(tag, registries);
        if (tag.contains("boxes")) {
            game = null;
            unreadableSave = null;
            sentRevision = -1;
        }
        if (!tag.contains("game")) return;
        String saved = tag.getString("game");
        try {
            Game restored = TableNetworking.JSON.fromJson(saved, Game.class);
            restored.validate();
            game = restored;
            unreadableSave = null;
        } catch (RuntimeException error) {
            unreadableSave = saved;
            game = null;
            LOGGER.error("Cannot load mahjong table at {}. Original save retained.", worldPosition, error);
        }
    }

    @Override protected void writeAppearance(CompoundTag tag) {
        super.writeAppearance(tag);
        equipment.writeAppearance(tag);
    }
}
