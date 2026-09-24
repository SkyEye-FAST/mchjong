package top.skyeyefast.mchjong.neo;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import top.skyeyefast.mchjong.compat.create.CreateCompat;
import top.skyeyefast.mchjong.compat.create.CreateProcessing;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.MahjongContent;
import static org.junit.jupiter.api.Assertions.*;

/** One installed-Create server case covers native matching, atomic consumption and output rejection. */
@ExtendWith(EphemeralTestServerProvider.class)
class CreateProcessingTest {
    @Test void nativeBasinTransactionsAndSingleTileDeploying(MinecraftServer server) throws Exception {
        server.submit(() -> {
            try { workshop(server); }
            catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        }).get();
    }

    private static void workshop(MinecraftServer server) throws ReflectiveOperationException {
        // This provider loads registries but intentionally creates no levels. Build its already
        // configured flat dimensions on the server thread, without preparing a spawn-chunk region.
        if (server.overworld() == null) {
            var createLevels = MinecraftServer.class.getDeclaredMethod("createLevels", net.minecraft.server.level.progress.ChunkProgressListener.class);
            createLevels.setAccessible(true);
            createLevels.invoke(server, net.minecraft.server.level.progress.LoggerChunkProgressListener.create(0));
        }
        var level = server.overworld();
        var pos = new BlockPos(2, 80, 2);
        level.getChunkAt(pos);
        level.setBlockAndUpdate(pos, AllBlocks.BASIN.getDefaultState());
        level.setBlockAndUpdate(pos.above(2), AllBlocks.MECHANICAL_PRESS.getDefaultState());
        var basin = (BasinBlockEntity) level.getBlockEntity(pos);
        assertNotNull(basin);
        try {
            var blank = MahjongSupplies.tile(new TileData(-1, TileMaterial.GLASS, false), DyeColor.CYAN, 64);
            blank.set(DataComponents.CUSTOM_NAME, Component.literal("Workshop"));
            var plate = CreateCompat.plate(TileFacePreset.KANTO);
            plate.set(DataComponents.CUSTOM_NAME, Component.literal("Reusable plate"));
            var inputs = List.of(blank, blank.copy(), blank.copyWithCount(16), new ItemStack(MahjongContent.BOX_ITEM),
                new ItemStack(MahjongContent.MAHJONG_DYE), plate);
            for (var input : inputs)
                assertTrue(net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(basin.inputInventory, input.copy(), false).isEmpty(),
                    "Ordinary hopper/belt insertion must accept all 144 identical blanks");
            var batch = CreateProcessing.pressing(inputs);
            assertNotNull(batch);
            assertEquals(147, batch.recipe().getIngredients().size());
            assertTrue(batch.recipe().validate().isEmpty());
            var matcher = BasinOperatingBlockEntity.class.getDeclaredMethod("getMatchingRecipes");
            matcher.setAccessible(true);
            @SuppressWarnings("unchecked")
            var recipes = (List<Recipe<?>>) matcher.invoke(level.getBlockEntity(pos.above(2)));
            assertFalse(recipes.isEmpty(), "The optional mixin must expose the full-deck operation to the press");
            assertTrue(BasinRecipe.match(basin, recipes.getFirst()));
            assertTrue(BasinRecipe.apply(basin, recipes.getFirst()));
            assertEquals(0, total(basin.inputInventory));
            var produced = stacks(basin.getOutputInventory());
            var printed = produced.stream().filter(s -> s.is(MahjongContent.BOX_ITEM)).findFirst().orElseThrow();
            assertEquals(144, MahjongSupplies.tileCount(MahjongSupplies.contents(printed)));
            assertEquals(TileFacePreset.KANTO, MahjongSupplies.deck(printed).preset());
            assertTrue(produced.stream().anyMatch(s -> ItemStack.matches(s, plate)));
            assertFalse(BasinRecipe.apply(basin, recipes.getFirst()), "A stale transaction cannot be repeated");

            clear(basin);
            var pair = List.of(printed, printed.copy(), new ItemStack(Items.BLUE_DYE));
            for (var input : pair)
                assertTrue(net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(basin.inputInventory, input.copy(), false).isEmpty(),
                    "Two identical nonstackable boxes must fit in distinct input slots");
            var dye = CreateProcessing.mixing(pair);
            assertNotNull(dye);
            for (int i = 0; i < basin.getOutputInventory().getSlots() - 1; i++)
                basin.getOutputInventory().setStackInSlot(i, new ItemStack(Items.STONE, 64));
            assertFalse(BasinRecipe.match(basin, dye.recipe()));
            assertFalse(BasinRecipe.apply(basin, dye.recipe()));
            assertEquals(3, total(basin.inputInventory), "One free output slot cannot accept two boxes or consume the dye");
            for (int i = 0; i < basin.getOutputInventory().getSlots(); i++) basin.getOutputInventory().setStackInSlot(i, ItemStack.EMPTY);
            assertTrue(BasinRecipe.apply(basin, dye.recipe()));
            var dyed = stacks(basin.getOutputInventory());
            assertEquals(2, dyed.size());
            dyed.forEach(box -> assertEquals(DyeColor.BLUE, MahjongSupplies.deck(box).back()));
            var noOp = new ArrayList<>(dyed);
            noOp.add(new ItemStack(Items.BLUE_DYE));
            assertNull(CreateProcessing.mixing(noOp));
            noOp.set(2, new ItemStack(MahjongContent.UNDO_DYE));
            assertNotNull(CreateProcessing.mixing(noOp));

            clear(basin);
            level.setBlockAndUpdate(pos.above(2), AllBlocks.MECHANICAL_MIXER.getDefaultState());
            basin.inputInventory.setStackInSlot(0, new ItemStack(MahjongContent.POINT_STICK, 32));
            basin.inputInventory.setStackInSlot(1, new ItemStack(Items.BLUE_DYE));
            var mark = CreateProcessing.mixing(stacks(basin.inputInventory));
            assertNotNull(mark);
            assertTrue(BasinRecipe.apply(basin, mark.recipe()));
            assertEquals(16, total(basin.inputInventory));
            var marked = stacks(basin.getOutputInventory()).getFirst();
            assertEquals(16, marked.getCount());
            clear(basin);
            level.setBlockAndUpdate(pos.above(2), AllBlocks.MECHANICAL_PRESS.getDefaultState());
            basin.inputInventory.setStackInSlot(0, printed.copy());
            basin.inputInventory.setStackInSlot(1, marked.copy());
            basin.inputInventory.setStackInSlot(2, new ItemStack(MahjongContent.DICE, 2));
            var packing = CreateProcessing.pressing(stacks(basin.inputInventory));
            assertNotNull(packing);
            assertTrue(BasinRecipe.apply(basin, packing.recipe()));
            var packed = stacks(basin.getOutputInventory()).getFirst();
            assertTrue(MahjongSupplies.validBox(packed));
            assertEquals(144, MahjongSupplies.tileCount(MahjongSupplies.contents(packed)));
            assertEquals(2, MahjongSupplies.contents(packed).get(MahjongSupplies.DICE_SLOT).getCount());
            assertEquals(16, MahjongSupplies.contents(packed).stream().filter(stack -> stack.is(MahjongContent.POINT_STICK))
                .mapToInt(ItemStack::getCount).sum());

            var five = MahjongSupplies.tile(new TileData(13, TileMaterial.GLASS, false), DyeColor.CYAN, 64);
            five.set(DataComponents.CUSTOM_NAME, Component.literal("Five"));
            var red = CreateProcessing.deploying(five, new ItemStack(MahjongContent.RED_DORA_DYE));
            assertNotNull(red);
            var result = red.getResultItem(server.registryAccess());
            assertEquals(1, result.getCount());
            assertEquals(five.getHoverName(), result.getHoverName());
            assertEquals(64, five.getCount());
            assertTrue(MahjongSupplies.tile(result).red());
            assertNull(CreateProcessing.deploying(result, new ItemStack(MahjongContent.RED_DORA_DYE)));
            assertNotNull(CreateProcessing.deploying(result, new ItemStack(MahjongContent.UNDO_DYE)));
            assertEquals(16, CreateProcessing.mixing(List.of(new ItemStack(MahjongContent.POINT_STICK, 32),
                new ItemStack(Items.WHITE_DYE))).outputs().getFirst().getCount());

            for (String id : List.of("blanks_bone", "blank_point_sticks", "mahjong_dye", "red_dora_dye", "undo_dye",
                    "printing_plate_kansai", "printing_plate_kanto", "mahjong_box"))
                assertTrue(server.getRecipeManager().byKey(MahjongContent.id("create/" + id)).isPresent(), id);
            for (String id : List.of("blanks_bone", "blank_point_sticks", "mahjong_dye")) {
                assertTrue(com.simibubi.create.AllRecipeTypes.shouldIgnoreInAutomation(
                    server.getRecipeManager().byKey(MahjongContent.id(id)).orElseThrow()));
                assertFalse(com.simibubi.create.AllRecipeTypes.shouldIgnoreInAutomation(
                    server.getRecipeManager().byKey(MahjongContent.id("create/" + id)).orElseThrow()));
            }
            bulkSaw(server, pos.east(3));
            var assembled = new ItemStack(Items.CHEST);
            for (var reagent : List.of(Items.LEATHER, Items.IRON_NUGGET)) {
                var application = com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe.getRecipe(level, assembled,
                    com.simibubi.create.AllRecipeTypes.DEPLOYING.getType(),
                    com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe.class).orElseThrow().value();
                assertTrue(application.getIngredients().get(1).test(new ItemStack(reagent)));
                assembled = application.rollResults(level.random).getFirst();
                assertTrue(assembled.is(CreateCompat.INCOMPLETE_BOX.get()));
            }
            var finishing = com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe.getRecipe(level, assembled,
                com.simibubi.create.AllRecipeTypes.PRESSING.getType(),
                com.simibubi.create.content.kinetics.press.PressingRecipe.class).orElseThrow().value();
            assertTrue(MahjongSupplies.validBox(finishing.rollResults(level.random).getFirst()), "Native sequence must finish a usable empty box");
        } finally {
            level.removeBlock(pos.above(2), false);
            level.removeBlock(pos, false);
        }
    }

