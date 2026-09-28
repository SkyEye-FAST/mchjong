package top.skyeyefast.mchjong.compat.ponder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Storyboards operate only on Ponder's isolated display world. */
final class MahjongScenes {
    private static final BlockPos TABLE = new BlockPos(3, 1, 3);
    private static final Vec3 FELT = new Vec3(3.5, 2.05, 3.5);
    private static final Vec3 CONTROLS = FELT.add(-2, 1.6, 2);
    private static final int TEXT_TIME = 100;

    private MahjongScenes() {}

    static void placement(SceneBuilder scene, SceneBuildingUtil util) {
        base(scene, "table_placement", "Placing a mahjong table");
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "footprint",
            new AABB(2, 1, 2, 5, 2, 5), TEXT_TIME);
        say(scene, "Leave a clear 3 × 3 area for the table, with nothing blocking the space above.", FELT);
        scene.world().showSection(util.select().position(TABLE), Direction.DOWN);
        say(scene, "Place the table in the center of the area; its rim reaches into the surrounding blocks.", FELT);
        for (int seat = 0; seat < 4; seat++) {
            scene.world().showSection(util.select().position(TableGeometry.stool(TABLE, seat)), Direction.DOWN);
            scene.idle(8);
        }
        say(scene, "Put one stool two blocks from the center on each side you play on.",
            Vec3.atCenterOf(TableGeometry.stool(TABLE, 0)));
        scene.rotateCameraY(90);
        say(scene, "Both ordinary and automatic tables use this compact layout.", FELT);
        scene.markAsFinished();
    }

    static void equipment(SceneBuilder scene, SceneBuildingUtil util) {
        base(scene, "table_equipment", "Preparing the table");
        scene.world().showSection(util.select().fromTo(1, 1, 1, 5, 1, 5), Direction.DOWN);
        ItemStack box = MahjongSupplies.completeBox(TileMaterial.BONE, DyeColor.BLUE);
        scene.overlay().showControls(CONTROLS, Pointing.DOWN, 80).rightClick().withItem(box);
        say(scene, "Put the tiles your rules need into the box: 136 for four players or 108 for three, all with matching material, backs, and face preset. Spare tiles may stay in the box.", FELT);
        scene.world().modifyBlockEntity(TABLE, MahjongTableBlockEntity.class,
            table -> table.equipment().boxes().setItem(0, box.copy()));
        scene.overlay().showControls(CONTROLS, Pointing.DOWN, 80).rightClick();
        say(scene, "Right-click the table to open its storage; it holds up to two boxes.", FELT);
        ItemStack cloth = new ItemStack(MahjongContent.CLOTH_ITEM);
        MahjongComponents.color(cloth, DyeColor.CYAN);
        scene.overlay().showControls(CONTROLS, Pointing.DOWN, 80).rightClick().withItem(cloth);
        scene.world().modifyBlockEntity(TABLE, MahjongTableBlockEntity.class,
            table -> table.equipment().installCloth(cloth));
        say(scene, "Right-click the table with a cloth in hand to drape it. With the cloth down and a full set ready, you can start preparing a match.", FELT);
        ItemStack stick = new ItemStack(MahjongContent.POINT_STICK);
        MahjongComponents.points(stick, 1000);
        scene.world().modifyBlockEntity(TABLE, MahjongTableBlockEntity.class,
            table -> table.equipment().drawer(0).setItem(0, stick.copy()));
        Vec3 drawer = TableGeometry.world(TABLE, TableGeometry.drawerBounds(0).getCenter());
        scene.overlay().showControls(CONTROLS, Pointing.DOWN, 80).rightClick().withItem(stick);
        say(scene, "Open the side drawer to store every stick denomination and pay players manually during settlement.", drawer);
        say(scene, "While waiting, move boxes in and out through the storage screen. Keep both hands empty and sneak-right-click the tabletop to retrieve the cloth.", FELT);
        scene.markAsFinished();
    }

    static void playing(SceneBuilder scene, SceneBuildingUtil util) {
        base(scene, "table_playing", "Taking your seat");
        scene.world().showSection(util.select().fromTo(1, 1, 1, 5, 1, 5), Direction.DOWN);
        scene.world().modifyBlockEntity(TABLE, MahjongTableBlockEntity.class, table -> {
            table.equipment().installCloth(new ItemStack(MahjongContent.CLOTH_ITEM));
            table.equipment().boxes().setItem(0, MahjongSupplies.completeBox(TileMaterial.BONE, DyeColor.BLUE));
        });
        Vec3 stool = Vec3.atCenterOf(TableGeometry.stool(TABLE, 0));
        scene.overlay().showControls(CONTROLS, Pointing.DOWN, 80).rightClick();
        say(scene, "Right-click a stool to sit down and open the table screen; right-click the stool again while seated to reopen it.", stool);
        say(scene, "Pick a three- or four-player preset in the lobby, then have everyone click Ready.", FELT);
        say(scene, "On ordinary tables, sweep the loose tiles to shuffle, drag stacks in front of you to build the wall, then drag tiles from the wall into your hand.", FELT);
        scene.world().setBlock(TABLE, MahjongContent.AUTO_TABLE.defaultBlockState(), false);
        scene.world().modifyBlockEntity(TABLE, MahjongTableBlockEntity.class, table -> {
            table.equipment().installCloth(new ItemStack(MahjongContent.CLOTH_ITEM));
            table.equipment().boxes().setItem(0, MahjongSupplies.completeBox(TileMaterial.BONE, DyeColor.BLUE));
        });
        say(scene, "Automatic tables shuffle, build walls, deal, and draw all by themselves.", FELT);
        say(scene, "Click a table tile to discard it. The screen lists the chi, pon, kan, riichi, and win options available right now.", FELT);
        say(scene, "Close the screen and you can look around while staying seated; press sneak to stand up.", stool);
        scene.markAsFinished();
    }

    private static void base(SceneBuilder scene, String id, String title) {
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(0.85f);
        scene.showBasePlate();
        scene.idle(15);
    }

    private static void say(SceneBuilder scene, String text, Vec3 target) {
        scene.overlay().showText(TEXT_TIME).text(text).pointAt(target).attachKeyFrame();
        scene.idle(TEXT_TIME + 10);
    }
}
