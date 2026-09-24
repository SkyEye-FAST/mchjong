package top.skyeyefast.mchjong.data;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

/** Build-only structure generation using Minecraft's native NBT format. */
public final class GeneratePonderStructure {
    private GeneratePonderStructure() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("Expected the generated resource directory and optional create profile");
        Path path = Path.of(args[0], "assets", "mchjong", "ponder", "table.nbt");
        Files.createDirectories(path.getParent());
        CompoundTag structure = new CompoundTag();
        structure.put("size", position(7, 3, 7));
        ListTag palette = new ListTag();
        for (String name : new String[] {"minecraft:smooth_stone", "mchjong:mahjong_table", "mchjong:mahjong_stool"}) {
            CompoundTag state = new CompoundTag();
            state.putString("Name", name);
            palette.add(state);
        }
        structure.put("palette", palette);
        ListTag blocks = new ListTag();
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) blocks.add(block(x, 0, z, 0));
        blocks.add(block(3, 1, 3, 1));
        blocks.add(block(3, 1, 5, 2));
        blocks.add(block(1, 1, 3, 2));
        blocks.add(block(3, 1, 1, 2));
        blocks.add(block(5, 1, 3, 2));
        structure.put("blocks", blocks);
        structure.put("entities", new ListTag());
        NbtIo.writeCompressed(structure, path.toFile());
        if (args.length == 2 && args[1].equals("create")) {
            var workshop = structure.copy();
            workshop.put("size", position(7, 5, 7));
            var air = new CompoundTag();
            air.putString("Name", "minecraft:air");
            var workshopPalette = workshop.getList("palette", 10);
            workshopPalette.add(air);
            workshop.getList("blocks", 10).add(block(6, 4, 6, workshopPalette.size() - 1));
            NbtIo.writeCompressed(workshop, path.resolveSibling("workshop.nbt").toFile());
        }
    }

    private static CompoundTag block(int x, int y, int z, int state) {
        CompoundTag block = new CompoundTag();
        block.put("pos", position(x, y, z));
        block.putInt("state", state);
        if (state == 1 || state == 2) {
            CompoundTag entity = new CompoundTag();
            entity.putString("id", state == 1 ? "mchjong:mahjong_table" : "mchjong:mahjong_stool");
            entity.putString("wood", "oak");
            block.put("nbt", entity);
        }
        return block;
    }

    private static ListTag position(int x, int y, int z) {
        ListTag values = new ListTag();
        values.add(IntTag.valueOf(x));
        values.add(IntTag.valueOf(y));
        values.add(IntTag.valueOf(z));
        return values;
    }
}