    private static void bulkSaw(MinecraftServer server, BlockPos pos) throws ReflectiveOperationException {
        var level = server.overworld();
        level.setBlockAndUpdate(pos, AllBlocks.MECHANICAL_SAW.getDefaultState()
            .setValue(com.simibubi.create.content.kinetics.saw.SawBlock.FACING, net.minecraft.core.Direction.UP));
        try {
            var saw = (com.simibubi.create.content.kinetics.saw.SawBlockEntity) level.getBlockEntity(pos);
            assertNotNull(saw);
            saw.initialize();
            var filter = com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(saw,
                com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour.TYPE);
            filter.setFilter(new ItemStack(MahjongContent.POINT_STICK));
            saw.inventory.setStackInSlot(0, new ItemStack(Items.BONE_BLOCK, 64));
            var apply = com.simibubi.create.content.kinetics.saw.SawBlockEntity.class.getDeclaredMethod("applyRecipe");
            apply.setAccessible(true);
            apply.invoke(saw);
            assertEquals(64 * 32, total(saw.inventory), "Bulk cutting must not discard the thirty-second output stack");
            assertTrue(stacks(saw.inventory).stream().allMatch(stack -> stack.is(MahjongContent.POINT_STICK)));
        } finally { level.removeBlock(pos, false); }
    }

    private static List<ItemStack> stacks(net.neoforged.neoforge.items.IItemHandler handler) {
        var stacks = new ArrayList<ItemStack>();
        for (int i = 0; i < handler.getSlots(); i++) if (!handler.getStackInSlot(i).isEmpty()) stacks.add(handler.getStackInSlot(i).copy());
        return stacks;
    }
    private static int total(net.neoforged.neoforge.items.IItemHandler handler) {
        return stacks(handler).stream().mapToInt(ItemStack::getCount).sum();
    }
    private static void clear(BasinBlockEntity basin) {
        for (int i = 0; i < basin.inputInventory.getSlots(); i++) basin.inputInventory.setStackInSlot(i, ItemStack.EMPTY);
        for (int i = 0; i < basin.getOutputInventory().getSlots(); i++) basin.getOutputInventory().setStackInSlot(i, ItemStack.EMPTY);
    }
}
