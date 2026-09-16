package top.skyeyefast.mchjong.neo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.TableGeometry;
import top.skyeyefast.mchjong.world.TableSpaceBlock;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class TableGeometryTest {
    @Test void allTwentyFiveCellsResolveTheirCenterAndClipCollisionToTheTabletop(MinecraftServer server) {
        var block = MahjongContent.SPACE;
        var center = new BlockPos(12, 64, -8);
        double half = TableGeometry.OUTER_HALF_WIDTH;
        var table = new AABB(.5 - half, .75, .5 - half, .5 + half, 1, .5 + half);
        int radius = TableGeometry.FOOTPRINT_RADIUS;
        assertEquals(2, radius);
        double area = 0;
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            var state = block.defaultBlockState().setValue(TableSpaceBlock.X, x + radius).setValue(TableSpaceBlock.Z, z + radius);
            assertEquals(center, TableSpaceBlock.center(center.offset(x, 0, z), state));
            var shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
            var actual = shape.bounds().move(x, 0, z);
            assertEquals(table.intersect(new AABB(x, 0, z, x + 1, 1, z + 1)), actual);
            area += actual.getXsize() * actual.getZsize();
        }
        assertEquals(half * half * 4, area, 1e-8);
    }

    @Test void seatsAndDismountSpaceRemainOutsideTheExpandedFootprint() {
        assertEquals(3, TableGeometry.STOOL_DISTANCE);
        for (int side = 0; side < 4; side++) {
            var stool = TableGeometry.stool(BlockPos.ZERO, side);
            assertEquals(BlockPos.ZERO.relative(TableGeometry.SIDES[side], 3), stool);
            assertTrue(TableGeometry.STOOL_DISTANCE - .4 > TableGeometry.OUTER_HALF_WIDTH);
        }
    }
}
