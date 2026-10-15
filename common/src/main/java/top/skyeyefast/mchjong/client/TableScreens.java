package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.screens.Screen;

/** Identifies the table interaction screens shared by all variants. */
public final class TableScreens {
    private TableScreens() {}

    public static boolean isOpen(Screen screen) {
        return RiichiTableScreen.active(screen) != null || McrTableScreen.isOpen(screen)
            || SichuanTableScreen.isOpen(screen) || TaiwanTableScreen.isOpen(screen);
    }
}
