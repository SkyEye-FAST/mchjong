package top.skyeyefast.mchjong.smoke;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.compat.recipes.SupplySubtype;
import top.skyeyefast.mchjong.item.MahjongCatalog;

final class ReiBrowserSmoke implements BrowserDriver {
    @Override public boolean ready() {
        try {
            var display = DisplayRegistry.getInstance().getAll().values().stream().flatMap(List::stream)
                .flatMap(candidate -> candidate.getDisplayLocation().stream())
                .filter(id -> id.getNamespace().equals("mchjong") && id.getPath().startsWith("/supplies/"))
                .findFirst().orElse(null);
            if (display == null) return false;
            var registered = DisplayRegistry.getInstance().getAll().values().stream().flatMap(List::stream)
                .filter(candidate -> candidate.getDisplayLocation().filter(display::equals).isPresent())
                .findFirst().orElse(null);
            if (registered == null || registered.getOutputEntries().isEmpty() || registered.getOutputEntries().getFirst().isEmpty()) return false;
            if (ViewSearchBuilder.builder().addRecipesFor(registered.getOutputEntries().getFirst().getFirst()).streamDisplays()
                .flatMap(spec -> spec.provideInternalDisplayIds().stream()).noneMatch(display::equals)) return false;
            var entries = catalogue();
            return MahjongCatalog.entries().stream().allMatch(expected -> entries.stream()
                .anyMatch(actual -> SupplySubtype.of(actual).equals(SupplySubtype.of(expected))));
        } catch (java.util.ConcurrentModificationException ignored) {
            return false;
        }
    }

    @Override public List<ItemStack> catalogue() {
        return EntryRegistry.getInstance().getEntryStacks().map(entry -> entry.getValue())
            .filter(ItemStack.class::isInstance).map(ItemStack.class::cast).toList();
    }

    @Override public Set<ResourceLocation> query(ItemStack stack, boolean output) {
        var target = EntryStacks.of(stack);
        var search = ViewSearchBuilder.builder();
        if (output) search.addRecipesFor(target);
        else search.addUsagesFor(target);
        return search.streamDisplays().flatMap(spec -> spec.provideInternalDisplayIds().stream())
            .collect(Collectors.toSet());
    }

    @Override public void showRecipe(ResourceLocation id) {
        var display = DisplayRegistry.getInstance().getAll().values().stream().flatMap(List::stream)
            .filter(candidate -> candidate.getDisplayLocation().filter(id::equals).isPresent())
            .findFirst().orElseThrow(() -> new IllegalStateException("REI did not register " + id));
        var output = display.getOutputEntries().getFirst().getFirst();
        if (!ViewSearchBuilder.builder().addRecipesFor(output).filterCategory(display.getCategoryIdentifier()).open())
            throw new IllegalStateException("REI did not open " + id);
    }
}
