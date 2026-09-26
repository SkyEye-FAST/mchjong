package top.skyeyefast.mchjong.forge.smoke;

import top.skyeyefast.mchjong.platform.ItemRegistry;
import top.skyeyefast.mchjong.platform.ResourceIds;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import top.skyeyefast.mchjong.smoke.SmokeScreenshots;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.registries.ForgeRegistries;
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

    public ForgeClientBootstrap() {
        if (Boolean.getBoolean("mchjong.smoke")) {
            boolean expectPonder = Boolean.getBoolean("mchjong.smoke.ponder") || net.minecraftforge.fml.ModList.get().isLoaded("create");
            if (net.minecraftforge.fml.ModList.get().isLoaded("ponder") != expectPonder)
                throw new IllegalStateException("Ponder availability differs from the requested smoke configuration");
            var smoke = new top.skyeyefast.mchjong.smoke.TableClientSmoke();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase == TickEvent.Phase.END) smoke.tick(Minecraft.getInstance());
            });
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (event.phase != TickEvent.Phase.END) return;
        if (!Boolean.getBoolean("mchjong.smoke.bootstrap") || complete) return;
        var client = Minecraft.getInstance();
        if (client.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding) {
            onboarding.onClose();
            return;
        }
        if (!(client.screen instanceof TitleScreen) || client.getOverlay() != null) return;
        if (++titleTicks < 60) return;
        if (ForgeRegistries.BLOCKS.getValue(MahjongContent.id("mahjong_table")) != MahjongContent.TABLE)
            throw new IllegalStateException("Forge table registration is missing");
        for (var item : MahjongItemRenderer.items())
            if (!(IClientItemExtensions.of(item).getCustomRenderer() instanceof MahjongItemRenderer))
                throw new IllegalStateException("Forge item renderer is missing for " + ItemRegistry.getKey(item));
        var models = client.getModelManager();
        if (models.getModel(RiichiStickModel.ID) == models.getMissingModel())
            throw new IllegalStateException("Forge additional riichi-stick model is missing");
        Path output = Path.of(System.getProperty("mchjong.smoke.output"));
        verifyNativeStackData();
        Files.createDirectories(output);
        SmokeScreenshots.grab(output.toFile(), "forge-bootstrap.png", client.getMainRenderTarget(), message -> {});
        Files.writeString(output.resolve("PASS.txt"), "Forge client registrations, renderer bindings and additional model loaded.\n");
        complete = true;
        client.stop();
    }

    private static void verifyNativeStackData() throws Exception {
        var plain = new net.minecraft.world.item.ItemStack(MahjongContent.TABLE_ITEM);
        var explicit = plain.copy();
        top.skyeyefast.mchjong.item.MahjongComponents.wood(explicit, top.skyeyefast.mchjong.item.FurnitureWood.OAK);
        if (!net.minecraft.world.item.ItemStack.isSameItemSameTags(plain, explicit))
            throw new IllegalStateException("Default wood changes stack identity");
        var saved = plain.save(new net.minecraft.nbt.CompoundTag());
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.put("mchjong", new net.minecraft.nbt.CompoundTag());
        tag.getCompound("mchjong").putString("wood", "oak");
        saved.put("tag", tag);
        if (!net.minecraft.world.item.ItemStack.isSameItemSameTags(plain, net.minecraft.world.item.ItemStack.of(saved)))
            throw new IllegalStateException("Saved default wood changes stack identity");
        for (String name : new String[] {"mahjong_table_cherry", "blanks_quartz"}) {
            try (var input = MahjongContent.class.getResourceAsStream("/data/mchjong/recipes/" + name + ".json")) {
                if (input == null) throw new IllegalStateException("Missing generated recipe " + name);
                var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var serializer = ForgeRegistries.RECIPE_SERIALIZERS.getValue(ResourceIds.of(json.get("type").getAsString()));
                var result = serializer.fromJson(MahjongContent.id(name), json).getResultItem(net.minecraft.core.RegistryAccess.EMPTY);
                boolean valid = name.equals("mahjong_table_cherry")
                    ? top.skyeyefast.mchjong.item.MahjongComponents.wood(result) == top.skyeyefast.mchjong.item.FurnitureWood.CHERRY
                    : top.skyeyefast.mchjong.item.MahjongComponents.tile(result).material() == top.skyeyefast.mchjong.item.TileMaterial.QUARTZ;
                if (!valid) throw new IllegalStateException("Recipe result lost native NBT: " + name);
            }
        }
        var box = new net.minecraft.world.item.ItemStack(MahjongContent.BOX_ITEM);
        var items = new net.minecraft.nbt.ListTag();
        items.add(new net.minecraft.world.item.ItemStack(MahjongContent.TILE_ITEM).save(new net.minecraft.nbt.CompoundTag()));
        box.getOrCreateTagElement("mchjong").put("Items", items);
        if (top.skyeyefast.mchjong.item.MahjongSupplies.validBox(box))
            throw new IllegalStateException("Missing container slot accepted");
    }
}
