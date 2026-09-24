package top.skyeyefast.mchjong.compat.rei;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExample;

final class SupplyReiDisplay extends BasicDisplay {
    static final CategoryIdentifier<SupplyReiDisplay> CATEGORY = CategoryIdentifier.of("mchjong", "supplies");
    static final DisplaySerializer<SupplyReiDisplay> SERIALIZER = DisplaySerializer.of(
        RecordCodecBuilder.mapCodec(instance -> instance.group(
            EntryIngredient.codec().listOf().fieldOf("inputs").forGetter(SupplyReiDisplay::getInputEntries),
            EntryIngredient.codec().listOf().fieldOf("outputs").forGetter(SupplyReiDisplay::getOutputEntries),
            Identifier.CODEC.optionalFieldOf("location").forGetter(SupplyReiDisplay::getDisplayLocation),
            Codec.BOOL.fieldOf("shapeless").forGetter(SupplyReiDisplay::shapeless)
        ).apply(instance, SupplyReiDisplay::new)),
        StreamCodec.composite(
            EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), SupplyReiDisplay::getInputEntries,
            EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), SupplyReiDisplay::getOutputEntries,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), SupplyReiDisplay::getDisplayLocation,
            ByteBufCodecs.BOOL, SupplyReiDisplay::shapeless,
            SupplyReiDisplay::new));
    private final boolean shapeless;

    SupplyReiDisplay(SupplyRecipeExample example) {
        this(IntStream.range(0, example.input().size())
            .mapToObj(index -> EntryIngredients.ofItemStacks(example.ingredients(index))).toList(),
            List.of(EntryIngredients.of(example.output())), Optional.of(example.id()), example.shapeless());
    }

    private SupplyReiDisplay(List<EntryIngredient> inputs, List<EntryIngredient> outputs,
            Optional<Identifier> location, boolean shapeless) {
        super(inputs, outputs, location);
        this.shapeless = shapeless;
    }

    @Override public CategoryIdentifier<?> getCategoryIdentifier() { return CATEGORY; }
    @Override public DisplaySerializer<? extends SupplyReiDisplay> getSerializer() { return SERIALIZER; }
    boolean shapeless() { return shapeless; }
}
