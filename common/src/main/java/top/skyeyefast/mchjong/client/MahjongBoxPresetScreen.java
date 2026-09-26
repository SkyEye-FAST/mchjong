package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Preset pages retain the same authorized box menu until the player leaves it. */
abstract class MahjongBoxPresetScreen extends Screen {
    protected final MahjongBoxScreen parent;
    private boolean returning;

    MahjongBoxPresetScreen(Component title, MahjongBoxScreen parent) {
        super(title);
        this.parent = parent;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int x, int y, float partialTick) {}

    @Override public void tick() {
        if (minecraft.player == null || minecraft.player.containerMenu != parent.boxMenu()
                || !minecraft.player.isAlive()) minecraft.setScreen(null);
    }

    @Override public void onClose() {
        returning = minecraft.player != null && minecraft.player.containerMenu == parent.boxMenu();
        minecraft.setScreen(returning ? parent : null);
    }

    @Override public void removed() {
        if (!returning) parent.removed();
    }
}
