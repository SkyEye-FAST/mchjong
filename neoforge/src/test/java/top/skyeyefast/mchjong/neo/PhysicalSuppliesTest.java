package top.skyeyefast.mchjong.neo;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
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
import top.skyeyefast.mchjong.world.MahjongContent;
import static org.junit.jupiter.api.Assertions.*;

/** These tests decode the generated datapacks and exercise actual registered Minecraft recipes. */
@ExtendWith(EphemeralTestServerProvider.class)
class PhysicalSuppliesTest {
    @Test void catalogueKeepsBoxesAdjacentAndListsEveryStickWithoutSeparateTileFaces(MinecraftServer server) {
        var entries = top.skyeyefast.mchjong.item.MahjongCatalog.entries();
        assertEquals(12, entries.size());
        assertTrue(entries.get(4).is(MahjongContent.BOX_ITEM));
        assertTrue(entries.get(5).is(MahjongContent.BOX_ITEM));
        assertEquals(0, MahjongSupplies.tileCount(MahjongSupplies.contents(entries.get(4))));
        assertEquals(136, MahjongSupplies.tileCount(MahjongSupplies.contents(entries.get(5))));
        assertNotNull(MahjongSupplies.deck(entries.get(5)));
        assertEquals(List.of(0, 100, 1000, 5000, 10000), entries.stream()
            .filter(stack -> stack.is(MahjongContent.POINT_STICK)).map(stack -> stack.get(MahjongComponents.POINTS)).toList());
        assertEquals(1, entries.stream().filter(stack -> stack.is(MahjongContent.TILE_ITEM)).count());
        assertTrue(MahjongSupplies.tile(entries.get(6)).blank());
        for (int i = 0; i < entries.size(); i++) for (int j = i + 1; j < entries.size(); j++)
            assertFalse(ItemStack.isSameItemSameComponents(entries.get(i), entries.get(j)));
        entries.getFirst().shrink(1);
        assertEquals(1, top.skyeyefast.mchjong.item.MahjongCatalog.entries().getFirst().getCount());
    }

