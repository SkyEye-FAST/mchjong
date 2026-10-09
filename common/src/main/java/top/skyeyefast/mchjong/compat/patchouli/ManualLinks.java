package top.skyeyefast.mchjong.compat.patchouli;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import vazkii.patchouli.api.PatchouliAPI;
import vazkii.patchouli.client.book.gui.GuiBook;

/** Loaded only after the loader has confirmed Patchouli is installed. */
final class ManualLinks {
    private ManualLinks() {}
    static void open(String entry, int page) {
        PatchouliAPI.get().openBookEntry(new ResourceLocation("mchjong:guide"), new ResourceLocation("mchjong:" + entry), page);
    }
    static boolean isOpen(Screen screen) { return screen instanceof GuiBook; }
}
