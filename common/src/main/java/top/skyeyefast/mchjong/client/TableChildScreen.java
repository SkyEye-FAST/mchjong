package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.screens.Screen;

/** Keeps nested table pages attached to the screen that owns their synchronized state. */
interface TableChildScreen {
    Screen parent();

    static Screen root(Screen screen) {
        while (screen instanceof TableChildScreen child) screen = child.parent();
        return screen;
    }
}