    private static Item vanilla(String name) {
        return BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(name));
    }
    private static CraftingRecipe crafting(MinecraftServer server, String id) {
        return (CraftingRecipe) server.getRecipeManager().byKey(MahjongContent.id(id)).orElseThrow().value();
    }
    private static ItemStack craft(MinecraftServer server, String id, int width, int height, List<ItemStack> items) {
        var input = CraftingInput.of(width, height, items);
        var recipe = crafting(server, id);
        assertTrue(recipe.matches(input, server.overworld()), id);
        return recipe.assemble(input, server.registryAccess());
    }
    private static ItemStack box(List<ItemStack> items) {
        var box = new ItemStack(MahjongContent.BOX_ITEM);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        return box;
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
            assertSame(component, BuiltInRegistries.DATA_COMPONENT_TYPE.get(MahjongContent.id(name))));
        MahjongContent.SUPPLIES.forEach((name, item) -> assertSame(item, BuiltInRegistries.ITEM.get(MahjongContent.id(name))));
    }

    @Test void stonecuttingMakesBlanksAndEngravesAllFacesWithoutLosingMaterialOrDye(MinecraftServer server) {
        for (TileMaterial material : TileMaterial.values()) {
            var cutting = (StonecutterRecipe) server.getRecipeManager().byKey(MahjongContent.id("blanks_" + material.getSerializedName())).orElseThrow().value();
            var input = new SingleRecipeInput(new ItemStack(vanilla(material.source())));
            assertTrue(cutting.matches(input, server.overworld()));
            ItemStack blanks = cutting.assemble(input, server.registryAccess());
            assertTrue(blanks.is(MahjongContent.TILE_ITEM));
            assertEquals(16, blanks.getCount());
            assertEquals(new TileData(-1, material, false), blanks.get(MahjongComponents.TILE));
            blanks.set(DataComponents.BASE_COLOR, DyeColor.LIME);
            for (int face = 0; face < 45; face++) {
                var engraving = (StonecutterRecipe) server.getRecipeManager().byKey(MahjongContent.id("engrave_tile_" + face)).orElseThrow().value();
                var blankInput = new SingleRecipeInput(blanks);
                assertTrue(engraving.matches(blankInput, server.overworld()));
                ItemStack tile = engraving.assemble(blankInput, server.registryAccess());
                TileData data = MahjongSupplies.tile(tile);
                assertEquals(material, data.material());
                boolean red = face >= 34 && face < 37;
                assertEquals(red ? 4 + (face - 34) * 9 : face >= 37 ? face - 3 : face, data.face());
                assertEquals(red, data.red());
                assertEquals(face >= 37, data.flower());
                assertEquals(DyeColor.LIME, MahjongSupplies.color(tile));
                assertEquals(1, tile.getCount());
                assertFalse(engraving.matches(new SingleRecipeInput(tile), server.overworld()));
                assertTrue(engraving.assemble(new SingleRecipeInput(tile), server.registryAccess()).isEmpty());
                assertTrue(MahjongSupplies.tile(blanks).blank());
            }
        }
    }

    @Test void eightFlowersRoundTripAndStayOutsideTheRiichiWall(MinecraftServer server) {
        for (TileMaterial material : TileMaterial.values()) {
            var original = MahjongSupplies.completeBox(material, DyeColor.BLUE);
            var contents = MahjongSupplies.contents(original);
            for (int flower = 0; flower < TileData.FLOWER_COUNT; flower++) {
                var data = new TileData(TileData.FIRST_FLOWER + flower, material, false);
                var stack = MahjongSupplies.tile(data, DyeColor.BLUE, 1);
                assertTrue(data.flower());
                assertEquals(data, TileData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    TileData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, data).getOrThrow()).getOrThrow());
                contents.set(37 + flower, stack);
            }
            original.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
            assertEquals(144, MahjongSupplies.tileCount(MahjongSupplies.contents(original)));
            var deck = MahjongSupplies.deck(original);
            assertNotNull(deck);
            assertEquals(136, deck.tiles(false).size());
            assertEquals(108, deck.tiles(true).size());
            assertTrue(deck.tiles(false).stream().allMatch(tile -> tile >= 0 && tile < 136));
            var loaded = ItemStack.parseOptional(server.registryAccess(), (net.minecraft.nbt.CompoundTag) original.save(server.registryAccess()));
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

    @Test void engravingAndBulkDyeAreAtomicAndKeepSparesAndPhysicalSticks(MinecraftServer server) {
        for (TileMaterial material : TileMaterial.values()) {
            var blank = new TileData(-1, material, false);
            var stick = new ItemStack(MahjongContent.POINT_STICK, 12);
            stick.set(MahjongComponents.POINTS, 1000);
            ItemStack source = box(List.of(MahjongSupplies.tile(blank, DyeColor.BLUE, 64), MahjongSupplies.tile(blank, DyeColor.BLUE, 64),
                MahjongSupplies.tile(blank, DyeColor.BLUE, 16), stick));
            ItemStack before = source.copy();
            ItemStack engraved = craft(server, "engrave_set", 2, 1, List.of(source, new ItemStack(Items.INK_SAC)));
            assertTrue(ItemStack.matches(before, source));
            assertNotNull(MahjongSupplies.deck(engraved));
            assertEquals(144, MahjongSupplies.tileCount(MahjongSupplies.contents(engraved)));
            assertEquals(8, MahjongSupplies.contents(engraved).stream().filter(s -> s.is(MahjongContent.TILE_ITEM)
                && MahjongSupplies.tile(s).blank()).mapToInt(ItemStack::getCount).sum());
            assertEquals(12, MahjongSupplies.contents(engraved).stream().filter(s -> s.is(MahjongContent.POINT_STICK)).mapToInt(ItemStack::getCount).sum());
            assertTrue(MahjongSupplies.engrave(engraved).isEmpty(), "Cannot re-engrave an existing set");
            for (DyeColor color : DyeColor.values()) {
                ItemStack dyed = craft(server, "dye", 2, 1, List.of(engraved, new ItemStack(vanilla(color.getName() + "_dye"))));
                var deck = MahjongSupplies.deck(dyed);
                assertNotNull(deck);
                assertEquals(material, deck.material());
                assertEquals(color, deck.back());
                assertEquals(136, deck.tiles(false).size());
                assertEquals(108, deck.tiles(true).size());
                assertEquals(144, MahjongSupplies.tileCount(MahjongSupplies.contents(dyed)));
                assertEquals(DyeColor.BLUE, MahjongSupplies.deck(engraved).back());
                assertEquals(3, MahjongSupplies.contents(dyed).stream().filter(s -> s.is(MahjongContent.TILE_ITEM)
                    && MahjongSupplies.tile(s).red()).mapToInt(ItemStack::getCount).sum());
            }
        }
        var shortBox = box(List.of(MahjongSupplies.tile(TileData.BLANK, DyeColor.BLUE, 64)));
        assertTrue(MahjongSupplies.engrave(shortBox).isEmpty());
        var mixed = box(List.of(MahjongSupplies.tile(TileData.BLANK, DyeColor.BLUE, 64),
            MahjongSupplies.tile(TileData.BLANK, DyeColor.RED, 64), MahjongSupplies.tile(TileData.BLANK, DyeColor.BLUE, 8)));
        assertTrue(MahjongSupplies.engrave(mixed).isEmpty());
        assertFalse(MahjongSupplies.storable(new ItemStack(MahjongContent.BOX_ITEM)));
        var nested = new ItemStack(MahjongContent.TILE_ITEM);
        nested.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        assertFalse(MahjongSupplies.storable(nested));
    }

    @Test void pointStickMarkingUsesOneReagentForUpToEightBlanks(MinecraftServer server) {
        for (var marking : Map.of(Items.BLACK_DYE, 100, Items.REDSTONE, 1000, Items.LAPIS_LAZULI, 5000, Items.GOLD_NUGGET, 10000).entrySet()) {
            var input = new ArrayList<ItemStack>();
            input.add(new ItemStack(marking.getKey()));
            for (int i = 0; i < 8; i++) input.add(new ItemStack(MahjongContent.POINT_STICK));
            ItemStack sticks = craft(server, "mark_stick", 3, 3, input);
            assertEquals(8, sticks.getCount());
            assertEquals(marking.getValue(), sticks.get(MahjongComponents.POINTS));
            assertFalse(crafting(server, "mark_stick").matches(CraftingInput.of(2, 1,
                List.of(sticks, new ItemStack(marking.getKey()))), server.overworld()));
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
        var saved = new net.minecraft.nbt.CompoundTag();
        equipment.save(saved, server.registryAccess());
        var loaded = new top.skyeyefast.mchjong.world.TableEquipment(() -> {});
        loaded.load(saved, server.registryAccess());
        assertTrue(ItemStack.matches(source, loaded.drawer(2).getItem(0)));
        assertTrue(ItemStack.matches(small, loaded.drawer(2).getItem(1)));
        assertEquals(9, loaded.drawer(2).getContainerSize());
        var publicData = new net.minecraft.nbt.CompoundTag();
        loaded.writeAppearance(publicData);
        assertEquals(java.util.Set.of("cloth_color", "tile_material", "tile_back"), publicData.getAllKeys());
        assertTrue(ItemStack.matches(source, loaded.drawer(2).removeItemNoUpdate(0)));
        assertTrue(loaded.drawer(2).removeItemNoUpdate(0).isEmpty());
        assertFalse(saved.contains("game"));
    }

    @Test void equipmentSnapshotsOwnTheirDrawerStacks(MinecraftServer server) {
        var equipment = new top.skyeyefast.mchjong.world.TableEquipment(() -> {});
        var source = new ItemStack(MahjongContent.POINT_STICK);
        source.set(MahjongComponents.POINTS, 1000);
        var saved = new net.minecraft.nbt.CompoundTag();
        equipment.writeAppearance(saved);
        equipment.save(saved, server.registryAccess());
        var snapshot = saved.copy();
        equipment.drawer(0).setItem(0, source.copy());
        assertEquals(snapshot, saved, "Later equipment changes must not mutate an earlier snapshot");

        var restored = new top.skyeyefast.mchjong.world.TableEquipment(() -> {});
        restored.load(saved, server.registryAccess());
        restored.drawer(0).setItem(0, source.copy());
        assertEquals(snapshot, saved, "Restored equipment must not mutate its source NBT");
        restored.load(saved, server.registryAccess());
        assertTrue(restored.drawer(0).isEmpty());

        var appearance = new net.minecraft.nbt.CompoundTag();
        equipment.writeAppearance(appearance);
        restored.load(appearance, server.registryAccess());
        assertTrue(restored.drawer(0).isEmpty(), "Appearance packets must not disclose drawer contents");
        equipment.load(appearance, server.registryAccess());
        assertTrue(ItemStack.matches(source, equipment.drawer(0).getItem(0)), "Appearance updates must preserve private contents");
        equipment.save(saved, server.registryAccess());
        restored.load(saved, server.registryAccess());
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
        assertTrue(MahjongSupplies.engrave(oversized).isEmpty());
        assertTrue(MahjongSupplies.dye(oversized, DyeColor.RED).isEmpty());
        assertEquals(55, oversized.get(DataComponents.CONTAINER).stream().count());
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
        equipment.load(appearance, server.registryAccess());
        assertTrue(ItemStack.matches(box, equipment.boxes().getItem(0)));
        assertNotNull(equipment.deck());
        var empty = new net.minecraft.nbt.CompoundTag();
        new top.skyeyefast.mchjong.world.TableEquipment(() -> {}).save(empty, server.registryAccess());
        equipment.load(empty, server.registryAccess());
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
        var tag = original.save(server.registryAccess());
        var restored = ItemStack.parse(server.registryAccess(), tag).orElseThrow();
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
