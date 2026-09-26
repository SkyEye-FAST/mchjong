package top.skyeyefast.mchjong.neo;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.world.MahjongContent;
import static org.junit.jupiter.api.Assertions.*;

/** These tests decode the generated datapacks and exercise actual registered Minecraft recipes. */
@ExtendWith(EphemeralTestServerProvider.class)
class PhysicalSuppliesTest {
    @Test void catalogueKeepsBoxesAdjacentAndListsEveryStickWithoutSeparateTileFaces(MinecraftServer server) {
        var entries = top.skyeyefast.mchjong.item.MahjongCatalog.entries();
        int stools = 2 * FurnitureWood.values().length;
        int cloths = stools + 16, boxes = cloths + 16, tiles = boxes + 4;
        assertEquals(tiles + TileMaterial.values().length + 11, entries.size());
        assertEquals(16, entries.stream().filter(stack -> stack.is(MahjongContent.STOOL_ITEM)).count());
        assertEquals(16, entries.stream().filter(stack -> stack.is(MahjongContent.CLOTH_ITEM)).count());
        for (int i = stools; i < cloths; i++) assertTrue(entries.get(i).is(MahjongContent.STOOL_ITEM));
        assertEquals(List.of(DyeColor.values()), entries.subList(stools, cloths).stream().map(MahjongSupplies::color).toList());
        for (int i = cloths; i < boxes; i++) assertTrue(entries.get(i).is(MahjongContent.CLOTH_ITEM));
        assertEquals(List.of(DyeColor.values()), entries.subList(cloths, boxes).stream().map(MahjongSupplies::color).toList());
        for (int i = boxes; i < tiles; i++) assertTrue(entries.get(i).is(MahjongContent.BOX_ITEM));
        assertEquals(2, MahjongSupplies.contents(entries.get(boxes + 1)).get(MahjongSupplies.DICE_SLOT).getCount());
        assertTrue(entries.get(tiles + TileMaterial.values().length).is(MahjongContent.DICE));
        assertTrue(MahjongSupplies.boxAccepts(MahjongSupplies.DICE_SLOT, new ItemStack(MahjongContent.DICE)));
        assertFalse(MahjongSupplies.boxAccepts(MahjongSupplies.DICE_SLOT, new ItemStack(MahjongContent.POINT_STICK)));
        assertEquals(64, new ItemStack(MahjongContent.MAHJONG_DYE).getMaxStackSize());
        assertEquals(1, new ItemStack(MahjongContent.CREATIVE_MAHJONG_DYE).getMaxStackSize());
        assertEquals(0, MahjongSupplies.tileCount(MahjongSupplies.contents(entries.get(boxes))));
        assertEquals(144, MahjongSupplies.tileCount(MahjongSupplies.contents(entries.get(boxes + 1))));
        assertNotNull(MahjongSupplies.deck(entries.get(boxes + 1)));
        assertNull(MahjongSupplies.deck(entries.get(boxes + 1)).back());
        assertTrue(MahjongSupplies.boxAccepts(MahjongSupplies.DYE_SLOT, new ItemStack(Items.CYAN_DYE)));
        assertTrue(MahjongSupplies.boxAccepts(MahjongSupplies.DYE_SLOT, new ItemStack(MahjongContent.UNDO_DYE)));
        assertEquals(List.of(-10000, 0, 100, 1000, 5000, 10000), entries.stream()
            .filter(stack -> stack.is(MahjongContent.POINT_STICK)).map(stack -> stack.get(MahjongComponents.POINTS)).toList());
        assertEquals(TileMaterial.values().length, entries.stream().filter(stack -> stack.is(MahjongContent.TILE_ITEM)).count());
        assertEquals(FurnitureWood.values().length, entries.stream().filter(stack -> stack.is(MahjongContent.TABLE_ITEM)).count());
        assertEquals(FurnitureWood.values().length, entries.stream().filter(stack -> stack.is(MahjongContent.AUTO_TABLE_ITEM)).count());
        for (var material : TileMaterial.values()) {
            var blank = entries.get(tiles + material.ordinal());
            assertTrue(blank.is(MahjongContent.TILE_ITEM));
            assertEquals(new TileData(-1, material, false), MahjongSupplies.tile(blank));
        }
        assertEquals(Component.translatable("block.mchjong.mahjong_table.oak"), entries.get(0).getHoverName());
        assertEquals(Component.translatable("block.mchjong.automatic_mahjong_table.oak"), entries.get(FurnitureWood.values().length).getHoverName());
        assertEquals(Component.translatable("block.mchjong.mahjong_stool.white"), entries.get(stools).getHoverName());
        assertEquals(Component.translatable("item.mchjong.table_cloth.white"), entries.get(cloths).getHoverName());
        for (int i = 0; i < entries.size(); i++) for (int j = i + 1; j < entries.size(); j++)
            assertFalse(ItemStack.isSameItemSameComponents(entries.get(i), entries.get(j)));
        entries.getFirst().shrink(1);
        assertEquals(1, top.skyeyefast.mchjong.item.MahjongCatalog.entries().getFirst().getCount());
    }

    @Test void creativeTabUsesHatsuTileIconAndListsAllCatalogueEntries() {
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(MahjongContent.id("mchjong")).orElseThrow().value();
        var icon = tab.getIconItem();
        assertTrue(icon.is(MahjongContent.TILE_ITEM));
        assertEquals(32, MahjongSupplies.tile(icon).face());
        assertEquals(TileMaterial.BONE, MahjongSupplies.tile(icon).material());
        assertFalse(MahjongSupplies.tile(icon).red());
    }

