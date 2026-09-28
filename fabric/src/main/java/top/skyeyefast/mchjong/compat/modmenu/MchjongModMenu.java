package top.skyeyefast.mchjong.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import top.skyeyefast.mchjong.client.PersonalSettingsScreen;

/** Optional Mod Menu entry point; Fabric loads it only when Mod Menu is installed. */
public final class MchjongModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return PersonalSettingsScreen::new;
    }
}
