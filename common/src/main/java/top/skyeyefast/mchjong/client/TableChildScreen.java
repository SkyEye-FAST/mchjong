package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.screens.Screen;

/** Keeps nested table pages attached to the screen that owns their synchronized state. */
interface TableChildScreen {
    Screen parent();

    static Screen root(Screen screen) {
        Screen parent;
        while ((parent = parentOf(screen)) != null) screen = parent;
        return screen;
    }
    static <S extends Screen> S find(Screen screen, Class<S> type) {
        while (screen != null) {
            if (type.isInstance(screen)) return type.cast(screen);
            screen = parentOf(screen);
        }
        return null;
    }
    private static Screen parentOf(Screen screen) {
        return screen instanceof TableChildScreen child ? child.parent() : RuleHelp.manualParent(screen);
    }
}
