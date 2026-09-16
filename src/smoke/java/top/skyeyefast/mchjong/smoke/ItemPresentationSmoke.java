package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Native first-person rendering, drop packets and synchronized dropped-item components. */
final class ItemPresentationSmoke {
    private static final String[] NAMES = {"ordinary-table", "automatic-table", "cloth", "glass-tile", "point-stick", "stool", "flower-winter"};
    private int sample, ticks, count;
    private ItemStack expected = ItemStack.EMPTY;
    private final List<UUID> dropped = new ArrayList<>();
    private CompletableFuture<Boolean> cleanup;
    private net.minecraft.world.entity.HumanoidArm originalArm;

    boolean tick(Minecraft client, Path output) {
        if (sample == NAMES.length) {
            if (cleanup == null) {
                var server = client.getSingleplayerServer();
                var ids = List.copyOf(dropped);
                cleanup = server.submit(() -> {
                    for (UUID id : ids) {
                        var entity = server.overworld().getEntity(id);
                        if (entity instanceof ItemEntity) entity.discard();
                    }
                    return true;
                });
            }
            if (!cleanup.isDone()) return false;
            cleanup.join();
            if (client.level.getEntitiesOfClass(ItemEntity.class, client.player.getBoundingBox().inflate(6)).stream()
                    .anyMatch(entity -> dropped.contains(entity.getUUID()))) {
                check(++ticks < 60, "Item presentation fixtures were not removed before the table screenshots");
                return false;
            }
            client.player.getInventory().selected = 0;
            client.options.mainHand().set(originalArm);
            return true;
        }
        if (ticks == 0) {
            if (originalArm == null) originalArm = client.options.mainHand().get();
            client.options.mainHand().set(net.minecraft.world.entity.HumanoidArm.RIGHT);
            client.player.getInventory().selected = sample + 1;
            var stack = client.player.getMainHandItem();
            check(!stack.isEmpty(), "Missing item presentation fixture: " + NAMES[sample]);
            expected = stack.copyWithCount(1);
            count = stack.getCount();
        }
        ticks++;
        if (ticks == 20) {
            check(ItemStack.isSameItemSameComponents(expected, client.player.getMainHandItem()), "Held item components changed");
            Screenshot.grab(output.toFile(), "07-held-" + NAMES[sample] + ".png", client.getMainRenderTarget(), ignored -> {});
            client.options.mainHand().set(net.minecraft.world.entity.HumanoidArm.LEFT);
        } else if (ticks == 30) {
            Screenshot.grab(output.toFile(), "07-held-" + NAMES[sample] + "-left.png", client.getMainRenderTarget(), ignored -> {});
            client.options.mainHand().set(net.minecraft.world.entity.HumanoidArm.RIGHT);
        } else if (ticks == 40) {
            // This is the real client's Q-key path, not a display-only spawned item.
            check(client.player.drop(false), "Native drop action did not remove the held item");
        } else if (ticks >= 55) {
            var drops = client.level.getEntitiesOfClass(ItemEntity.class, client.player.getBoundingBox().inflate(6));
            int matching = drops.stream().map(ItemEntity::getItem)
                .filter(stack -> ItemStack.isSameItemSameComponents(expected, stack)).mapToInt(ItemStack::getCount).sum();
            if (matching != 1) {
                check(ticks < 75, "Dropped item or its appearance components did not reach the client: " + NAMES[sample]);
                return false;
            }
            check(client.player.getInventory().getItem(sample + 1).getCount() == count - 1,
                "Dropping an item did not conserve the inventory count");
            Screenshot.grab(output.toFile(), "08-dropped-" + NAMES[sample] + ".png", client.getMainRenderTarget(), ignored -> {});
            drops.stream().filter(entity -> ItemStack.isSameItemSameComponents(expected, entity.getItem()))
                .map(ItemEntity::getUUID).forEach(dropped::add);
            sample++;
            ticks = 0;
        }
        return false;
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
