package top.skyeyefast.mchjong.smoke;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class FabricSmoke implements ClientModInitializer {
    @Override public void onInitializeClient() {
        if (Boolean.getBoolean("mchjong.smoke.quilt")
            && !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("quilt_loader"))
            throw new IllegalStateException("Quilt verification requires the actual Quilt loader");
        if (Boolean.getBoolean("mchjong.smoke")) ClientTickEvents.END_CLIENT_TICK.register(new TableClientSmoke()::tick);
    }
}
