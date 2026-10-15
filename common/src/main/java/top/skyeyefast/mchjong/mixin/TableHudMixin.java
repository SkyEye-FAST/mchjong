package top.skyeyefast.mchjong.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.skyeyefast.mchjong.client.TableScreens;

/** Hide only the ordinary HUD while the in-world table interaction layer is open. */
@Mixin(Gui.class)
public abstract class TableHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mchjong$tableHud(GuiGraphics graphics, float delta, CallbackInfo callback) {
        var screen = Minecraft.getInstance().screen;
        if (TableScreens.isOpen(screen)) callback.cancel();
    }
}
