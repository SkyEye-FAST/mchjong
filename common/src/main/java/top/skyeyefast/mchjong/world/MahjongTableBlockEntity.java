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
    private long clientViewReceivedNanos;
    private long nextArchiveRetry;
    private final TableEquipment equipment = new TableEquipment(this::equipmentChanged);

    public TableEquipment equipment() { return equipment; }
    public boolean automatic() { return getBlockState().is(MahjongContent.AUTO_TABLE); }

    public MahjongTableBlockEntity(BlockPos pos, BlockState state) { super(MahjongContent.TABLE_ENTITY, pos, state); }

    private Game serverGame() {
        if (level == null || level.isClientSide) throw new IllegalStateException("Private state accessed outside server");
        if (unreadableSave != null) return null;
        if (game == null) game = new Game(UUID.randomUUID(), RuleSet.MAHJONG_SOUL_4, SEEDS.nextLong());
        if (game.phase() == Game.Phase.LOBBY)
            game.configureEquipment(!automatic(), !equipment.hasCloth() || equipment.deck() == null ? java.util.List.of() : equipment.deck().tiles(false));
        else if (!equipment.hasCloth() || equipment.deck() == null) return null;
        return game;
    }

    public TableView clientView() { return clientView; }
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
        table.ticks++;
        table.flushReplays();
        if (game.revision() != table.sentRevision || table.ticks % 40 == 0) {
            table.setChanged();
            for (ServerPlayer player : ((ServerLevel) level).players()) {
                if (player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= 24 * 24)
                    table.sendView(player, false);
            }
            table.sentRevision = game.revision();
        }
    }

    private UUID authorizedViewer(ServerPlayer player) {
        if (player.getVehicle() instanceof SeatEntity seat && seat.tablePos().equals(worldPosition)
            && seat.getFirstPassenger() == player && serverGame() != null
            && serverGame().seatOf(player.getUUID()) == seat.seat()) return player.getUUID();
        return null;
    }

    public Game participantGame(ServerPlayer player) {
        return player.serverLevel() == level && authorizedViewer(player) != null ? serverGame() : null;
    }

    private void sendView(ServerPlayer player, boolean open) {
        Game game = serverGame();
        if (game == null) {
            if (open) player.displayClientMessage(Component.translatable("message.mchjong.corrupt"), false);
            return;
        }
        TableView snapshot = game.view(authorizedViewer(player));
        player.connection.send(new ClientboundCustomPayloadPacket(
            new TableViewPayload(worldPosition, TableNetworking.JSON.toJson(snapshot), open)));
    }

    public void open(ServerPlayer player) { sendView(player, true); }

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
        if (authorizedViewer(player) != null) { open(player); return; }
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
        sendView(player, true);
    }

    public void stoodUp(UUID player) {
        Game game = serverGame();
        if (game != null) { game.leave(player); setChanged(); sentRevision = -1; }
    }

    public void act(ServerPlayer player, TableActionPayload payload) {
        Game game = serverGame();
        if (game == null || !game.tableId().equals(payload.tableId()) || authorizedViewer(player) == null) return;
        if (game.act(player.getUUID(), payload.decision(), payload.action())) {
            setChanged();
            flushReplays();
        }
        sendView(player, false);
    }

    public void control(ServerPlayer player, TableControlPayload payload) {
        Game game = participantGame(player);
        if (game == null || !game.tableId().equals(payload.tableId())) return;
        boolean changed = switch (payload.operation()) {
            case REQUEST_EXIT -> payload.token() == game.view(player.getUUID()).decision() && game.requestExit(player.getUUID());
            case ANSWER_EXIT -> game.answerExit(player.getUUID(), payload.token(), payload.enabled());
            case OPEN_HANDS -> game.configureOpenHands(player.getUUID(), payload.token(), payload.enabled());
        };
        if (changed) {
            refreshParticipants(payload.operation() == TableControlPayload.Operation.REQUEST_EXIT);
            setChanged();
            sentRevision = -1;
            flushReplays();
        } else if (payload.operation() == TableControlPayload.Operation.REQUEST_EXIT) {
            player.displayClientMessage(Component.translatable("message.mchjong.exit_unavailable"), false);
        }
        sendView(player, false);
    }

    private void refreshParticipants(boolean openVote) {
        for (ServerPlayer participant : ((ServerLevel) level).players()) {
            if (!(participant.getVehicle() instanceof SeatEntity seat) || !seat.tablePos().equals(worldPosition)) continue;
            if (game.seatOf(participant.getUUID()) < 0) participant.stopRiding();
            sendView(participant, openVote && game.view(participant.getUUID()).exitVote() != null);
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
