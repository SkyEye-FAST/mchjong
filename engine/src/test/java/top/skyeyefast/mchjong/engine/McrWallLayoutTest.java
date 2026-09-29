package top.skyeyefast.mchjong.engine;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McrWallLayoutTest {
    @Test void physicalSlotsAndBothTraversalsAreBijectionsWithUpperFirst() {
        var opening = McrOpening.of(0, new McrOpening.Roll(2, 3), new McrOpening.Roll(1, 3));
        var front = new HashSet<Integer>();
        var tail = new HashSet<Integer>();
        for (int side = 0; side < 4; side++) for (int column = 0; column < 18; column++) {
            int stack = McrWallLayout.stack(side, column);
            assertEquals(side, McrWallLayout.seat(stack));
            assertEquals(column, McrWallLayout.column(stack));
            for (var layer : McrWallLayout.Layer.values()) {
                int slot = McrWallLayout.slot(stack, layer);
                assertEquals(stack, McrWallLayout.stackOfSlot(slot));
                assertEquals(layer, McrWallLayout.layer(slot));
            }
            int offset = stack * 2;
            assertTrue(front.add(McrWallLayout.drawSlot(opening, offset)));
            assertTrue(front.add(McrWallLayout.drawSlot(opening, offset + 1)));
            assertTrue(tail.add(McrWallLayout.replacementSlot(opening, offset)));
            assertTrue(tail.add(McrWallLayout.replacementSlot(opening, offset + 1)));
            assertEquals(McrWallLayout.Layer.UPPER, McrWallLayout.layer(McrWallLayout.drawSlot(opening, offset)));
            assertEquals(McrWallLayout.Layer.UPPER, McrWallLayout.layer(McrWallLayout.replacementSlot(opening, offset)));
        }
        assertEquals(144, front.size());
        assertEquals(front, tail);
        assertEquals(McrWallLayout.stack(3, 0), McrWallLayout.advance(McrWallLayout.stack(0, 17), 1));
        assertEquals(McrWallLayout.stack(1, 17), McrWallLayout.advance(McrWallLayout.stack(0, 0), -1));
    }

    @Test void twoRollsSelectTheRollerAndCountFromTheirRightIncludingWallOverflow() {
        var first = new McrOpening.Roll(1, 1);
        var second = new McrOpening.Roll(1, 2);
        for (int dealer = 0; dealer < 4; dealer++) {
            var opening = McrOpening.of(dealer, first, second);
            assertEquals((dealer + 1) % 4, opening.secondRoller());
            assertEquals(dealer, opening.dealer());
            assertEquals(5, opening.total());
            assertEquals(McrWallLayout.stack((dealer + 1) % 4, 5), opening.breakStack());
        }
        var overflow = McrOpening.of(2, new McrOpening.Roll(6, 6), new McrOpening.Roll(6, 6));
        assertEquals(1, overflow.secondRoller());
        assertEquals(McrWallLayout.stack(0, 6), overflow.breakStack());
        assertThrows(IllegalArgumentException.class, () -> new McrOpening(first, second, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new McrOpening.Roll(0, 6));
        assertEquals(new McrGame(711).save().wall(), new McrGame(711).save().wall());
    }

    @Test void packetsAndFirstThirdJumpLeaveExactFrontAndTailSlotsAcrossRestore() {
        var opening = McrGameTest.OPENING; // Dealer zero, break at column nine of wall zero.
        var wall = new McrWall(Tile.mcrSet(), opening);
        var plan = McrWallLayout.initialDeal(opening);
        assertEquals(53, plan.size());
        assertEquals(53, plan.stream().map(McrWallLayout.Take::slot).distinct().count());
        for (int offset = 0; offset < 48; offset++) {
            var take = plan.get(offset);
            assertEquals(offset % 16 / 4, take.seat());
            if (offset % 2 == 0) assertEquals(McrWallLayout.Layer.UPPER, McrWallLayout.layer(take.slot()));
            else assertEquals(plan.get(offset - 1).slot() + 1, take.slot());
            assertEquals(take.slot(), wall.takeRaw(take.slot()));
        }
        assertEquals(List.of(new McrWallLayout.Take(0, 138), new McrWallLayout.Take(0, 142),
            new McrWallLayout.Take(1, 139), new McrWallLayout.Take(2, 140), new McrWallLayout.Take(3, 141)), plan.subList(48, 53));
        assertEquals(138, wall.takeRaw(138));
        assertEquals(142, wall.takeRaw(142));
        wall = McrWall.restore(wall.save()); // The skipped middle stack has not been taken yet.
        for (int slot : new int[]{139, 140, 141}) assertEquals(slot, wall.drawRaw());
        assertEquals(143, wall.nextDrawSlot());
        assertEquals(16, wall.nextReplacementSlot());
        var player = new McrPlayerState();
        assertEquals(16, wall.replace(player));
        assertEquals(17, wall.replace(player));
        var restored = McrWall.restore(wall.save());
        assertEquals(wall.save(), restored.save());
        assertEquals(143, restored.nextDrawSlot());
        assertEquals(14, restored.nextReplacementSlot());
        assertEquals(wall.drawRaw(), restored.drawRaw());
        assertEquals(wall.replace(player), restored.replace(new McrPlayerState()));
        assertEquals(wall.save(), restored.save());
        var game = McrGameTest.fixed(5, new McrGameTest.Fixture().build());
        assertEquals(game.opening(), game.view(-1).opening());
        assertEquals(game.save().wall(), McrCodec.restore(McrCodec.save(game)).save().wall());
    }
}
