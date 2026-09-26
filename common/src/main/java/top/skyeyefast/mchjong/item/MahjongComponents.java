package top.skyeyefast.mchjong.item;

import com.mojang.serialization.Codec;
import java.util.Locale;
import java.util.Set;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.engine.RedFives;

/** Native 1.20.1 stack data, stored under the mod's own NBT compound. */
public final class MahjongComponents {
    public static final Set<Integer> DENOMINATIONS = Set.of(-10000, 0, 100, 1000, 5000, 10000);

    private MahjongComponents() {}
    public static FurnitureWood wood(ItemStack stack) { return read(stack, "wood", FurnitureWood.CODEC, FurnitureWood.OAK); }
    public static void wood(ItemStack stack, FurnitureWood wood) { write(stack, "wood", FurnitureWood.CODEC, wood); }
    public static TileData tile(ItemStack stack) {
        var tag = stack.getTagElement("mchjong");
        if (tag == null || !tag.contains("tile")) return TileData.BLANK;
        return read(stack, "tile", TileData.CODEC, new TileData(-2, TileMaterial.BONE, false));
    }
    public static void tile(ItemStack stack, TileData tile) { write(stack, "tile", TileData.CODEC, tile); }
    public static TileFacePreset facePreset(ItemStack stack) { return read(stack, "face_preset", TileFacePreset.CODEC, TileFacePreset.KANSAI); }
    public static void facePreset(ItemStack stack, TileFacePreset preset) { write(stack, "face_preset", TileFacePreset.CODEC, preset); }
    public static net.minecraft.resources.ResourceLocation backPreset(ItemStack stack) {
        return read(stack, "back_preset", net.minecraft.resources.ResourceLocation.CODEC, MahjongContent.id("default"));
    }
    public static void backPreset(ItemStack stack, net.minecraft.resources.ResourceLocation preset) {
        write(stack, "back_preset", net.minecraft.resources.ResourceLocation.CODEC, preset);
    }
    public static int points(ItemStack stack) {
        int value = read(stack, "points", Codec.INT, 0);
        return DENOMINATIONS.contains(value) ? value : 0;
    }
    public static void points(ItemStack stack, int points) {
        if (!DENOMINATIONS.contains(points)) throw new IllegalArgumentException("Invalid point-stick denomination");
        write(stack, "points", Codec.INT, points);
    }
    public static DyeColor color(ItemStack stack) {
        var tag = stack.getTagElement("mchjong");
        if (tag != null && tag.contains("color", 3)) return DyeColor.byId(tag.getInt("color"));
        if (stack.is(MahjongContent.CLOTH_ITEM)) return DyeColor.CYAN;
        if (stack.is(MahjongContent.STOOL_ITEM)) return DyeColor.WHITE;
        return null;
    }
    public static void color(ItemStack stack, DyeColor color) {
        if (color == null) {
            var tag = stack.getTagElement("mchjong");
            if (tag == null) return;
            tag.remove("color");
        } else stack.getOrCreateTagElement("mchjong").putInt("color", color.getId());
        normalize(stack.getItem(), stack.getTag());
        if (stack.getTag().isEmpty()) stack.setTag(null);
    }
    public static RedFives boxPreset(ItemStack stack) {
        String value = read(stack, "box_preset", Codec.STRING, "");
        for (var preset : RedFives.values()) if (preset.name().equalsIgnoreCase(value)) return preset;
        return null;
    }
    public static void boxPreset(ItemStack stack, RedFives preset) {
        write(stack, "box_preset", Codec.STRING, preset.name().toLowerCase(Locale.ROOT));
    }
    public static boolean valid(ItemStack stack) {
        var root = stack.getTag();
        if (root == null || !root.contains("mchjong")) return true;
        if (!root.contains("mchjong", 10)) return false;
        var data = root.getCompound("mchjong");
        if (data.contains("wood") && FurnitureWood.CODEC.parse(NbtOps.INSTANCE, data.get("wood")).result().isEmpty()) return false;
        if (data.contains("tile") && TileData.CODEC.parse(NbtOps.INSTANCE, data.get("tile")).result().isEmpty()) return false;
        if (data.contains("face_preset") && TileFacePreset.CODEC.parse(NbtOps.INSTANCE, data.get("face_preset")).result().isEmpty()) return false;
        if (data.contains("back_preset") && net.minecraft.resources.ResourceLocation.CODEC.parse(NbtOps.INSTANCE, data.get("back_preset")).result().isEmpty()) return false;
        if (data.contains("points") && (!data.contains("points", 3) || !DENOMINATIONS.contains(data.getInt("points")))) return false;
        if (data.contains("color") && (!data.contains("color", 3) || data.getInt("color") < 0 || data.getInt("color") > 15)) return false;
        return !data.contains("box_preset") || boxPreset(stack) != null;
    }
    private static <T> T read(ItemStack stack, String key, Codec<T> codec, T absent) {
        var tag = stack.getTagElement("mchjong");
        return tag == null || !tag.contains(key) ? absent : codec.parse(NbtOps.INSTANCE, tag.get(key)).result().orElse(absent);
    }
    /** Canonical defaults keep crafted, command-created and freshly allocated stacks mergeable. */
    public static void normalize(net.minecraft.world.item.Item item, net.minecraft.nbt.CompoundTag root) {
        if (root == null || !root.contains("mchjong", 10)) return;
        var data = root.getCompound("mchjong");
        if (data.getString("wood").equals("oak")) data.remove("wood");
        if (data.contains("points", 3) && data.getInt("points") == 0) data.remove("points");
        if (data.getString("face_preset").equals("mchjong:kansai")) data.remove("face_preset");
        if (data.getString("back_preset").equals("mchjong:default")) data.remove("back_preset");
        if (data.contains("tile") && TileData.CODEC.parse(NbtOps.INSTANCE, data.get("tile")).result().filter(TileData.BLANK::equals).isPresent())
            data.remove("tile");
        int defaultColor = item == MahjongContent.CLOTH_ITEM ? DyeColor.CYAN.getId()
            : item == MahjongContent.STOOL_ITEM ? DyeColor.WHITE.getId() : -1;
        if (data.contains("color", 3) && data.getInt("color") == defaultColor) data.remove("color");
        if (data.get("Items") instanceof net.minecraft.nbt.ListTag items && items.isEmpty()) data.remove("Items");
        if (data.isEmpty()) root.remove("mchjong");
    }
    private static <T> void write(ItemStack stack, String key, Codec<T> codec, T value) {
        var encoded = codec.encodeStart(NbtOps.INSTANCE, value).result()
            .orElseThrow(() -> new IllegalArgumentException("Invalid " + key));
        stack.getOrCreateTagElement("mchjong").put(key, encoded);
        normalize(stack.getItem(), stack.getTag());
        if (stack.getTag().isEmpty()) stack.setTag(null);
    }
}