    private static Item vanilla(String name) {
        return BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.withDefaultNamespace(name)).orElseThrow().value();
    }
    private static ResourceKey<Recipe<?>> recipeKey(String id) {
        return ResourceKey.create(Registries.RECIPE, MahjongContent.id(id));
    }
    private static CraftingRecipe crafting(MinecraftServer server, String id) {
        return (CraftingRecipe) server.getRecipeManager().byKey(recipeKey(id)).orElseThrow().value();
    }
    private static ItemStack craft(MinecraftServer server, String id, int width, int height, List<ItemStack> items) {
        var input = CraftingInput.of(width, height, items);
        var recipe = crafting(server, id);
        assertTrue(recipe.matches(input, server.overworld()), id);
        return recipe.assemble(input);
    }
    private static ItemStack roundTrip(MinecraftServer server, ItemStack stack) {
        var ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
        return ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, stack).getOrThrow()).getOrThrow();
    }
    private static net.minecraft.world.level.storage.ValueInput input(MinecraftServer server, net.minecraft.nbt.CompoundTag tag) {
        return net.minecraft.world.level.storage.TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), tag);
    }
    private static net.minecraft.nbt.CompoundTag save(MinecraftServer server, top.skyeyefast.mchjong.world.TableEquipment equipment) {
        var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
        equipment.save(output);
        return output.buildResult();
    }
    private static ItemStack box(List<ItemStack> items) {
        var box = new ItemStack(MahjongContent.BOX_ITEM);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        return box;
    }

    @Test void backPresetBelongsToPhysicalTilesAndSurvivesDyeing(MinecraftServer server) {
        var box = MahjongSupplies.completeBox(TileMaterial.BONE);
        var id = net.minecraft.resources.Identifier.parse("smoke:pattern");
        var contents = MahjongSupplies.backedContents(MahjongSupplies.contents(box), id);
        assertFalse(contents.isEmpty());
        MahjongSupplies.setContents(box, contents);
        assertEquals(id, MahjongSupplies.deck(box).backPreset());
        var colored = MahjongSupplies.dyedContents(MahjongSupplies.contents(box), DyeColor.BLUE);
        assertEquals(id, MahjongSupplies.backPreset(colored.getFirst()));
        assertEquals(DyeColor.BLUE, MahjongSupplies.back(colored.getFirst()));
        var equipment = new top.skyeyefast.mchjong.world.TableEquipment(() -> {});
        equipment.boxes().setItem(0, box);
        assertEquals(id, equipment.backPreset());
        assertEquals(net.minecraft.resources.Identifier.parse("mchjong:default"),
            MahjongSupplies.backPreset(MahjongSupplies.backedContents(contents,
                net.minecraft.resources.Identifier.parse("mchjong:default")).getFirst()));
    }

    @Test void everyFurnitureWoodAndClothColorUsesOneItemRegistryEntry(MinecraftServer server) {
        for (FurnitureWood wood : FurnitureWood.values()) {
            String name = wood.getSerializedName(), suffix = wood == FurnitureWood.OAK ? "" : "_" + name;
            Item slab = vanilla(name + "_slab"), fence = vanilla(name + "_fence");
            List<ItemStack> ingredients = new ArrayList<>(List.of(new ItemStack(slab), new ItemStack(slab), new ItemStack(slab),
                new ItemStack(slab), ItemStack.EMPTY, new ItemStack(slab), new ItemStack(fence), ItemStack.EMPTY, new ItemStack(fence)));
            ItemStack table = craft(server, "mahjong_table" + suffix, 3, 3, ingredients);
            assertTrue(table.is(MahjongContent.TABLE_ITEM));
            assertEquals(wood, table.get(MahjongComponents.WOOD));
            ingredients.set(0, new ItemStack(vanilla(wood == FurnitureWood.OAK ? "birch_slab" : "oak_slab")));
            assertFalse(crafting(server, "mahjong_table" + suffix).matches(CraftingInput.of(3, 3, ingredients), server.overworld()));
            ItemStack stools = craft(server, "mahjong_stool" + suffix, 2, 3,
                List.of(new ItemStack(Items.WHITE_CARPET), new ItemStack(Items.WHITE_CARPET),
                    new ItemStack(slab), new ItemStack(slab), new ItemStack(fence), new ItemStack(fence)));
            assertTrue(stools.is(MahjongContent.STOOL_ITEM));
            assertEquals(2, stools.getCount());
            assertEquals(wood, stools.get(MahjongComponents.WOOD));
            List<ItemStack> upgrade = List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.REDSTONE), new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.REDSTONE), table, new ItemStack(Items.REDSTONE),
                new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.HOPPER), new ItemStack(Items.COPPER_INGOT));
            ItemStack automatic = craft(server, "upgrade_table", 3, 3, upgrade);
            assertTrue(automatic.is(MahjongContent.AUTO_TABLE_ITEM));
            assertEquals(wood, automatic.get(MahjongComponents.WOOD));
            assertTrue(table.is(MahjongContent.TABLE_ITEM), "Recipe previews must not mutate inputs");
        }
        for (DyeColor color : DyeColor.values()) {
            var carpet = new ItemStack(vanilla(color.getName() + "_carpet"));
            ItemStack cloth = craft(server, "table_cloth_" + color.getName(), 3, 1, List.of(carpet, carpet.copy(), carpet.copy()));
            assertTrue(cloth.is(MahjongContent.CLOTH_ITEM));
            assertEquals(color, cloth.get(DataComponents.BASE_COLOR));
        }
        MahjongComponents.TYPES.forEach((name, component) ->
            assertSame(component, BuiltInRegistries.DATA_COMPONENT_TYPE.get(MahjongContent.id(name)).orElseThrow().value()));
        MahjongContent.SUPPLIES.forEach((name, item) ->
            assertSame(item, BuiltInRegistries.ITEM.get(MahjongContent.id(name)).orElseThrow().value()));
    }

    @Test void stonecuttingMakesOnlyBlanksAndMahjongDyeUsesFiveColors(MinecraftServer server) {
        for (TileMaterial material : TileMaterial.values()) {
            var cutting = (StonecutterRecipe) server.getRecipeManager().byKey(recipeKey("blanks_" + material.getSerializedName())).orElseThrow().value();
            var input = new SingleRecipeInput(new ItemStack(vanilla(material.source())));
            assertTrue(cutting.matches(input, server.overworld()));
            ItemStack blanks = cutting.assemble(input);
            assertTrue(blanks.is(MahjongContent.TILE_ITEM));
            assertEquals(16, blanks.getCount());
            assertEquals(new TileData(-1, material, false), blanks.get(MahjongComponents.TILE));
            assertNull(blanks.get(DataComponents.BASE_COLOR));
        }
        var stickCutting = (StonecutterRecipe) server.getRecipeManager().byKey(recipeKey("blank_point_sticks")).orElseThrow().value();
        var boneInput = new SingleRecipeInput(new ItemStack(Items.BONE_BLOCK));
        assertTrue(stickCutting.matches(boneInput, server.overworld()));
        ItemStack sticks = stickCutting.assemble(boneInput);
        assertTrue(sticks.is(MahjongContent.POINT_STICK));
        assertEquals(24, sticks.getCount());
        assertEquals(0, sticks.getOrDefault(MahjongComponents.POINTS, 0));
        var dye = craft(server, "mahjong_dye", 3, 2, List.of(new ItemStack(Items.BLUE_DYE), new ItemStack(Items.BLACK_DYE),
            new ItemStack(Items.GREEN_DYE), new ItemStack(Items.RED_DYE), new ItemStack(Items.WHITE_DYE), ItemStack.EMPTY));
        assertTrue(dye.is(MahjongContent.MAHJONG_DYE));
        assertEquals(1, dye.getCount());
        assertFalse(crafting(server, "mahjong_dye").matches(CraftingInput.of(2, 2, List.of(
            new ItemStack(Items.BLUE_DYE), new ItemStack(Items.BLACK_DYE), new ItemStack(Items.GREEN_DYE), new ItemStack(Items.RED_DYE))), server.overworld()));
        var caseItem = craft(server, "mahjong_box", 3, 3, List.of(new ItemStack(Items.LEATHER), new ItemStack(Items.OAK_SLAB),
            new ItemStack(Items.LEATHER), new ItemStack(Items.BIRCH_SLAB), new ItemStack(Items.CHEST), new ItemStack(Items.SPRUCE_SLAB),
            ItemStack.EMPTY, new ItemStack(Items.IRON_NUGGET), ItemStack.EMPTY));
        assertTrue(MahjongSupplies.validBox(caseItem));
        assertTrue(server.getRecipeManager().byKey(recipeKey("engrave_set")).isEmpty());
        assertTrue(server.getRecipeManager().getRecipes().stream().noneMatch(recipe ->
            recipe.id().identifier().getPath().startsWith("engrave_tile_")));
    }

    @Test void redDyeCraftingPreservesComponentsAndSelectsRuleCompatibleSets(MinecraftServer server) {
        var dye = craft(server, "red_dora_dye", 1, 1, List.of(new ItemStack(Items.RED_DYE)));
        assertTrue(dye.is(MahjongContent.RED_DORA_DYE));
        assertEquals(4, dye.getCount());
        assertEquals(64, dye.getMaxStackSize());
        var undo = craft(server, "undo_dye", 1, 1, List.of(new ItemStack(Items.BLACK_DYE)));
        assertTrue(undo.is(MahjongContent.UNDO_DYE));
        assertEquals(4, undo.getCount());
        var emptyReds = MahjongSupplies.completeBox(TileMaterial.GLASS, DyeColor.CYAN);
        var contents = MahjongSupplies.contents(emptyReds);
        assertEquals(top.skyeyefast.mchjong.engine.RedFives.NONE, MahjongSupplies.deck(emptyReds).redFives());
        assertTrue(MahjongSupplies.engrave(emptyReds, TileFacePreset.KANSAI).isEmpty());
        var equipment = new top.skyeyefast.mchjong.world.TableEquipment(() -> {});
        equipment.boxes().setItem(0, emptyReds.copy());
        int converted = 0;
        for (int face : new int[]{4, 13, 22, 13}) {
            var ordinary = contents.stream().filter(stack -> !stack.isEmpty() && !MahjongSupplies.tile(stack).red()
                && MahjongSupplies.tile(stack).face() == face).findFirst().orElseThrow();
            ordinary.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Named five"));
            var before = ordinary.copy();
            var red = craft(server, "red_five", 2, 1, List.of(ordinary, dye));
            var expected = before.copyWithCount(1);
            expected.set(MahjongComponents.TILE, MahjongSupplies.tile(before).engraved(face, true));
            assertTrue(ItemStack.matches(expected, red));
            var recolored = MahjongSupplies.dye(red, DyeColor.PURPLE);
            assertTrue(MahjongSupplies.tile(recolored).red());
            assertEquals(DyeColor.PURPLE, MahjongSupplies.back(recolored));
            assertTrue(ItemStack.matches(before, ordinary), "Recipe previews cannot consume inputs");
            assertFalse(crafting(server, "red_five").matches(CraftingInput.of(2, 1, List.of(red, dye)), server.overworld()));
            assertTrue(ItemStack.matches(before.copyWithCount(1), craft(server, "undo_red_five", 2, 1, List.of(undo, red))));
            assertFalse(crafting(server, "undo_red_five").matches(CraftingInput.of(2, 1, List.of(ordinary, undo)), server.overworld()));
            ordinary.shrink(1);
            int redSlot = java.util.stream.IntStream.range(0, MahjongSupplies.TILE_SLOTS)
                .filter(i -> !contents.get(i).isEmpty() && ItemStack.isSameItemSameComponents(contents.get(i), red))
                .findFirst().orElseGet(() -> java.util.stream.IntStream.range(0, MahjongSupplies.TILE_SLOTS)
                    .filter(i -> contents.get(i).isEmpty()).findFirst().orElseThrow());
            if (contents.get(redSlot).isEmpty()) contents.set(redSlot, red); else contents.get(redSlot).grow(1);
            var updated = box(contents);
            converted++;
            var printed = MahjongSupplies.engrave(updated, TileFacePreset.KANTO);
            assertFalse(printed.isEmpty());
            assertEquals(converted, MahjongSupplies.contents(printed).stream()
                .filter(stack -> stack.is(MahjongContent.TILE_ITEM) && MahjongSupplies.tile(stack).red())
                .mapToInt(ItemStack::getCount).sum());
            if (converted < 3) {
                for (var reds : top.skyeyefast.mchjong.engine.RedFives.values()) {
                    assertNull(MahjongSupplies.deck(updated, false, reds));
                    assertNull(MahjongSupplies.deck(printed, false, reds));
                }
                continue;
            }
            var deck = MahjongSupplies.deck(updated);
            assertNotNull(deck);
            assertEquals(converted, deck.redFives().total());
            assertEquals(converted == 3, MahjongSupplies.canSupplyReds(updated, false, top.skyeyefast.mchjong.engine.RedFives.THREE));
            assertEquals(converted == 4, MahjongSupplies.canSupplyReds(updated, false, top.skyeyefast.mchjong.engine.RedFives.FOUR));
            assertEquals(converted, deck.tiles().stream().filter(top.skyeyefast.mchjong.engine.Tile::red).count());
            assertEquals(136, new java.util.HashSet<>(deck.tiles()).size());
            equipment.boxes().setItem(1, updated);
            equipment.selectRules(top.skyeyefast.mchjong.engine.RuleSet.M_LEAGUE.config());
            assertEquals(converted == 3 ? 1 : -1, equipment.activeBox());
            equipment.selectRules(top.skyeyefast.mchjong.engine.RuleSet.WRC.config());
            assertEquals(0, equipment.activeBox());
            if (converted == 4) {
                assertEquals(top.skyeyefast.mchjong.engine.RedFives.FOUR, MahjongSupplies.deck(printed).redFives());
                assertEquals(136, MahjongSupplies.tileCount(MahjongSupplies.contents(printed)));
                assertEquals(4, MahjongSupplies.deck(updated).redFives().total(), "Printing previews cannot modify their source");
            }
        }
        assertFalse(crafting(server, "red_five").matches(CraftingInput.of(2, 1,
            List.of(contents.getFirst(), dye)), server.overworld()));
    }

    @Test void eightFlowersRoundTripAndStayOutsideTheRiichiWall(MinecraftServer server) {
        for (TileMaterial material : TileMaterial.values()) {
            var original = MahjongSupplies.completeBox(material, DyeColor.BLUE);
            var contents = MahjongSupplies.contents(original);
            for (int flower = 0; flower < TileData.FLOWER_COUNT; flower++) {
                var data = new TileData(TileData.FIRST_FLOWER + flower, material, false);
                var stack = MahjongSupplies.tile(data, DyeColor.BLUE, 1);
                assertTrue(data.flower());
                assertEquals((flower + 1) + "q", data.notation());
                assertEquals(data.notation(), data.label(true, TileFacePreset.KANSAI).getString());
                assertEquals("flower.mchjong." + List.of("spring", "summer", "autumn", "winter", "plum", "orchid", "bamboo", "chrysanthemum").get(flower), data.flowerKey(TileFacePreset.KANSAI));
                assertEquals("flower.mchjong." + List.of("spring", "summer", "autumn", "winter", "fortune", "prosperity", "longevity", "nobility").get(flower), data.flowerKey(TileFacePreset.KANTO));
                assertEquals(data, TileData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    TileData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, data).getOrThrow()).getOrThrow());
                contents.set(37 + flower, stack);
            }
            original.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
            assertEquals(144, MahjongSupplies.tileCount(MahjongSupplies.contents(original)));
            var deck = MahjongSupplies.deck(original);
            assertNotNull(deck);
            assertEquals(136, deck.tiles().size());
            assertEquals(108, MahjongSupplies.deck(original, true, deck.redFives()).tiles().size());
            assertTrue(top.skyeyefast.mchjong.engine.Tile.validSet(deck.tiles()));
            var loaded = roundTrip(server, original);
            assertTrue(ItemStack.matches(original, loaded));
            var recolored = MahjongSupplies.dye(loaded, DyeColor.CYAN);
            var flowers = MahjongSupplies.contents(recolored).stream()
                .filter(stack -> stack.is(MahjongContent.TILE_ITEM) && MahjongSupplies.tile(stack).flower()).toList();
            assertEquals(8, flowers.size());
            for (var flower : flowers) {
                assertEquals(1, flower.getCount());
                assertEquals(material, MahjongSupplies.tile(flower).material());
                assertEquals(DyeColor.CYAN, MahjongSupplies.color(flower));
            }
            assertNotNull(MahjongSupplies.deck(recolored));
            assertEquals(DyeColor.BLUE, MahjongSupplies.color(MahjongSupplies.contents(original).get(37)));
        }
        assertFalse(new TileData(42, TileMaterial.BONE, false).valid());
        assertFalse(new TileData(34, TileMaterial.BONE, true).valid());
    }

    @Test void noRedCompositionRequiresFourOrdinaryFivesInEveryUsedSuit() {
        var reds = top.skyeyefast.mchjong.engine.RedFives.NONE;
        assertFalse(MahjongSupplies.canSupplyReds(ItemStack.EMPTY, false, reds));
        for (int suit = 0; suit < 3; suit++) {
            var original = MahjongSupplies.completeBox(TileMaterial.BONE, DyeColor.BLUE);
            assertTrue(MahjongSupplies.canSupplyReds(original, false, reds));
            var items = MahjongSupplies.contents(original);
            items.get(suit * 9 + 4).setCount(3);
            items.set(34 + suit, MahjongSupplies.tile(new TileData(suit * 9 + 4, TileMaterial.BONE, true), DyeColor.BLUE, 1));
            var incomplete = box(items);
            assertFalse(MahjongSupplies.canSupplyReds(incomplete, false, reds));
            assertEquals(suit == 0, MahjongSupplies.canSupplyReds(incomplete, true, reds));
            var equipment = new top.skyeyefast.mchjong.world.TableEquipment(() -> {});
            equipment.boxes().setItem(0, incomplete);
            assertEquals(0, equipment.redOptions() & 1);
        }
    }

    @Test void surplusStockCoversOnlyTheSelectedModeAndRedComposition() {
        var original = MahjongSupplies.completeBox(TileMaterial.BONE, DyeColor.BLUE);
        var items = MahjongSupplies.contents(original);
        items.getFirst().grow(2);
        for (int suit = 0; suit < 3; suit++)
            items.set(34 + suit, MahjongSupplies.tile(new TileData(suit * 9 + 4, TileMaterial.BONE, true), DyeColor.BLUE, suit == 1 ? 2 : 1));
        var surplus = box(items);
        var before = surplus.copy();
        for (boolean sanma : new boolean[]{false, true}) for (var reds : top.skyeyefast.mchjong.engine.RedFives.values()) {
            assertTrue(MahjongSupplies.canSupplyReds(surplus, sanma, reds), "Surplus stock must enable every supported red composition");
            var selected = MahjongSupplies.deck(surplus, sanma, reds);
            assertNotNull(selected);
            assertEquals(sanma ? 108 : 136, selected.tiles().size());
            assertEquals(reds.total() - (sanma ? reds.count(0) : 0), selected.tiles().stream().filter(top.skyeyefast.mchjong.engine.Tile::red).count());
            assertTrue(top.skyeyefast.mchjong.engine.Tile.validSet(selected.tiles()));
        }
        assertTrue(ItemStack.matches(before, surplus), "Selecting a subset must leave all spare tiles untouched");
        items.get(13).setCount(2);
        assertFalse(MahjongSupplies.canSupplyReds(box(items), false, top.skyeyefast.mchjong.engine.RedFives.NONE));
        assertFalse(MahjongSupplies.canSupplyReds(box(items), false, top.skyeyefast.mchjong.engine.RedFives.THREE));
        assertTrue(MahjongSupplies.canSupplyReds(box(items), false, top.skyeyefast.mchjong.engine.RedFives.FOUR));
        assertNull(MahjongSupplies.deck(box(items), false, top.skyeyefast.mchjong.engine.RedFives.THREE));
        assertNotNull(MahjongSupplies.deck(box(items), false, top.skyeyefast.mchjong.engine.RedFives.FOUR));
        for (int face = 1; face <= 7; face++) items.set(face, ItemStack.EMPTY);
        items.set(34, ItemStack.EMPTY);
        var sanmaBox = box(items);
        assertEquals(108, MahjongSupplies.deck(sanmaBox, true, top.skyeyefast.mchjong.engine.RedFives.FOUR).tiles().size());
        assertNull(MahjongSupplies.deck(sanmaBox, false, top.skyeyefast.mchjong.engine.RedFives.FOUR));
        assertFalse(MahjongSupplies.canSupplyReds(sanmaBox, false, top.skyeyefast.mchjong.engine.RedFives.THREE));
        assertTrue(MahjongSupplies.canSupplyReds(sanmaBox, true, top.skyeyefast.mchjong.engine.RedFives.FOUR));
        var equipment = new top.skyeyefast.mchjong.world.TableEquipment(() -> {});
        equipment.boxes().setItem(0, sanmaBox);
        equipment.boxes().setItem(1, box(List.of(MahjongSupplies.tile(new TileData(4, TileMaterial.BONE, true), DyeColor.BLUE, 1))));
        assertFalse(equipment.canSupplyReds(false, top.skyeyefast.mchjong.engine.RedFives.THREE), "Separate boxes cannot pool red suits");
    }

    @Test void customPresetIdentitySurvivesPrintingAndPersistence(MinecraftServer server) {
        var preset = new TileFacePreset(net.minecraft.resources.Identifier.parse("example:ink/night"));
        var source = MahjongSupplies.completeBox(TileMaterial.BONE);
        var printed = MahjongSupplies.engrave(source, preset);
        assertEquals(preset, MahjongSupplies.deck(printed).preset());
        var restored = roundTrip(server, printed);
        assertTrue(ItemStack.matches(printed, restored));
        assertTrue(MahjongSupplies.engrave(restored, new TileFacePreset(preset.id())).isEmpty());
        var equipment = new top.skyeyefast.mchjong.world.TableEquipment(() -> {});
        equipment.boxes().setItem(0, restored);
        var tag = new net.minecraft.nbt.CompoundTag();
        equipment.writeAppearance(tag);
        var copy = new top.skyeyefast.mchjong.world.TableEquipment(() -> {});
        copy.load(input(server, tag));
        assertEquals(preset, copy.preset());
    }

    @Test void engravingAndBulkDyeAreAtomicAndKeepFlowersAndPhysicalSticks(MinecraftServer server) {
        for (TileMaterial material : TileMaterial.values()) {
            var blank = new TileData(-1, material, false);
            var stick = new ItemStack(MahjongContent.POINT_STICK, 12);
            stick.set(MahjongComponents.POINTS, 1000);
            var items = net.minecraft.core.NonNullList.withSize(MahjongSupplies.BOX_SLOTS, ItemStack.EMPTY);
            items.set(0, MahjongSupplies.tile(blank, DyeColor.BLUE, 64));
            items.set(1, MahjongSupplies.tile(blank, DyeColor.BLUE, 64));
            items.set(2, MahjongSupplies.tile(blank, DyeColor.BLUE, 16));
            items.set(MahjongSupplies.TILE_SLOTS, stick);
            ItemStack source = box(items);
            ItemStack before = source.copy();
            ItemStack engraved = MahjongSupplies.engrave(source, TileFacePreset.KANSAI);
            assertTrue(ItemStack.matches(before, source));
            assertNotNull(MahjongSupplies.deck(engraved));
            assertEquals(144, MahjongSupplies.tileCount(MahjongSupplies.contents(engraved)));
            assertEquals(8, MahjongSupplies.contents(engraved).stream().filter(s -> s.is(MahjongContent.TILE_ITEM)
                && MahjongSupplies.tile(s).flower()).mapToInt(ItemStack::getCount).sum());
            assertEquals(12, MahjongSupplies.contents(engraved).stream().filter(s -> s.is(MahjongContent.POINT_STICK)).mapToInt(ItemStack::getCount).sum());
            assertTrue(MahjongSupplies.engrave(engraved, TileFacePreset.KANSAI).isEmpty(), "An unchanged preset is not a paid operation");
            var kanto = MahjongSupplies.engrave(engraved, TileFacePreset.KANTO);
            assertEquals(TileFacePreset.KANTO, MahjongSupplies.deck(kanto).preset());
            assertTrue(ItemStack.matches(kanto, MahjongSupplies.engrave(source, TileFacePreset.KANTO)));
            var otherPreset = MahjongSupplies.contents(kanto);
            assertTrue(ItemStack.matches(engraved, MahjongSupplies.engrave(box(otherPreset), TileFacePreset.KANSAI)));
            otherPreset.getFirst().set(MahjongComponents.FACE_PRESET, TileFacePreset.KANSAI);
            assertNull(MahjongSupplies.deck(box(otherPreset)), "Mixed face presets do not form a uniform set");
            assertTrue(ItemStack.matches(engraved, MahjongSupplies.engrave(box(otherPreset), TileFacePreset.KANSAI)));
            for (DyeColor color : DyeColor.values()) {
                ItemStack dyed = craft(server, "dye", 2, 1, List.of(engraved, new ItemStack(vanilla(color.getName() + "_dye"))));
                var deck = MahjongSupplies.deck(dyed);
                assertNotNull(deck);
                assertEquals(material, deck.material());
                assertEquals(color, deck.back());
                assertEquals(136, deck.tiles().size());
                assertEquals(108, MahjongSupplies.deck(dyed, true, deck.redFives()).tiles().size());
                assertEquals(144, MahjongSupplies.tileCount(MahjongSupplies.contents(dyed)));
                assertEquals(DyeColor.BLUE, MahjongSupplies.deck(engraved).back());
                assertEquals(0, MahjongSupplies.contents(dyed).stream().filter(s -> s.is(MahjongContent.TILE_ITEM)
                    && MahjongSupplies.tile(s).red()).mapToInt(ItemStack::getCount).sum());
            }
            ItemStack undyed = MahjongSupplies.dye(engraved, null);
            assertNull(MahjongSupplies.deck(undyed).back());
            assertEquals(144, MahjongSupplies.tileCount(MahjongSupplies.contents(undyed)));
        }
        var shortBox = box(List.of(MahjongSupplies.tile(TileData.BLANK, DyeColor.BLUE, 64)));
        assertTrue(MahjongSupplies.engrave(shortBox, TileFacePreset.KANSAI).isEmpty());
        var mixed = box(List.of(MahjongSupplies.tile(TileData.BLANK, DyeColor.BLUE, 64),
            MahjongSupplies.tile(TileData.BLANK, DyeColor.RED, 64), MahjongSupplies.tile(TileData.BLANK, DyeColor.BLUE, 8)));
        assertTrue(MahjongSupplies.engrave(mixed, TileFacePreset.KANSAI).isEmpty());
        assertFalse(MahjongSupplies.storable(new ItemStack(MahjongContent.BOX_ITEM)));
        var nested = new ItemStack(MahjongContent.TILE_ITEM);
        nested.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        assertFalse(MahjongSupplies.storable(nested));
    }

    @Test void industrialTransactionsPreserveComponentsAndRejectNoOpsOrOverflow() {
        var blank = MahjongSupplies.tile(new TileData(-1, TileMaterial.GLASS, false), DyeColor.CYAN, 64);
        blank.set(DataComponents.CUSTOM_NAME, Component.literal("Workshop tiles"));
        var empty = new ItemStack(MahjongContent.BOX_ITEM);
        empty.set(DataComponents.CUSTOM_NAME, Component.literal("Workshop box"));
        var blanks = List.of(blank, blank.copy(), blank.copyWithCount(16));
        var printed = MahjongSupplies.printBox(empty, blanks);
        assertTrue(MahjongSupplies.validBox(printed));
        assertEquals(144, MahjongSupplies.tileCount(MahjongSupplies.contents(printed)));
        assertEquals(empty.getHoverName(), printed.getHoverName());
        assertTrue(MahjongSupplies.contents(empty).stream().allMatch(ItemStack::isEmpty));
        assertEquals(64, blank.getCount());
        for (var tile : MahjongSupplies.contents(printed)) if (!tile.isEmpty()) {
            assertEquals(blank.getHoverName(), tile.getHoverName());
            assertEquals(TileMaterial.GLASS, MahjongSupplies.tile(tile).material());
            assertEquals(TileFacePreset.KANSAI, MahjongSupplies.facePreset(tile));
        }
        var stick = new ItemStack(MahjongContent.POINT_STICK, 16);
        stick.set(DataComponents.CUSTOM_NAME, Component.literal("Workshop sticks"));
        var marked = MahjongSupplies.markSticks(List.of(stick), new ItemStack(Items.BLUE_DYE), 16);
        assertEquals(1000, marked.get(MahjongComponents.POINTS));
        assertEquals(stick.getHoverName(), marked.getHoverName());
        var packed = MahjongSupplies.pack(printed, List.of(marked, new ItemStack(MahjongContent.DICE, 2)));
        var dyed = MahjongSupplies.dyeBatch(List.of(packed, blank), DyeColor.BLUE);
        assertEquals(2, dyed.size());
        assertEquals(64, dyed.getLast().getCount());
        assertTrue(ItemStack.matches(marked, MahjongSupplies.contents(dyed.getFirst()).get(MahjongSupplies.TILE_SLOTS)));
        assertEquals(2, MahjongSupplies.contents(dyed.getFirst()).get(MahjongSupplies.DICE_SLOT).getCount());
        assertTrue(MahjongSupplies.dyeBatch(dyed, DyeColor.BLUE).isEmpty());
        var undone = MahjongSupplies.dyeBatch(dyed, null);
        assertEquals(2, undone.size());
        assertNull(MahjongSupplies.back(undone.getLast()));
        assertTrue(MahjongSupplies.dyeBatch(undone, null).isEmpty());
        var compact = MahjongSupplies.stockedBox(top.skyeyefast.mchjong.engine.RedFives.NONE);
        assertTrue(MahjongSupplies.dyeBatch(List.of(compact, compact.copy()), null).isEmpty());
        assertTrue(compact.has(MahjongComponents.BOX_PRESET));
        assertEquals(DyeColor.CYAN, MahjongSupplies.back(blank));
        assertTrue(MahjongSupplies.pack(packed, List.of(new ItemStack(Items.STONE))).isEmpty());
        assertTrue(MahjongSupplies.pack(packed, List.of(new ItemStack(MahjongContent.DICE, 64))).isEmpty());
        var malformed = blank.copy();
        malformed.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        assertTrue(MahjongSupplies.pack(empty, List.of(malformed)).isEmpty());
        assertTrue(MahjongSupplies.printBox(empty, List.of(blank, blank.copy(), blank.copyWithCount(15))).isEmpty());
        var different = blank.copyWithCount(16);
        different.set(DataComponents.CUSTOM_NAME, Component.literal("Different"));
        assertTrue(MahjongSupplies.printBox(empty, List.of(blank, blank.copy(), different)).isEmpty());
        var custom = MahjongSupplies.engrave(printed, TileFacePreset.KANTO);
        assertTrue(MahjongSupplies.printBox(custom, List.of()).isEmpty());
    }

    @Test void pointStickMarkingUsesOneReagentForEightBlanks(MinecraftServer server) {
        for (var marking : Map.of(Items.WHITE_DYE, 100, Items.BLUE_DYE, 1000,
            Items.YELLOW_DYE, 5000, Items.RED_DYE, 10000, Items.BLACK_DYE, -10000).entrySet()) {
            var input = new ArrayList<ItemStack>();
            input.add(new ItemStack(marking.getKey()));
            for (int i = 0; i < 8; i++) input.add(new ItemStack(MahjongContent.POINT_STICK));
            ItemStack sticks = craft(server, "mark_stick", 3, 3, input);
            assertEquals(8, sticks.getCount());
            assertEquals(marking.getValue(), sticks.get(MahjongComponents.POINTS));
            assertFalse(crafting(server, "mark_stick").matches(CraftingInput.of(2, 1,
                List.of(sticks, new ItemStack(marking.getKey()))), server.overworld()));
            assertFalse(crafting(server, "mark_stick").matches(CraftingInput.of(2, 1,
                List.of(new ItemStack(MahjongContent.POINT_STICK), new ItemStack(marking.getKey()))), server.overworld()));
        }
    }

    @Test void pointDrawersPersistMultipleDenominationsPrivately(MinecraftServer server) {
        var equipment = new top.skyeyefast.mchjong.world.TableEquipment(() -> {});
        var source = new ItemStack(MahjongContent.POINT_STICK, 64);
        source.set(MahjongComponents.POINTS, 5000);
        equipment.drawer(2).setItem(0, source.copy());
        var small = source.copyWithCount(3);
        small.set(MahjongComponents.POINTS, 100);
        equipment.drawer(2).setItem(1, small.copy());
        var saved = save(server, equipment);
        var loaded = new top.skyeyefast.mchjong.world.TableEquipment(() -> {});
        loaded.load(input(server, saved));
        assertTrue(ItemStack.matches(source, loaded.drawer(2).getItem(0)));
        assertTrue(ItemStack.matches(small, loaded.drawer(2).getItem(1)));
        assertEquals(10, loaded.drawer(2).getContainerSize());
        var publicData = new net.minecraft.nbt.CompoundTag();
        loaded.writeAppearance(publicData);
        assertEquals(java.util.Set.of("cloth_color", "tile_material", "tile_back", "tile_preset", "tile_back_preset"),
            publicData.keySet());
        assertTrue(ItemStack.matches(source, loaded.drawer(2).removeItemNoUpdate(0)));
        assertTrue(loaded.drawer(2).removeItemNoUpdate(0).isEmpty());
        assertFalse(saved.contains("game"));
    }

    @Test void equipmentSnapshotsOwnTheirDrawerStacks(MinecraftServer server) {
        var equipment = new top.skyeyefast.mchjong.world.TableEquipment(() -> {});
        var source = new ItemStack(MahjongContent.POINT_STICK);
        source.set(MahjongComponents.POINTS, 1000);
        var saved = save(server, equipment);
        equipment.writeAppearance(saved);
        var snapshot = saved.copy();
        equipment.drawer(0).setItem(0, source.copy());
        assertEquals(snapshot, saved, "Later equipment changes must not mutate an earlier snapshot");

        var restored = new top.skyeyefast.mchjong.world.TableEquipment(() -> {});
        restored.load(input(server, saved));
        restored.drawer(0).setItem(0, source.copy());
        assertEquals(snapshot, saved, "Restored equipment must not mutate its source NBT");
        restored.load(input(server, saved));
        assertTrue(restored.drawer(0).isEmpty());

        var appearance = new net.minecraft.nbt.CompoundTag();
        equipment.writeAppearance(appearance);
        restored.load(input(server, appearance));
        assertTrue(restored.drawer(0).isEmpty(), "Appearance packets must not disclose drawer contents");
        equipment.load(input(server, appearance));
        assertTrue(ItemStack.matches(source, equipment.drawer(0).getItem(0)), "Appearance updates must preserve private contents");
        saved = save(server, equipment);
        restored.load(input(server, saved));
        var copy = saved.copy();
        restored.drawer(0).getItem(0).grow(10);
        assertEquals(copy, saved);
        assertEquals(1, equipment.drawer(0).getItem(0).getCount());
    }

    @Test void furnitureExposesOnlyAppearanceComponentsForLoot(MinecraftServer server) {
        for (var block : List.of(MahjongContent.TABLE, MahjongContent.AUTO_TABLE)) {
            var table = new top.skyeyefast.mchjong.world.MahjongTableBlockEntity(net.minecraft.core.BlockPos.ZERO, block.defaultBlockState());
            var furniture = new ItemStack(block);
            furniture.set(MahjongComponents.WOOD, FurnitureWood.WARPED);
            table.applyComponentsFromItemStack(furniture);
            table.equipment().boxes().setItem(0, MahjongSupplies.completeBox(TileMaterial.QUARTZ, DyeColor.CYAN));
            table.equipment().installCloth(new ItemStack(MahjongContent.CLOTH_ITEM));
            var components = table.collectComponents();
            assertEquals(FurnitureWood.WARPED, components.get(MahjongComponents.WOOD));
            assertFalse(components.has(DataComponents.CONTAINER));
            assertFalse(components.has(DataComponents.BLOCK_ENTITY_DATA));
        }
        var stool = new top.skyeyefast.mchjong.world.FurnitureBlockEntity(net.minecraft.core.BlockPos.ZERO, MahjongContent.STOOL.defaultBlockState());
        var stack = new ItemStack(MahjongContent.STOOL_ITEM);
        stack.set(MahjongComponents.WOOD, FurnitureWood.CHERRY);
        stack.set(DataComponents.BASE_COLOR, DyeColor.MAGENTA);
        stool.applyComponentsFromItemStack(stack);
        assertEquals(FurnitureWood.CHERRY, stool.collectComponents().get(MahjongComponents.WOOD));
        assertEquals(DyeColor.MAGENTA, stool.collectComponents().get(DataComponents.BASE_COLOR));
    }

    @Test void oversizedBoxesCannotLoseContentsAndDrawerSlotsValidateSticks(MinecraftServer server) {
        var stored = new ArrayList<>(MahjongSupplies.contents(MahjongSupplies.completeBox(TileMaterial.BONE, DyeColor.BLUE)));
        stored.add(new ItemStack(MahjongContent.POINT_STICK));
        var oversized = box(stored);
        assertFalse(MahjongSupplies.validBox(oversized));
        assertNull(MahjongSupplies.deck(oversized));
        assertTrue(MahjongSupplies.engrave(oversized, TileFacePreset.KANSAI).isEmpty());
        assertTrue(MahjongSupplies.dye(oversized, DyeColor.RED).isEmpty());
        assertEquals(MahjongSupplies.BOX_SLOTS + 1, oversized.get(DataComponents.CONTAINER).allItemsCopyStream().count());
        var stick = new ItemStack(MahjongContent.POINT_STICK, 64);
        stick.set(MahjongComponents.POINTS, 1000);
        assertTrue(top.skyeyefast.mchjong.item.PointStickMenu.validStick(stick));
        assertFalse(top.skyeyefast.mchjong.item.PointStickMenu.validStick(new ItemStack(MahjongContent.POINT_STICK)));
        assertFalse(top.skyeyefast.mchjong.item.PointStickMenu.validStick(new ItemStack(Items.STONE)));
        assertFalse(top.skyeyefast.mchjong.item.PointStickMenu.validStick(oversized));
    }

    @Test void publicAppearanceKeepsPrivateEquipmentButEmptySavesActuallyClearIt(MinecraftServer server) {
        var equipment = new top.skyeyefast.mchjong.world.TableEquipment(() -> {});
        var box = MahjongSupplies.completeBox(TileMaterial.GLASS, DyeColor.BLUE);
        equipment.boxes().setItem(0, box.copy());
        equipment.installCloth(new ItemStack(MahjongContent.CLOTH_ITEM));
        var appearance = new net.minecraft.nbt.CompoundTag();
        equipment.writeAppearance(appearance);
        equipment.load(input(server, appearance));
        assertTrue(ItemStack.matches(box, equipment.boxes().getItem(0)));
        assertNotNull(equipment.deck());
        var empty = save(server, new top.skyeyefast.mchjong.world.TableEquipment(() -> {}));
        equipment.load(input(server, empty));
        assertTrue(equipment.boxes().isEmpty());
        assertNull(equipment.deck());
        assertFalse(equipment.hasCloth());
    }

    @Test void upgradesPreserveNamesAndInvalidPhysicalSetsStayUnchanged(MinecraftServer server) {
        var table = new ItemStack(MahjongContent.TABLE_ITEM);
        table.set(MahjongComponents.WOOD, FurnitureWood.BAMBOO);
        table.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Home table"));
        var input = List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.REDSTONE), new ItemStack(Items.IRON_INGOT),
            new ItemStack(Items.REDSTONE), table, new ItemStack(Items.REDSTONE),
            new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.HOPPER), new ItemStack(Items.COPPER_INGOT));
        var upgraded = craft(server, "upgrade_table", 3, 3, input);
        assertEquals(table.get(DataComponents.CUSTOM_NAME), upgraded.get(DataComponents.CUSTOM_NAME));
        assertEquals(FurnitureWood.BAMBOO, upgraded.get(MahjongComponents.WOOD));
        table.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        assertFalse(crafting(server, "upgrade_table").matches(CraftingInput.of(3, 3, input), server.overworld()));
        for (int alteration = 0; alteration < 3; alteration++) {
            var original = MahjongSupplies.completeBox(TileMaterial.BONE, DyeColor.BLUE);
            var contents = MahjongSupplies.contents(original);
            if (alteration == 0) contents.getFirst().shrink(1);
            else if (alteration == 1) contents.getFirst().set(DataComponents.BASE_COLOR, DyeColor.RED);
            else contents.getFirst().set(MahjongComponents.TILE, new TileData(0, TileMaterial.GLASS, false));
            original.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
            var before = original.copy();
            assertNull(MahjongSupplies.deck(original));
            assertTrue(ItemStack.matches(before, original));
        }
    }

    @Test void nativeContainerAndComponentCodecsRoundTripACompleteGlassSet(MinecraftServer server) {
        var original = MahjongSupplies.completeBox(TileMaterial.GLASS, DyeColor.MAGENTA);
        var restored = roundTrip(server, original);
        assertTrue(ItemStack.matches(original, restored));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess(), ConnectionType.NEOFORGE);
        try {
            ItemStack.STREAM_CODEC.encode(buffer, original);
            var synchronizedBox = ItemStack.STREAM_CODEC.decode(buffer);
            assertTrue(ItemStack.matches(original, synchronizedBox));
            assertNotNull(MahjongSupplies.deck(synchronizedBox));
        } finally { buffer.release(); }
        assertTrue(TileData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
            com.google.gson.JsonParser.parseString("{\"face\":0,\"material\":\"glass\",\"red\":true}")).error().isPresent());
    }
}
