package top.skyeyefast.mchjong.world;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** A real vehicle gives players Minecraft's seated pose, without moving the world camera to a GUI. */
public final class SeatEntity extends Entity {
    private static final EntityDataAccessor<BlockPos> TABLE = SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> SEAT = SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> RIDER = SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.STRING);
    private UUID rider;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    public void initialize(BlockPos table, int seat, UUID player) {
        entityData.set(TABLE, table.immutable());
        entityData.set(SEAT, seat);
        rider = player;
        entityData.set(RIDER, player.toString());
        BlockPos stool = TableGeometry.stool(table, seat);
        setPos(stool.getX() + 0.5, stool.getY() + TableGeometry.STOOL_HEIGHT, stool.getZ() + 0.5);
        setYRot(TableGeometry.yaw(seat));
    }

    public BlockPos tablePos() { return entityData.get(TABLE); }
    public int seat() { return entityData.get(SEAT); }
    public String riderId() { return entityData.get(RIDER); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TABLE, BlockPos.ZERO); builder.define(SEAT, 0); builder.define(RIDER, "");
    }
    @Override protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty(); }
    @Override protected void positionRider(Entity passenger, MoveFunction position) {
        // The maid's riding pose sits lower than a player's on the same mount.
        double offset = passenger instanceof Player ? 0.65 : 0.15;
        position.accept(passenger, getX(), getY() - offset, getZ());
        if (passenger instanceof LivingEntity companion && !(companion instanceof Player)) {
            float yaw = TableGeometry.yaw(seat());
            companion.setYRot(yaw);
            companion.setYBodyRot(yaw);
            companion.setYHeadRot(yaw);
        }
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        BlockPos stool = TableGeometry.stool(tablePos(), seat());
        if (!isVehicle() || !level().getBlockState(stool).is(MahjongContent.STOOL)
            || !(level().getBlockEntity(tablePos()) instanceof MahjongTableBlockEntity)) {
            ejectPassengers();
            if (rider != null && level().getBlockEntity(tablePos()) instanceof MahjongTableBlockEntity table) table.stoodUp(rider);
            discard();
        }
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos outward = tablePos().relative(TableGeometry.SIDES[seat()], TableGeometry.STOOL_DISTANCE + 1);
        for (BlockPos pos : new BlockPos[]{outward, outward.above(), outward.relative(TableGeometry.SIDES[seat()].getClockWise()),
                outward.relative(TableGeometry.SIDES[seat()].getCounterClockWise())}) {
            Vec3 safe = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), pos, true);
            if (safe != null) return safe;
        }
        return new Vec3(getX(), getY() + 0.5, getZ());
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(TABLE, BlockPos.of(input.getLongOr("table", 0)));
        entityData.set(SEAT, Math.clamp(input.getIntOr("seat", 0), 0, 3));
        rider = input.getString("rider").map(UUID::fromString).orElse(null);
        entityData.set(RIDER, rider == null ? "" : rider.toString());
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        output.putLong("table", tablePos().asLong());
        output.putInt("seat", seat());
        if (rider != null) output.putString("rider", rider.toString());
    }
}
