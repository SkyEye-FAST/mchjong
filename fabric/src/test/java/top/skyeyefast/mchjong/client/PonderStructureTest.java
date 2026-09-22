package top.skyeyefast.mchjong.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

class PonderStructureTest {
    @Test void generatedStructureContainsTheCompactTableAndFourSeats() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/mchjong/ponder/table.nbt")) {
            assertNotNull(input, "Ponder structure must be packaged on both loaders");
            CompoundTag structure = NbtIo.readCompressed(input, NbtAccounter.create(65536));
            var size = structure.getList("size", 3);
            assertEquals(7, size.getInt(0));
            assertEquals(3, size.getInt(1));
            assertEquals(7, size.getInt(2));
            var palette = structure.getList("palette", 10);
            assertEquals("mchjong:mahjong_table", palette.getCompound(1).getString("Name"));
            assertEquals("mchjong:mahjong_stool", palette.getCompound(2).getString("Name"));
            var occupied = new HashSet<String>();
            var seats = new HashSet<String>();
            int tables = 0;
            var blocks = structure.getList("blocks", 10);
            assertEquals(54, blocks.size());
            for (var value : blocks) {
                CompoundTag block = (CompoundTag) value;
                var pos = block.getList("pos", 3);
                String coordinates = pos.getInt(0) + "," + pos.getInt(1) + "," + pos.getInt(2);
                assertTrue(occupied.add(coordinates), "Duplicate block: " + coordinates);
                int state = block.getInt("state");
                if (state == 1) { assertEquals("3,1,3", coordinates); tables++; }
                if (state == 2) seats.add(coordinates);
                assertFalse(block.getCompound("nbt").contains("game"), "Structure must only contain display furniture");
            }
            assertEquals(1, tables);
            assertEquals(java.util.Set.of("1,1,3", "5,1,3", "3,1,1", "3,1,5"), seats);
        }
    }
}
