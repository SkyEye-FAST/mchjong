package top.skyeyefast.mchjong.item;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

public final class MahjongBoxItem extends Item {
    public MahjongBoxItem(Properties properties) { super(properties); }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack box = player.getItemInHand(hand);
        if (player.isSpectator() || !MahjongSupplies.validBox(box)) return InteractionResult.FAIL;
        if (player instanceof ServerPlayer server) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : 40;
            server.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new MahjongBoxMenu(id, inventory, slot), getName(box)));
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    // Minecraft 26.1.2 still dispatches item-specific tooltip text through this override.
    @SuppressWarnings("deprecation")
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.mchjong.box_count", MahjongSupplies.tileCount(MahjongSupplies.contents(stack))));
        var preset = stack.get(MahjongComponents.BOX_PRESET);
        if (preset != null) tooltip.accept(Component.translatable("item.mchjong.box_red_fives", Component.translatable(preset.translationKey())));
        tooltip.accept(Component.translatable(MahjongSupplies.deck(stack) == null ? "item.mchjong.box_incomplete" : "item.mchjong.box_ready"));
    }
}
