package top.skyeyefast.mchjong.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.skyeyefast.mchjong.client.TableScreen;

/** Hide only the ordinary HUD while the in-world table interaction layer is open. */
@Mixin(Gui.class)
public abstract class TableHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mchjong$tableHud(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        if (TableScreen.active(Minecraft.getInstance().screen) != null) callback.cancel();
    }
}
