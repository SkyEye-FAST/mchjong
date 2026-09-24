package top.skyeyefast.mchjong.smoke;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class FabricSmoke implements ClientModInitializer {
    @Override public void onInitializeClient() {
        boolean expectPonder = Boolean.getBoolean("mchjong.smoke.ponder")
            || net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("create");
        if (Boolean.getBoolean("mchjong.smoke") && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ponder")
                != expectPonder)
            throw new IllegalStateException("Ponder availability differs from the requested smoke configuration");
        if (Boolean.getBoolean("mchjong.smoke")) ClientTickEvents.END_CLIENT_TICK.register(new TableClientSmoke()::tick);
    }
}
