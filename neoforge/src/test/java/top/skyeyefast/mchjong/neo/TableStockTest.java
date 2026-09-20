package top.skyeyefast.mchjong.neo;

import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import top.skyeyefast.mchjong.engine.RuleOption;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.TableEquipment;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class TableStockTest {
    @Test void fixedKitsTopUpAtomicallyAndRestoreToDrawers(MinecraftServer server) {
        assertEquals(Map.of(100, 10, 1000, 4, 5000, 2, 10000, 1), TableEquipment.startingKit(25000));
        assertEquals(Map.of(100, 10, 1000, 4, 5000, 3, 10000, 1), TableEquipment.startingKit(30000));
        assertEquals(Map.of(100, 10, 1000, 4, 5000, 2, 10000, 2), TableEquipment.startingKit(35000));
        var table = new TableEquipment(() -> {});
        table.selectRules(RuleSet.MAHJONG_SOUL_4.config().with(RuleOption.BANKRUPTCY, 0));
        var box = stock(15, 4);
        table.boxes().setItem(0, box);
        assertFalse(table.prepareMatch(), "Extra large sticks cannot replace missing thousand-point sticks");
        assertTrue(ItemStack.matches(box, table.boxes().getItem(0)));
        for (int seat = 0; seat < 4; seat++) assertTrue(table.drawer(seat).isEmpty());
        table.boxes().setItem(0, stock(16, 4));
        assertTrue(table.manualSuppliesReady());
        assertTrue(table.prepareMatch());
        for (int seat = 0; seat < 4; seat++) {
            var row = table.drawer(seat);
            for (var entry : TableEquipment.startingKit(25000).entrySet()) {
                int count = 0;
                for (int slot = 0; slot < TableEquipment.BUST_SLOT; slot++)
                    if (row.getItem(slot).getOrDefault(MahjongComponents.POINTS, 0).equals(entry.getKey())) count += row.getItem(slot).getCount();
                assertEquals(entry.getValue().intValue(), count);
            }
            assertEquals(-10000, row.getItem(TableEquipment.BUST_SLOT).get(MahjongComponents.POINTS));
        }
        var paid = table.drawer(0).removeItem(1, 1);
        table.drawer(1).setItem(8, paid);
        table.endMatch();
        assertEquals(4, table.drawer(0).getItem(1).getCount());
        assertTrue(table.drawer(1).getItem(8).isEmpty());
        assertEquals(2, MahjongSupplies.contents(table.boxes().getItem(0)).get(MahjongSupplies.DICE_SLOT).getCount());
    }

    @Test void onlyRulesWithoutBankruptcyRequireBustReserves(MinecraftServer server) {
        var table = new TableEquipment(() -> {});
        table.boxes().setItem(0, stock(16, 0));
        table.selectRules(RuleSet.MAHJONG_SOUL_4.config().with(RuleOption.BANKRUPTCY, 1));
        assertTrue(table.manualSuppliesReady());
        table.selectRules(RuleSet.MAHJONG_SOUL_4.config().with(RuleOption.BANKRUPTCY, 0));
        assertFalse(table.manualSuppliesReady());
    }

    private static ItemStack stock(int thousands, int busts) {
        var box = MahjongSupplies.completeBox(TileMaterial.BONE, DyeColor.BLUE);
        var contents = MahjongSupplies.contents(box);
        int slot = MahjongSupplies.TILE_SLOTS;
        for (int[] supply : new int[][]{{100, 40}, {1000, thousands}, {5000, 8}, {10000, 4}, {-10000, busts}}) {
            var stick = new ItemStack(MahjongContent.POINT_STICK, supply[1]);
            stick.set(MahjongComponents.POINTS, supply[0]);
            contents.set(slot++, stick);
        }
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        return box;
    }
}
