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
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.GameType;
import top.skyeyefast.mchjong.engine.McrCodec;
import top.skyeyefast.mchjong.engine.McrSession;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.network.TableActionPayload;
import top.skyeyefast.mchjong.network.McrActionPayload;
import top.skyeyefast.mchjong.network.McrViewPayload;
import top.skyeyefast.mchjong.network.TableControlPayload;
import top.skyeyefast.mchjong.network.TableGameTypePayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.TableSeatPayload;
import top.skyeyefast.mchjong.network.TableViewPayload;

public final class MahjongTableBlockEntity extends FurnitureBlockEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger("mchjong");
    private static final SecureRandom SEEDS = new SecureRandom();
    private Game game;
    private GameType gameType = GameType.RIICHI;
    private McrTableHost mcrHost;
    private String unreadableMcrSave;
    private final BotServiceClient botService = new BotServiceClient();
    private String unreadableSave;
    private int ticks;
    private long sentRevision = -1;
    private long sentMcrRevision = -1;
    private WorldSettings.Policy sentWorldPolicy;
    private TableView clientView;
    private McrSession.View clientMcrView;
    private top.skyeyefast.mchjong.item.McrDeck clientMcrDeck;
    private net.minecraft.world.item.DyeColor clientMcrCloth;
    private GameType clientGameType = GameType.RIICHI;
    private top.skyeyefast.mchjong.engine.RoomView clientRoom;
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

    private Game serverGame() {
        if (level == null || level.isClientSide) throw new IllegalStateException("Private state accessed outside server");
        if (unreadableSave != null) return null;
        if (game == null) game = new Game(UUID.randomUUID(), RuleSet.MAHJONG_SOUL_4.config()
            .with(top.skyeyefast.mchjong.engine.RuleOption.RED_FIVES, top.skyeyefast.mchjong.engine.RedFives.NONE.ordinal()), SEEDS.nextLong());
        var policy = WorldSettings.of(level.getServer()).policy();
        game.configureExternalBots(BotServiceClient.availableBots());
        game.configureWorld(policy.gamePolicy());
        synchronizeEquipment();
        if (game.phase() == Game.Phase.LOBBY && !equipment.canSupplyReds(game.rules().sanma(), game.rules().redFives()))
            for (var reds : new top.skyeyefast.mchjong.engine.RedFives[]{top.skyeyefast.mchjong.engine.RedFives.THREE,
                top.skyeyefast.mchjong.engine.RedFives.FOUR, top.skyeyefast.mchjong.engine.RedFives.NONE})
                if (game.rules().preset().allows(reds) && equipment.canSupplyReds(game.rules().sanma(), reds)) {
                    game.configureStockRedFives(reds);
                    break;
                }
        if (equipment.selectRules(game.rules())) appearanceChanged();
        if (game.phase() == Game.Phase.LOBBY) {
            var mcrStock = gameType == GameType.MCR && automatic() ? equipment.mcrStock() : null;
            game.configureEquipment(!automatic(), !equipment.hasCloth() || gameType == GameType.MCR && mcrStock == null
                || gameType == GameType.RIICHI && (equipment.deck() == null || !automatic() && !equipment.manualSuppliesReady())
                ? java.util.List.of() : gameType == GameType.MCR
                ? mcrStock.deck().tiles().stream().filter(tile -> tile < 136).toList() : equipment.deck().tiles());
        }
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
        var connected = new java.util.HashSet<UUID>();
        for (ServerPlayer player : ((ServerLevel) level).getServer().getPlayerList().getPlayers()) connected.add(player.getUUID());
        for (SeatEntity seat : level.getEntitiesOfClass(SeatEntity.class, new AABB(worldPosition).inflate(4))) {
            if (seat.tablePos().equals(worldPosition) && !seat.isRemoved()
                && seat.getFirstPassenger() instanceof net.minecraft.world.entity.TamableAnimal companion) {
                int assigned = game.entityBot(companion.getUUID()) ? game.seatOf(companion.getUUID()) : -1;
                if (assigned < 0 || !companion.isAlive()
                    || !level.getBlockState(TableGeometry.stool(worldPosition, assigned)).is(MahjongContent.STOOL)) {
                    companion.stopRiding();
                    seat.discard();
                    game.leaveEntityBot(companion.getUUID());
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
            int assigned = game.seatOf(player.getUUID());
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
        game.synchronizeSeats(mounted, connected);
    }

    public TableView clientView() { return clientView; }
    public GameType clientGameType() { return clientGameType; }
    public void acceptGameType(GameType value) { clientGameType = java.util.Objects.requireNonNull(value); }
    public McrSession.View clientMcrView() { return clientMcrView; }
    public top.skyeyefast.mchjong.item.McrDeck clientMcrDeck() { return clientMcrDeck; }
    public net.minecraft.world.item.DyeColor clientMcrCloth() { return clientMcrCloth; }
    public void acceptMcrView(McrSession.View view, top.skyeyefast.mchjong.item.McrDeck deck,
                              net.minecraft.world.item.DyeColor cloth) {
        if (level == null || !level.isClientSide) throw new IllegalStateException("Client MCR snapshot on server");
        if (clientMcrView != null && clientMcrView.tableId().equals(view.tableId())
            && clientMcrView.incarnation().equals(view.incarnation()) && view.revision() < clientMcrView.revision()) return;
        clientMcrView = view;
        clientMcrDeck = deck;
        clientMcrCloth = cloth;
        clientGameType = GameType.MCR;
    }
    public top.skyeyefast.mchjong.engine.RoomView clientRoom() { return clientRoom; }
    public BotServiceState clientBotService() { return clientBotService; }
    public void acceptBotService(BotServiceState state) {
        if (level == null || !level.isClientSide) throw new IllegalStateException("Client bot service state on server");
        clientBotService = java.util.Objects.requireNonNull(state);
    }
    public void acceptRoom(top.skyeyefast.mchjong.engine.RoomView room) {
        if (level == null || !level.isClientSide) throw new IllegalStateException("Client room state on server");
        clientRoom = java.util.Objects.requireNonNull(room);
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
    public void acceptView(TableView view) {
        if (level == null || !level.isClientSide) throw new IllegalStateException("Client snapshot on server");
        if (clientView != null && clientView.tableId().equals(view.tableId()) && view.revision() < clientView.revision()) return;
        clientView = view;
        clientViewReceivedNanos = System.nanoTime();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MahjongTableBlockEntity table) {
        if (table.unreadableMcrSave != null) return;
        if (table.mcrHost != null) {
            table.mcrHost.synchronizeSeats((ServerLevel) level, pos);
            table.ticks++;
            long revision = table.mcrHost.revision();
            if (revision != table.sentMcrRevision || table.ticks % 40 == 0) {
                table.setChanged();
                for (ServerPlayer player : ((ServerLevel) level).players())
                    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= 24 * 24)
                        table.sendMcrView(player, false);
                table.sentMcrRevision = revision;
            }
            return;
        }
        Game game = table.serverGame();
        if (game == null) return;
        game.tick();
        table.botService.tick(game);
        table.flushExperience();
        table.synchronizeEquipment();
        table.ticks++;
        table.flushReplays();
        var worldPolicy = WorldSettings.of(level.getServer()).policy();
        if (game.revision() != table.sentRevision || !worldPolicy.equals(table.sentWorldPolicy) || table.ticks % 40 == 0) {
            table.setChanged();
            for (ServerPlayer player : ((ServerLevel) level).players()) {
                if (player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= 24 * 24)
                    table.sendView(player, false, false);
            }
            table.sentRevision = game.revision();
            table.sentWorldPolicy = worldPolicy;
        }
    }

    private void flushExperience() {
        if (game == null || game.pendingExperience().isEmpty()) return;
        for (var entry : game.pendingExperience().entrySet()) {
            ServerPlayer player = ((ServerLevel) level).getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            player.giveExperiencePoints(game.takeExperience(entry.getKey()));
            setChanged();
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
        if (mcrHost != null) { sendMcrView(player, open); return; }
        Game game = serverGame();
        if (game == null) {
            if (open) player.displayClientMessage(Component.translatable("message.mchjong.corrupt"), false);
            return;
        }
        var policy = WorldSettings.of(level.getServer()).policy();
        UUID viewer = authorizedViewer(player);
        TableView snapshot = viewer == null ? game.spectatorView(policy.spectatorHandVisibility()) : game.view(viewer);
        player.connection.send(PayloadPackets.clientbound(
            new TableViewPayload(worldPosition, TableNetworking.JSON.toJson(snapshot), open, controlReply,
                game.leaveDecision(player.getUUID()), equipment.redOptions(), game.roomView(), botService.state(game), policy,
                gameType)));
    }

    private void sendMcrView(ServerPlayer player, boolean open) {
        if (mcrHost == null || player.serverLevel() != level) return;
        var stock = equipment.mcrStock();
        if (stock == null || !equipment.hasCloth()) return;
        mcrHost.synchronizeSeats((ServerLevel) level, worldPosition);
        var snapshot = mcrHost.view(player.getUUID());
        player.connection.send(PayloadPackets.clientbound(new McrViewPayload(worldPosition,
            McrCodec.encodeSessionView(snapshot), stock.deck(), equipment.clothColor(), open)));
    }

    public void open(ServerPlayer player) {
        if (unreadableMcrSave != null) {
            player.displayClientMessage(Component.translatable("message.mchjong.corrupt"), false);
            return;
        }
        if (mcrHost != null) {
            if (mcrHost.seatOf(player.getUUID()) < 0
                && !WorldSettings.of(level.getServer()).policy().spectatingEnabled()) {
                player.displayClientMessage(Component.translatable("message.mchjong.spectating_disabled"), true);
                return;
            }
            sendMcrView(player, true);
            return;
        }
        Game current = serverGame();
        if (current != null && current.phase() != Game.Phase.LOBBY && authorizedViewer(player) == null
            && !WorldSettings.of(level.getServer()).policy().spectatingEnabled()) {
            player.displayClientMessage(Component.translatable("message.mchjong.spectating_disabled"), true);
            return;
        }
        sendView(player, true, false);
    }

    public boolean equipmentEditable() { return unreadableSave == null && unreadableMcrSave == null && mcrHost == null
        && (game == null || game.phase() == Game.Phase.LOBBY); }

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

    public boolean canReceiveSticks(int side) {
        Game game = serverGame();
        return side >= 0 && side < (game == null ? 4 : game.rules().players());
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
        if (mcrHost != null) {
            if (seat < 0 || seat > 3 || mcrHost.seatOf(player.getUUID()) != seat
                || player.isSpectator() || player.isPassenger()
                || !level.getBlockState(TableGeometry.stool(worldPosition, seat)).is(MahjongContent.STOOL)) return;
            BlockPos stool = TableGeometry.stool(worldPosition, seat);
            if (!level.getEntitiesOfClass(SeatEntity.class, new AABB(stool).inflate(0.1),
                entity -> !entity.isRemoved() && entity.isVehicle()).isEmpty()) return;
            if (mount(player, seat) != null) {
                mcrHost.synchronizeSeats((ServerLevel) level, worldPosition);
                setChanged();
                sendMcrView(player, true);
            }
            return;
        }
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
        int membership = game.seatOf(player.getUUID());
        if (membership >= 0 && membership != seat || membership < 0 && game.view(null).seats().get(seat).occupied()
            || !level.getEntitiesOfClass(SeatEntity.class, new AABB(stool).inflate(0.1), e -> !e.isRemoved() && e.isVehicle()).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.mchjong.occupied"), true);
            return;
        }
        SeatEntity mount = mount(player, seat);
        if (mount == null) return;
        if (!game.join(player.getUUID(), player.getGameProfile().getName(), seat)) {
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
        if (mcrHost != null) {
            if (!worldPosition.equals(payload.pos()) || !mcrHost.tableId().equals(payload.tableId())
                || player.serverLevel() != level || !player.isAlive() || player.isSpectator()
                || player.distanceToSqr(worldPosition.getCenter()) > 36) return;
            int seat = mcrHost.seatOf(player.getUUID());
            if (seat < 0) return;
            if (player.getVehicle() instanceof SeatEntity current && current.tablePos().equals(worldPosition)
                && current.seat() == seat) { sendMcrView(player, false); return; }
            if (player.isPassenger()) return;
            sit(player, seat);
            return;
        }
        Game game = serverGame();
        if (game == null || !worldPosition.equals(payload.pos()) || !game.tableId().equals(payload.tableId())
            || player.serverLevel() != level || isRemoved() || level.getBlockEntity(worldPosition) != this
            || !player.isAlive() || player.isRemoved() || player.isSpectator()
            || player.distanceToSqr(worldPosition.getCenter()) > 36) return;
        int seat = game.seatOf(player.getUUID());
        if (seat < 0 || seat >= game.rules().players()) return;
        BlockPos stool = TableGeometry.stool(worldPosition, seat);
        if (!level.getBlockState(stool).is(MahjongContent.STOOL)) return;
        if (player.getVehicle() instanceof SeatEntity current) {
            if (current.isRemoved() || current.getFirstPassenger() != player || !current.tablePos().equals(worldPosition)) return;
            if (current.seat() == seat) {
                if (game.join(player.getUUID(), player.getGameProfile().getName(), seat)) {
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
        if (!game.join(player.getUUID(), player.getGameProfile().getName(), seat)) {
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
        if (gameType == GameType.MCR) return -1;
        Game current = serverGame();
        if (current == null || !WorldSettings.of(level.getServer()).policy().allowCompanionPlayers()
            || companion.level() != level || !companion.isAlive() || companion.isRemoved()) return -1;
        if (current.entityBot(companion.getUUID())) return current.seatOf(companion.getUUID());
        if (!(companion.getOwner() instanceof ServerPlayer owner) || owner.serverLevel() != level
            || !owner.isAlive() || owner.isSpectator() || owner.distanceToSqr(worldPosition.getCenter()) > 64
            || current.phase() != Game.Phase.LOBBY) return -1;
        var seats = current.view(null).seats();
        for (int seat = 0; seat < current.rules().players(); seat++) {
            BlockPos stool = TableGeometry.stool(worldPosition, seat);
            if (!seats.get(seat).occupied() && level.getBlockState(stool).is(MahjongContent.STOOL)
                && level.getEntitiesOfClass(SeatEntity.class, new AABB(stool).inflate(0.1), e -> !e.isRemoved() && e.isVehicle()).isEmpty())
                return seat;
        }
        return -1;
    }

    public UUID companionTableId(UUID companion) {
        Game current = serverGame();
        return current != null && current.entityBot(companion) ? current.tableId() : null;
    }

    public boolean sitCompanion(net.minecraft.world.entity.TamableAnimal companion) {
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
        Game current = serverGame();
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
        if (mcrHost != null) {
            mcrHost.synchronizeSeats((ServerLevel) level, worldPosition);
            setChanged();
            sentMcrRevision = -1;
            return;
        }
        Game game = serverGame();
        var current = ((ServerLevel) level).getServer().getPlayerList().getPlayer(player);
        if (current != null && current.serverLevel() == level && seatedViewer(current) != null) return;
        if (game != null) {
            int assigned = game.seatOf(player);
            boolean voluntary = current != null && current.isAlive() && !current.isSpectator()
                && assigned >= 0 && level.getBlockState(TableGeometry.stool(worldPosition, assigned)).is(MahjongContent.STOOL);
            game.unseat(player, voluntary);
            setChanged();
            sentRevision = -1;
        }
    }

    public void act(ServerPlayer player, TableActionPayload payload) {
        if (mcrHost != null) return;
        Game game = serverGame();
        if (game == null || !game.tableId().equals(payload.tableId()) || authorizedViewer(player) == null) return;
        var actions = game.view(player.getUUID()).actions();
        top.skyeyefast.mchjong.engine.Action.Type requested = null;
        if (payload.action() >= 0 && payload.action() < actions.size()) {
            var action = actions.get(payload.action());
            requested = action.type();
            if (gameType == GameType.MCR && requested != top.skyeyefast.mchjong.engine.Action.Type.LEAVE_ROOM
                && requested != top.skyeyefast.mchjong.engine.Action.Type.BEGIN_SEATING
                && requested != top.skyeyefast.mchjong.engine.Action.Type.READY) return;
            if (action.type() == top.skyeyefast.mchjong.engine.Action.Type.CHANGE_RULE) {
                var proposed = game.rules().withPreset(RuleSet.values()[action.tiles().getFirst()]);
                if (!equipment.canSupplyReds(proposed.sanma(), proposed.redFives())) {
                    sendView(player, false, false);
                    return;
                }
            }
        }
        if (gameType == GameType.MCR && requested == top.skyeyefast.mchjong.engine.Action.Type.READY
            && payload.decision() == game.view(player.getUUID()).decision()
            && startMcrIfReady(game, player)) return;
        if (game.act(player.getUUID(), payload.decision(), payload.action())) {
            flushExperience();
            if (requested == top.skyeyefast.mchjong.engine.Action.Type.LEAVE_ROOM) refreshParticipants(false);
            setChanged();
            flushReplays();
        }
        sendView(player, false, false);
    }

    private boolean startMcrIfReady(Game lobby, ServerPlayer last) {
        if (!automatic() || !equipment.hasCloth() || equipment.mcrStock() == null
            || lobby.roomView().seating() != top.skyeyefast.mchjong.engine.RoomSeating.Stage.POSITIONING) return false;
        var seats = lobby.view(null).seats();
        if (seats.size() != 4 || seats.stream().anyMatch(seat -> !seat.occupied() || seat.bot())) return false;
        int lastSeat = lobby.seatOf(last.getUUID());
        if (lastSeat < 0 || seats.get(lastSeat).ready()) return false;
        for (int seat = 0; seat < 4; seat++) if (seat != lastSeat && !seats.get(seat).ready()) return false;
        var roster = new java.util.ArrayList<McrSession.Participant>(4);
        for (int seat = 0; seat < 4; seat++) {
            int expected = seat;
            var players = level.getEntitiesOfClass(SeatEntity.class, new AABB(worldPosition).inflate(4)).stream()
                .filter(mount -> mount.tablePos().equals(worldPosition) && mount.seat() == expected
                    && mount.getFirstPassenger() instanceof ServerPlayer player
                    && player.isAlive() && !player.isSpectator() && lobby.seatOf(player.getUUID()) == expected
                    && level.getBlockState(TableGeometry.stool(worldPosition, expected)).is(MahjongContent.STOOL))
                .map(mount -> (ServerPlayer) mount.getFirstPassenger()).toList();
            if (players.size() != 1) return false;
            var player = players.getFirst();
            roster.add(new McrSession.Participant(player.getUUID(), player.getGameProfile().getName()));
        }
        var stock = equipment.mcrStock();
        var host = McrTableHost.start(lobby.tableId(), roster, SEEDS.nextLong(), stock.deck().tiles());
        host.synchronizeSeats((ServerLevel) level, worldPosition);
        if (host.view(null).seated() != 15) return false;
        mcrHost = host;
        sentMcrRevision = -1;
        setChanged();
        for (ServerPlayer participant : ((ServerLevel) level).players())
            if (host.seatOf(participant.getUUID()) >= 0) sendMcrView(participant, true);
        return true;
    }

    public void mcrAction(ServerPlayer player, McrActionPayload payload) {
        if (mcrHost == null || player.serverLevel() != level || !player.isAlive() || player.isSpectator()) return;
        mcrHost.synchronizeSeats((ServerLevel) level, worldPosition);
        boolean changed = payload.actionIndex() == -1
            ? mcrHost.confirm(player, payload.tableId(), payload.incarnation(), payload.decision())
            : mcrHost.act(player, payload.tableId(), payload.incarnation(), payload.decision(), payload.actionIndex());
        if (changed) {
            sentMcrRevision = -1;
            setChanged();
        }
        sendMcrView(player, false);
    }

    /** Server-side recipient projection for world interactions and focused integration checks. */
    public McrSession.View mcrView(ServerPlayer player) {
        if (mcrHost == null || player.serverLevel() != level) return null;
        mcrHost.synchronizeSeats((ServerLevel) level, worldPosition);
        return mcrHost.view(player.getUUID());
    }

    public void configureGameType(ServerPlayer player, TableGameTypePayload payload) {
        Game lobby = serverGame();
        if (lobby == null || mcrHost != null || !automatic() || !lobby.tableId().equals(payload.tableId())
            || payload.decision() != lobby.view(player.getUUID()).decision() || !lobby.isHost(player.getUUID())
            || authorizedViewer(player) == null || lobby.phase() != Game.Phase.LOBBY
            || lobby.roomView().seating() != top.skyeyefast.mchjong.engine.RoomSeating.Stage.GATHERING) return;
        if (payload.gameType() == GameType.MCR) {
            if (lobby.view(null).seats().stream().anyMatch(TableView.Seat::bot)) return;
            var config = RuleSet.MAHJONG_SOUL_4.config().with(top.skyeyefast.mchjong.engine.RuleOption.RED_FIVES,
                top.skyeyefast.mchjong.engine.RedFives.NONE.ordinal());
            if (!lobby.rules().equals(config) && !lobby.configureRules(player.getUUID(), payload.decision(), config)) return;
        }
        gameType = payload.gameType();
        serverGame();
        sentRevision = -1;
        setChanged();
        sendView(player, false, true);
    }

    public void configureRules(ServerPlayer player, top.skyeyefast.mchjong.network.TableRulesPayload payload) {
        if (gameType == GameType.MCR) return;
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

    public void configureVisibility(ServerPlayer player, top.skyeyefast.mchjong.network.TableVisibilityPayload payload) {
        if (gameType == GameType.MCR) return;
        var current = participantGame(player);
        if (current != null && current.tableId().equals(payload.tableId())
            && current.configureHandVisibility(player.getUUID(), payload.decision(), payload.visibility())) {
            setChanged();
            sentRevision = -1;
            refreshParticipants(false);
        }
        sendView(player, false, true);
    }

    public void reorderHand(ServerPlayer player, top.skyeyefast.mchjong.network.TableHandOrderPayload payload) {
        if (gameType == GameType.MCR) return;
        Game current = participantGame(player);
        if (current != null && current.tableId().equals(payload.tableId())
            && current.reorderHand(player.getUUID(), payload.decision(), payload.source(), payload.target(), payload.after())) {
            setChanged();
            sentRevision = -1;
        }
        sendView(player, false, false);
    }

    public void control(ServerPlayer player, TableControlPayload payload) {
        if (gameType == GameType.MCR) return;
        Game game = payload.operation() == TableControlPayload.Operation.RESOLVE_LEAVE
            && player.serverLevel() == level && player.isAlive() && !player.isSpectator()
            ? serverGame() : participantGame(player);
        if (game == null || !game.tableId().equals(payload.tableId())) {
            sendView(player, false, true);
            return;
        }
        boolean changed = switch (payload.operation()) {
            case REQUEST_EXIT -> payload.token() == game.view(player.getUUID()).decision() && game.requestExit(player.getUUID());
            case ANSWER_EXIT -> game.answerExit(player.getUUID(), payload.token(), payload.enabled());
            case RESOLVE_LEAVE -> payload.token() == game.view(null).decision()
                && game.resolveLeave(player.getUUID(), payload.enabled());
            case AUTO_SORT -> game.configureAutoPlay(player.getUUID(), payload.token(), top.skyeyefast.mchjong.engine.AutoPlay.Option.SORT, payload.enabled());
            case AUTO_WIN -> game.configureAutoPlay(player.getUUID(), payload.token(), top.skyeyefast.mchjong.engine.AutoPlay.Option.WIN, payload.enabled());
            case NO_CALLS -> game.configureAutoPlay(player.getUUID(), payload.token(), top.skyeyefast.mchjong.engine.AutoPlay.Option.NO_CALLS, payload.enabled());
            case AUTO_DISCARD -> game.configureAutoPlay(player.getUUID(), payload.token(), top.skyeyefast.mchjong.engine.AutoPlay.Option.DISCARD, payload.enabled());
            case AUTO_KITA -> game.configureAutoPlay(player.getUUID(), payload.token(), top.skyeyefast.mchjong.engine.AutoPlay.Option.KITA, payload.enabled());
            case CONVENIENCE_HINTS -> game.configureConvenienceHints(player.getUUID(), payload.token(), payload.enabled());
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
        tag.putString("game_type", gameType.name());
        if (unreadableSave != null) tag.putString("game", unreadableSave);
        else if (game != null) tag.putString("game", TableNetworking.JSON.toJson(game));
        if (unreadableMcrSave != null) tag.putString("mcr_session", unreadableMcrSave);
        else if (mcrHost != null) tag.putString("mcr_session", mcrHost.save());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        equipment.load(tag, registries);
        if (tag.contains("boxes")) {
            game = null;
            unreadableSave = null;
            sentRevision = -1;
            mcrHost = null;
            unreadableMcrSave = null;
            sentMcrRevision = -1;
            gameType = tag.contains("game_type") ? GameType.valueOf(tag.getString("game_type")) : GameType.RIICHI;
        }
        if (tag.contains("game")) {
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
        if (tag.contains("mcr_session")) {
            String saved = tag.getString("mcr_session");
            try {
                var restored = McrTableHost.restore(saved);
                if (game == null || gameType != GameType.MCR || !restored.tableId().equals(game.tableId()))
                    throw new IllegalArgumentException("MCR session does not match the table lobby");
                mcrHost = restored;
                unreadableMcrSave = null;
            } catch (RuntimeException error) {
                unreadableMcrSave = saved;
                mcrHost = null;
                LOGGER.error("Cannot load MCR session at {}. Original save retained.", worldPosition, error);
            }
        }
    }

    @Override protected void writeAppearance(CompoundTag tag) {
        super.writeAppearance(tag);
        equipment.writeAppearance(tag);
    }

}
