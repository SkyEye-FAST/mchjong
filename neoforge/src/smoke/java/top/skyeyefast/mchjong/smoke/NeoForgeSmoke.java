package top.skyeyefast.mchjong.smoke;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "mchjong_smoke", dist = Dist.CLIENT)
public final class NeoForgeSmoke {
    public NeoForgeSmoke() {
        if (Boolean.getBoolean("mchjong.smoke")) {
            if (net.neoforged.fml.ModList.get().isLoaded("ponder") != Boolean.getBoolean("mchjong.smoke.ponder"))
                throw new IllegalStateException("Ponder availability differs from the requested smoke configuration");
            var smoke = new TableClientSmoke();
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> smoke.tick(Minecraft.getInstance()));
        }
    }
}
