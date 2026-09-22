package top.skyeyefast.mchjong.forge.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.skyeyefast.mchjong.client.MahjongItemRenderer;
import top.skyeyefast.mchjong.client.RiichiStickModel;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Loader bootstrap boundary only; shared domain tests remain in their owning suites. */
@Mod("mchjong_smoke")
@Mod.EventBusSubscriber(modid = "mchjong_smoke", value = Dist.CLIENT)
public final class ForgeClientBootstrap {
    private static boolean complete;
    private static int titleTicks;

    public ForgeClientBootstrap() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("mchjong.smoke.bootstrap") || complete) return;
        var client = Minecraft.getInstance();
        if (client.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding) {
            onboarding.onClose();
            return;
        }
        if (!(client.screen instanceof TitleScreen) || client.getOverlay() != null) return;
        if (++titleTicks < 60) return;
        if (BuiltInRegistries.BLOCK.get(MahjongContent.id("mahjong_table")) != MahjongContent.TABLE)
            throw new IllegalStateException("Forge table registration is missing");
        for (var item : MahjongItemRenderer.items())
            if (!(IClientItemExtensions.of(item).getCustomRenderer() instanceof MahjongItemRenderer))
                throw new IllegalStateException("Forge item renderer is missing for " + BuiltInRegistries.ITEM.getKey(item));
        var models = client.getModelManager();
        if (models.getModel(new ModelResourceLocation(RiichiStickModel.ID, "standalone")) == models.getMissingModel())
            throw new IllegalStateException("Forge additional riichi-stick model is missing");
        Path output = Path.of(System.getProperty("mchjong.smoke.output"));
        Files.createDirectories(output);
        Screenshot.grab(output.toFile(), "forge-bootstrap.png", client.getMainRenderTarget(), message -> {});
        Files.writeString(output.resolve("PASS.txt"), "Forge client registrations, renderer bindings and additional model loaded.\n");
        complete = true;
        client.stop();
    }
}
