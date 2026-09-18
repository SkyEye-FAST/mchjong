package top.skyeyefast.mchjong.mixin;

import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.item.MahjongSupplyItem;
import top.skyeyefast.mchjong.item.TileData;

/** Keep client settings out of the dedicated-server item class. */
@Mixin(value = MahjongSupplyItem.class, remap = false)
public abstract class TileTooltipMixin {
    @Inject(method = "tileLabel", at = @At("HEAD"), cancellable = true)
    private static void mchjong$tileLabel(TileData tile, CallbackInfoReturnable<Component> callback) {
        callback.setReturnValue(tile.label(TableSettings.get().tileLabels == TableSettings.TileLabels.MPSZ));
    }
}
