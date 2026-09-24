package top.skyeyefast.mchjong.compat.create;

import java.util.List;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.compat.ponder.MchjongPonder;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Shared optional storyboards; inventories belong to Ponder's isolated display world. */
public final class CreatePonder implements PonderPlugin {
    private static final ResourceLocation TAG = MahjongContent.id("mahjong_workshop");
    private static final BlockPos BASIN = new BlockPos(3, 1, 3);
    private static final Vec3 TARGET = Vec3.atCenterOf(BASIN);

    public static void register() { MchjongPonder.register(new CreatePonder()); }
    @Override public String getModId() { return MahjongContent.MOD_ID; }
    private static List<Item> entries() {
        return List.of(CreateCompat.PRINTING_PLATE, CreateCompat.INCOMPLETE_BOX,
            MahjongContent.MAHJONG_DYE, MahjongContent.RED_DORA_DYE, MahjongContent.UNDO_DYE);
    }
    @Override public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        var items = helper.withKeyFunction(BuiltInRegistries.ITEM::getKey);
        for (var item : entries()) {
            items.addStoryBoard(item, "workshop", CreatePonder::production, TAG);
            items.addStoryBoard(item, "workshop", CreatePonder::dyeing, TAG);
        }
    }
    @Override public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(TAG).title("Mahjong workshop").description("Automated printing, dyeing and packing")
            .item(CreateCompat.PRINTING_PLATE).addToIndex().register();
        for (var item : entries()) helper.addTagToComponent(BuiltInRegistries.ITEM.getKey(item), TAG);
    }

    private static void production(SceneBuilder scene, SceneBuildingUtil util) {
        base(scene, util, "workshop_production", "Producing mahjong supplies", false);
        scene.world().setBlock(new BlockPos(1, 1, 3), AllBlocks.MECHANICAL_SAW.getDefaultState()
            .setValue(com.simibubi.create.content.kinetics.saw.SawBlock.FACING, Direction.UP), false);
        scene.world().showSection(util.select().position(1, 1, 3), Direction.DOWN);
        new com.simibubi.create.foundation.ponder.CreateSceneBuilder(scene).world().setKineticSpeed(util.select().position(1, 1, 3), 32);
        show(scene, new ItemStack(Items.BONE_BLOCK, 6));
        say(scene, "A mechanical saw cuts 24 blank tiles per material. Six materials make 144 tiles. A bone block also yields 32 blank point sticks.");
        show(scene, new ItemStack(MahjongContent.MAHJONG_DYE, 2));
        say(scene, "Mix black, red, green, blue and white dyes for two mahjong dyes. Mixing a red or black dye yields eight red dora or undo dyes.");
        scene.world().setBlock(new BlockPos(5, 3, 3), AllBlocks.DEPLOYER.getDefaultState()
            .setValue(com.simibubi.create.content.kinetics.deployer.DeployerBlock.FACING, Direction.DOWN), false);
        scene.world().setBlock(new BlockPos(5, 1, 3), AllBlocks.DEPOT.getDefaultState(), false);
        scene.world().showSection(util.select().fromTo(5, 1, 3, 5, 3, 3), Direction.DOWN);
        show(scene, new ItemStack(CreateCompat.INCOMPLETE_BOX));
        say(scene, "To assemble a box, apply leather to a chest with a deployer, apply an iron nugget, then press it. This route saves the wooden slabs.");
        var blank = MahjongSupplies.tile(new TileData(-1, TileMaterial.BONE, false), 64);
        var plate = CreateCompat.plate(TileFacePreset.KANTO);
        var inputs = List.of(blank, blank.copy(), blank.copyWithCount(16), new ItemStack(MahjongContent.BOX_ITEM),
            new ItemStack(MahjongContent.MAHJONG_DYE), plate);
        scene.world().modifyBlockEntity(BASIN, BasinBlockEntity.class, basin -> {
            for (int i = 0; i < inputs.size(); i++) basin.inputInventory.setStackInSlot(i, inputs.get(i).copy());
        });
        show(scene, plate);
        say(scene, "Under a mechanical press, supply one empty box, 144 identical blank tiles, one mahjong dye and a printing plate. The plate selects Kansai or Kanto faces.");
        var printed = CreateProcessing.pressing(inputs).outputs().get(0);
        scene.world().modifyBlockEntity(BASIN.above(2), com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity.class,
            press -> press.getPressingBehaviour().start(com.simibubi.create.content.kinetics.press.PressingBehaviour.Mode.BASIN));
        scene.idle(30);
        scene.world().modifyBlockEntity(BASIN, BasinBlockEntity.class, basin -> {
            basin.inputInventory.clearContent();
            basin.getOutputInventory().setStackInSlot(0, printed.copy());
            basin.getOutputInventory().setStackInSlot(1, plate.copy());
        });
        show(scene, printed);
        say(scene, "The press returns the printing plate with the complete 144-tile box. Route the plate back for the next batch. Material, backs and names are retained.");
        say(scene, "A press and basin can also pack tiles, marked sticks and dice into a box. Existing slot limits apply. Route the finished box directly to the table.");
        scene.markAsFinished();
    }

    private static void dyeing(SceneBuilder scene, SceneBuildingUtil util) {
        base(scene, util, "workshop_dyeing", "Dyeing tiles and point sticks", true);
        var box = MahjongSupplies.completeBox(TileMaterial.BONE);
        scene.world().modifyBlockEntity(BASIN, BasinBlockEntity.class, basin -> {
            basin.inputInventory.setStackInSlot(0, box.copy());
            basin.inputInventory.setStackInSlot(1, box.copy());
            basin.inputInventory.setStackInSlot(2, new ItemStack(Items.BLUE_DYE));
        });
        show(scene, new ItemStack(Items.BLUE_DYE));
        say(scene, "One dye colors two targets in a mixer and basin. Each target is a whole tile stack or one box. Two boxes and one blue dye color both complete sets.");
        var dyed = MahjongSupplies.dyeBatch(List.of(box, box.copy()), DyeColor.BLUE);
        scene.world().modifyBlockEntity(BASIN, BasinBlockEntity.class, basin -> {
            basin.inputInventory.clearContent();
            for (int i = 0; i < dyed.size(); i++) basin.getOutputInventory().setStackInSlot(i, dyed.get(i).copy());
        });
        show(scene, dyed.get(0));
        say(scene, "Only the tile backs change. Faces, red fives, material, names, point sticks and dice remain intact. Glass tiles become tinted glass.");
        show(scene, new ItemStack(MahjongContent.UNDO_DYE));
        say(scene, "One undo dye removes back coloring from two targets. Both targets must actually change; an unchanged batch does not start or consume dye.");
        scene.world().setBlock(BASIN.above(2), AllBlocks.DEPLOYER.getDefaultState()
            .setValue(com.simibubi.create.content.kinetics.deployer.DeployerBlock.FACING, Direction.DOWN), false);
        scene.world().setBlock(BASIN, AllBlocks.DEPOT.getDefaultState(), false);
        show(scene, MahjongSupplies.tile(new TileData(13, TileMaterial.BONE, true), 1));
        say(scene, "On a belt or depot, a deployer applies red dora dye to one ordinary five at a time. Undo dye restores one red five without changing its back.");
        scene.world().setBlock(BASIN, AllBlocks.BASIN.getDefaultState(), false);
        scene.world().setBlock(BASIN.above(2), AllBlocks.MECHANICAL_MIXER.getDefaultState(), false);
        show(scene, new ItemStack(MahjongContent.POINT_STICK, 16));
        say(scene, "A mixer marks 16 identical blank sticks with one dye: white 100, blue 1000, yellow 5000, red 10000 or black -10000.");
        scene.markAsFinished();
    }

    private static void base(SceneBuilder scene, SceneBuildingUtil util, String id, String title, boolean mixer) {
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(0.65f);
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++)
            scene.world().setBlock(new BlockPos(x, 1, z), Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(BASIN, AllBlocks.BASIN.getDefaultState(), false);
        scene.world().setBlock(BASIN.above(2), mixer ? AllBlocks.MECHANICAL_MIXER.getDefaultState() : AllBlocks.MECHANICAL_PRESS.getDefaultState(), false);
        new com.simibubi.create.foundation.ponder.CreateSceneBuilder(scene).world().setKineticSpeed(util.select().position(BASIN.above(2)), 32);
        scene.showBasePlate();
        scene.world().showSection(util.select().fromTo(3, 1, 3, 3, 3, 3), Direction.DOWN);
        scene.idle(15);
    }
    private static void show(SceneBuilder scene, ItemStack stack) { scene.overlay().showControls(TARGET, Pointing.DOWN, 90).withItem(stack); }
    private static void say(SceneBuilder scene, String text) {
        scene.overlay().showText(110).text(text).pointAt(TARGET).placeNearTarget().attachKeyFrame();
        scene.idle(120);
    }
}
