package top.skyeyefast.mchjong.compat.ponder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
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
    private static final int TEXT_TIME = 100;

    private MahjongScenes() {}

    static void placement(SceneBuilder scene, SceneBuildingUtil util) {
        base(scene, "table_placement", "Placing a mahjong table");
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "footprint",
            new AABB(2, 1, 2, 5, 2, 5), TEXT_TIME);
        say(scene, "Leave a clear 3 x 3 area for the table, with room above its playing surface.", FELT);
        scene.world().showSection(util.select().position(TABLE), Direction.DOWN);
        say(scene, "Place the table at the center of this area. Its frame fills the surrounding space.", FELT);
        for (int seat = 0; seat < 4; seat++) {
            scene.world().showSection(util.select().position(TableGeometry.stool(TABLE, seat)), Direction.DOWN);
            scene.idle(8);
        }
        say(scene, "Place stools two blocks from the center, on the sides used by your game.",
            Vec3.atCenterOf(TableGeometry.stool(TABLE, 0)));
        scene.rotateCameraY(90);
        say(scene, "Both ordinary and automatic tables use this compact arrangement.", FELT);
        scene.markAsFinished();
    }

    static void equipment(SceneBuilder scene, SceneBuildingUtil util) {
        base(scene, "table_equipment", "Preparing the table");
        scene.world().showSection(util.select().fromTo(1, 1, 1, 5, 1, 5), Direction.DOWN);
        ItemStack box = MahjongSupplies.completeBox(TileMaterial.BONE, DyeColor.BLUE);
        scene.overlay().showControls(FELT, Pointing.DOWN, 80).rightClick().withItem(box);
        say(scene, "Prepare a complete 136-tile set in a mahjong box, with matching material and back color.", FELT);
        scene.world().modifyBlockEntity(TABLE, MahjongTableBlockEntity.class,
            table -> table.equipment().boxes().setItem(0, box.copy()));
        scene.overlay().showControls(FELT, Pointing.DOWN, 80).rightClick();
        say(scene, "Right-click the table to open its storage. It holds up to two mahjong boxes inside.", FELT);
        ItemStack cloth = new ItemStack(MahjongContent.CLOTH_ITEM);
        cloth.set(DataComponents.BASE_COLOR, DyeColor.GREEN);
        scene.overlay().showControls(FELT, Pointing.DOWN, 80).rightClick().withItem(cloth);
        scene.world().modifyBlockEntity(TABLE, MahjongTableBlockEntity.class,
            table -> table.equipment().installCloth(cloth));
        say(scene, "Use a cloth on the table. A cloth and a complete set make the table ready for play.", FELT);
        ItemStack stick = new ItemStack(MahjongContent.POINT_STICK);
        stick.set(MahjongComponents.POINTS, 1000);
        scene.world().modifyBlockEntity(TABLE, MahjongTableBlockEntity.class,
            table -> table.equipment().placeStick(0, stick));
        scene.overlay().showControls(FELT.add(0.9, 0, 1.2), Pointing.DOWN, 80).rightClick().withItem(stick);
        say(scene, "Point sticks can be placed on your side. Flower and season tiles stay in the box during riichi games.", FELT);
        say(scene, "Manage boxes through table storage in the lobby. Crouch-click with empty hands to collect placed equipment.", FELT);
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
        scene.overlay().showControls(stool, Pointing.DOWN, 80).rightClick();
        say(scene, "Click a stool to sit and open the table controls. Click it again to reopen the controls while seated.", stool);
        say(scene, "Choose three- or four-player mahjong and a rule preset in the lobby, then have every player ready up.", FELT);
        say(scene, "On an ordinary table, use the controls to shuffle, build your wall, take starting packets and draw tiles.", FELT);
        scene.world().setBlock(TABLE, MahjongContent.AUTO_TABLE.defaultBlockState(), false);
        scene.world().modifyBlockEntity(TABLE, MahjongTableBlockEntity.class, table -> {
            table.equipment().installCloth(new ItemStack(MahjongContent.CLOTH_ITEM));
            table.equipment().boxes().setItem(0, MahjongSupplies.completeBox(TileMaterial.BONE, DyeColor.BLUE));
        });
        say(scene, "An automatic table handles shuffling, wall construction, dealing and drawing for you.", FELT);
        say(scene, "Select tiles on the table to discard. The controls offer legal calls, riichi and winning actions.", FELT);
        say(scene, "Close the overlay to look around while seated. Use Minecraft's sneak control after closing it to dismount.", stool);
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
        scene.overlay().showText(TEXT_TIME).text(text).pointAt(target).placeNearTarget().attachKeyFrame();
        scene.idle(TEXT_TIME + 10);
    }
}
