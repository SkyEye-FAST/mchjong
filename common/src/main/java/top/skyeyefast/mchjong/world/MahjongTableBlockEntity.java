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
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.network.TableActionPayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.TableViewPayload;

public final class MahjongTableBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger("mchjong");
    private static final SecureRandom SEEDS = new SecureRandom();
    private Game game;
    private String unreadableSave;
    private int ticks;
    private long sentRevision = -1;
    private TableView clientView;

    public MahjongTableBlockEntity(BlockPos pos, BlockState state) { super(MahjongContent.TABLE_ENTITY, pos, state); }

    private Game serverGame() {
        if (level == null || level.isClientSide) throw new IllegalStateException("Private state accessed outside server");
        if (unreadableSave != null) return null;
        if (game == null) game = new Game(UUID.randomUUID(), RuleSet.MAHJONG_SOUL_4, SEEDS.nextLong());
        return game;
    }

    public TableView clientView() { return clientView; }
    public void acceptView(TableView view) {
        if (level == null || !level.isClientSide) throw new IllegalStateException("Client snapshot on server");
        clientView = view;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MahjongTableBlockEntity table) {
        Game game = table.serverGame();
        if (game == null) return;
        game.tick();
        table.ticks++;
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

    public void sit(ServerPlayer player, int seat) {
        Game game = serverGame();
        if (game == null) { open(player); return; }
        if (seat < 0 || seat >= game.rules().players()) {
            player.displayClientMessage(Component.translatable("message.mchjong.inactive_seat"), true);
            return;
        }
        if (player.isSpectator() || player.isPassenger()) return;
        BlockPos stool = TableGeometry.stool(worldPosition, seat);
        if (!level.getBlockState(stool).is(MahjongContent.STOOL)) return;
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
        TableView before = game.view(player.getUUID());
        if (before.decision() != payload.decision() || payload.action() < 0 || payload.action() >= before.actions().size()) {
            sendView(player, false);
            return;
        }
        Action action = before.actions().get(payload.action());
        if (game.act(player.getUUID(), payload.decision(), payload.action())) {
            if (action.type() == Action.Type.LEAVE) player.stopRiding();
            setChanged();
        }
        sendView(player, false);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (unreadableSave != null) tag.putString("game", unreadableSave);
        else if (game != null) tag.putString("game", TableNetworking.JSON.toJson(game));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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

    /** Chunk synchronization deliberately excludes the server's save tag. */
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return new CompoundTag(); }
}
