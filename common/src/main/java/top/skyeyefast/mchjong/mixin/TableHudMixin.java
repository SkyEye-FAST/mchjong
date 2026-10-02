package top.skyeyefast.mchjong.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.skyeyefast.mchjong.client.RiichiTableScreen;

/** Hide only the ordinary HUD while the in-world table interaction layer is open. */
@Mixin(Gui.class)
public abstract class TableHudMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void mchjong$tableHud(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        var screen = Minecraft.getInstance().screen;
        if (RiichiTableScreen.active(screen) != null || top.skyeyefast.mchjong.client.McrTableScreen.isOpen(screen)
            || top.skyeyefast.mchjong.client.SichuanTableScreen.isOpen(screen)) callback.cancel();
    }
}
