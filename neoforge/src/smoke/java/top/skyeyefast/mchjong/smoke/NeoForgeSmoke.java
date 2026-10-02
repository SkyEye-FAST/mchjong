package top.skyeyefast.mchjong.smoke;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "mchjong_smoke", dist = Dist.CLIENT)
public final class NeoForgeSmoke {
    private static boolean checkedConfigScreen;

    public NeoForgeSmoke(net.neoforged.bus.api.IEventBus bus) {
        bus.addListener((net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent event) ->
            event.register(SmokeMesh.State.class, SmokeMesh.Renderer::new));
        if (Boolean.getBoolean("mchjong.smoke")) {
            var smoke = new TableClientSmoke();
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
                if (!checkedConfigScreen) {
                    var container = net.neoforged.fml.ModList.get().getModContainerById("mchjong").orElseThrow();
                    var factory = net.neoforged.neoforge.client.gui.IConfigScreenFactory.getForMod(container.getModInfo()).orElseThrow();
                    if (!(factory.createScreen(container, Minecraft.getInstance().screen)
                            instanceof top.skyeyefast.mchjong.client.PersonalSettingsScreen))
                        throw new IllegalStateException("NeoForge personal settings screen is missing from the mod list");
                    checkedConfigScreen = true;
                }
                smoke.tick(Minecraft.getInstance());
            });
        }
    }
}
