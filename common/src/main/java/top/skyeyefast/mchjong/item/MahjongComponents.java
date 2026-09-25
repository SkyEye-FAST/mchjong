package top.skyeyefast.mchjong.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import top.skyeyefast.mchjong.engine.RedFives;

/** Shared values, registered by the loaders before recipes and item stacks are decoded. */
public final class MahjongComponents {
    public static final Set<Integer> DENOMINATIONS = Set.of(-10000, 0, 100, 1000, 5000, 10000);
    public static final DataComponentType<FurnitureWood> WOOD = type(FurnitureWood.CODEC);
    public static final DataComponentType<TileData> TILE = type(TileData.CODEC);
    public static final DataComponentType<TileFacePreset> FACE_PRESET = type(TileFacePreset.CODEC);
    public static final DataComponentType<net.minecraft.resources.ResourceLocation> BACK_PRESET = type(net.minecraft.resources.ResourceLocation.CODEC);
    public static final DataComponentType<RedFives> BOX_PRESET = type(Codec.STRING.comapFlatMap(value -> {
        for (var candidate : RedFives.values())
            if (candidate.name().equalsIgnoreCase(value)) return DataResult.success(candidate);
        return DataResult.error(() -> "Invalid red-five box preset");
    }, value -> value.name().toLowerCase(Locale.ROOT)));
    public static final DataComponentType<Integer> POINTS = type(Codec.INT.validate(value -> DENOMINATIONS.contains(value)
        ? DataResult.success(value) : DataResult.error(() -> "Invalid point-stick denomination")));
    public static final Map<String, DataComponentType<?>> TYPES = Map.of(
        "wood", WOOD, "tile", TILE, "points", POINTS, "face_preset", FACE_PRESET,
        "back_preset", BACK_PRESET, "box_preset", BOX_PRESET);

    private MahjongComponents() {}
    // Minecraft derives a registry-aware stream codec from the persistent codec.
    private static <T> DataComponentType<T> type(Codec<T> codec) {
        return DataComponentType.<T>builder().persistent(codec).build();
    }
}
