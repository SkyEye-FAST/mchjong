package top.skyeyefast.mchjong.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class MahjongBoxItem extends Item {
    public MahjongBoxItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack box = player.getItemInHand(hand);
        if (player.isSpectator() || !MahjongSupplies.validBox(box)) return InteractionResultHolder.fail(box);
        if (player instanceof ServerPlayer server) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
            server.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new MahjongBoxMenu(id, inventory, slot), getName(box)));
        }
        return InteractionResultHolder.sidedSuccess(box, level.isClientSide);
    }

    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.mchjong.box_count", MahjongSupplies.tileCount(MahjongSupplies.contents(stack))));
        var preset = MahjongComponents.boxPreset(stack);
        if (preset != null) lines.add(Component.translatable("item.mchjong.box_red_fives", Component.translatable(preset.translationKey())));
        lines.add(Component.translatable(MahjongSupplies.deck(stack) == null ? "item.mchjong.box_incomplete" : "item.mchjong.box_ready"));
    }
}
