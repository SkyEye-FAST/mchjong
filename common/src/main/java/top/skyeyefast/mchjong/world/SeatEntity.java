package top.skyeyefast.mchjong.world;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A real vehicle gives players Minecraft's seated pose, without moving the world camera to a GUI. */
public final class SeatEntity extends Entity {
    private static final EntityDataAccessor<BlockPos> TABLE = SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> SEAT = SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.INT);
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
        BlockPos stool = TableGeometry.stool(table, seat);
        setPos(stool.getX() + 0.5, stool.getY() + TableGeometry.STOOL_HEIGHT, stool.getZ() + 0.5);
        setYRot(TableGeometry.yaw(seat));
    }

    public BlockPos tablePos() { return entityData.get(TABLE); }
    public int seat() { return entityData.get(SEAT); }
    @Override protected void defineSynchedData() {
        entityData.define(TABLE, BlockPos.ZERO); entityData.define(SEAT, 0);
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
        if (level().isClientSide) return;
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
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(TABLE, BlockPos.of(tag.getLong("table")));
        entityData.set(SEAT, net.minecraft.util.Mth.clamp(tag.getInt("seat"), 0, 3));
        rider = tag.hasUUID("rider") ? tag.getUUID("rider") : null;
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("table", tablePos().asLong()); tag.putInt("seat", seat());
        if (rider != null) tag.putUUID("rider", rider);
    }
}
