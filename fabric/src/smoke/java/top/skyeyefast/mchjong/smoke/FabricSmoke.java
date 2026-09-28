package top.skyeyefast.mchjong.smoke;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class FabricSmoke implements ClientModInitializer {
    @Override public void onInitializeClient() {
        if (Boolean.getBoolean("mchjong.smoke.quilt")
            && !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("quilt_loader"))
            throw new IllegalStateException("Quilt verification requires the actual Quilt loader");
        if (Boolean.getBoolean("mchjong.smoke") && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu"))
            ModMenuCheck.verify();
        if (Boolean.getBoolean("mchjong.smoke")) ClientTickEvents.END_CLIENT_TICK.register(new TableClientSmoke()::tick);
    }

    private static final class ModMenuCheck {
        private static void verify() {
            var api = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getEntrypoints("modmenu", com.terraformersmc.modmenu.api.ModMenuApi.class).stream()
                .filter(top.skyeyefast.mchjong.compat.modmenu.MchjongModMenu.class::isInstance)
                .findFirst().orElseThrow();
            if (!(api.getModConfigScreenFactory().create(null)
                    instanceof top.skyeyefast.mchjong.client.PersonalSettingsScreen))
                throw new IllegalStateException("Fabric personal settings screen is missing from Mod Menu");
        }
    }
}
