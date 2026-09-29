package top.skyeyefast.mchjong;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.network.RiichiActionPayload;
import top.skyeyefast.mchjong.network.RiichiControlPayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.TableSeatPayload;
import top.skyeyefast.mchjong.network.RiichiViewPayload;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

public class Mchjong implements ModInitializer {
    public static final String MOD_ID = "mchjong";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final CreativeModeTab TAB = FabricItemGroup.builder()
        .icon(() -> MahjongSupplies.tile(new TileData(32, TileMaterial.BONE, false), 1))
        .title(Component.translatable("itemGroup.mchjong"))
        .displayItems((params, output) -> top.skyeyefast.mchjong.item.MahjongCatalog.entries().forEach(output::accept))
        .build();

    @Override
    public void onInitialize() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(
            top.skyeyefast.mchjong.config.ServerPresets::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            top.skyeyefast.mchjong.world.WorldSettings.of(server);
            top.skyeyefast.mchjong.config.ServerPresets.load(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
            top.skyeyefast.mchjong.world.BotServiceClient.load(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            top.skyeyefast.mchjong.config.ServerPresets.send(handler.player);
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("patchouli"))
                top.skyeyefast.mchjong.compat.patchouli.ManualGift.give(handler.player);
        });
        top.skyeyefast.mchjong.item.MahjongComponents.TYPES.forEach((name, type) ->
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, MahjongContent.id(name), type));
        top.skyeyefast.mchjong.recipe.MahjongRecipes.SERIALIZERS.forEach((name, serializer) ->
            Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, MahjongContent.id(name), serializer));
        top.skyeyefast.mchjong.world.MahjongSounds.EVENTS.forEach((name, event) ->
            Registry.register(BuiltInRegistries.SOUND_EVENT, MahjongContent.id(name), event));
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            top.skyeyefast.mchjong.world.TableCommands.register(dispatcher));
        Registry.register(BuiltInRegistries.BLOCK, MahjongContent.id("mahjong_table"), MahjongContent.TABLE);
        Registry.register(BuiltInRegistries.BLOCK, MahjongContent.id("automatic_mahjong_table"), MahjongContent.AUTO_TABLE);
        Registry.register(BuiltInRegistries.BLOCK, MahjongContent.id("table_space"), MahjongContent.SPACE);
        Registry.register(BuiltInRegistries.BLOCK, MahjongContent.id("mahjong_stool"), MahjongContent.STOOL);
        Registry.register(BuiltInRegistries.ITEM, MahjongContent.id("mahjong_table"), MahjongContent.TABLE_ITEM);
        Registry.register(BuiltInRegistries.ITEM, MahjongContent.id("automatic_mahjong_table"), MahjongContent.AUTO_TABLE_ITEM);
        MahjongContent.SUPPLIES.forEach((name, item) -> Registry.register(BuiltInRegistries.ITEM, MahjongContent.id(name), item));
        Registry.register(BuiltInRegistries.ITEM, MahjongContent.id("mahjong_stool"), MahjongContent.STOOL_ITEM);
        Registry.register(BuiltInRegistries.MENU, MahjongContent.id("mahjong_box"), MahjongContent.BOX_MENU);
        Registry.register(BuiltInRegistries.MENU, MahjongContent.id("mahjong_table"), MahjongContent.TABLE_MENU);
        Registry.register(BuiltInRegistries.MENU, MahjongContent.id("point_sticks"), MahjongContent.STICK_MENU);
        MahjongContent.TABLE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MahjongContent.id("mahjong_table"),
            BlockEntityType.Builder.of(MahjongTableBlockEntity::new, MahjongContent.TABLE, MahjongContent.AUTO_TABLE).build(null));
        MahjongContent.STOOL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MahjongContent.id("mahjong_stool"),
            BlockEntityType.Builder.of(top.skyeyefast.mchjong.world.FurnitureBlockEntity::new, MahjongContent.STOOL).build(null));
        MahjongContent.SEAT_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, MahjongContent.id("seat"),
            EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC).sized(0.3f, 0.1f)
                .clientTrackingRange(10).updateInterval(10).build("mchjong:seat"));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MahjongContent.TAB_KEY, TAB);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            top.skyeyefast.mchjong.item.MahjongCatalog.entries().forEach(entries::accept);
        });
        PayloadTypeRegistry.playC2S().register(RiichiActionPayload.TYPE, RiichiActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(top.skyeyefast.mchjong.network.TableRoomActionPayload.TYPE,
            top.skyeyefast.mchjong.network.TableRoomActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(top.skyeyefast.mchjong.network.McrNextHandPayload.TYPE,
            top.skyeyefast.mchjong.network.McrNextHandPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(top.skyeyefast.mchjong.network.McrActionPayload.TYPE,
            top.skyeyefast.mchjong.network.McrActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(top.skyeyefast.mchjong.network.TableVariantPayload.TYPE,
            top.skyeyefast.mchjong.network.TableVariantPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(top.skyeyefast.mchjong.network.VoiceChoicePayload.TYPE, top.skyeyefast.mchjong.network.VoiceChoicePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.VoiceChoicePayload.TYPE,
            (payload, context) -> context.server().execute(() -> payload.handle(context.player())));
        PayloadTypeRegistry.playS2C().register(top.skyeyefast.mchjong.network.VoiceAppearancePayload.TYPE, top.skyeyefast.mchjong.network.VoiceAppearancePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RiichiControlPayload.TYPE, RiichiControlPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(top.skyeyefast.mchjong.network.TableSessionControlPayload.TYPE,
            top.skyeyefast.mchjong.network.TableSessionControlPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(top.skyeyefast.mchjong.network.RiichiHandOrderPayload.TYPE,
            top.skyeyefast.mchjong.network.RiichiHandOrderPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TableSeatPayload.TYPE, TableSeatPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(top.skyeyefast.mchjong.network.BoxPrintPayload.TYPE, top.skyeyefast.mchjong.network.BoxPrintPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.BoxPrintPayload.TYPE,
            (payload, context) -> context.server().execute(() -> payload.handle(context.player())));
        PayloadTypeRegistry.playC2S().register(top.skyeyefast.mchjong.network.StickChoicePayload.TYPE, top.skyeyefast.mchjong.network.StickChoicePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.StickChoicePayload.TYPE,
            (payload, context) -> context.server().execute(() -> payload.handle(context.player())));
        PayloadTypeRegistry.playC2S().register(top.skyeyefast.mchjong.network.RiichiRulesPayload.TYPE, top.skyeyefast.mchjong.network.RiichiRulesPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(top.skyeyefast.mchjong.network.RiichiVisibilityPayload.TYPE, top.skyeyefast.mchjong.network.RiichiVisibilityPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RiichiViewPayload.TYPE, RiichiViewPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(top.skyeyefast.mchjong.network.McrViewPayload.TYPE,
            top.skyeyefast.mchjong.network.McrViewPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(top.skyeyefast.mchjong.network.ReplayPayload.TYPE, top.skyeyefast.mchjong.network.ReplayPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(top.skyeyefast.mchjong.network.PresetBundlePayload.TYPE, top.skyeyefast.mchjong.network.PresetBundlePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(top.skyeyefast.mchjong.network.StickAppearancePayload.TYPE, top.skyeyefast.mchjong.network.StickAppearancePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RiichiActionPayload.TYPE,
            (payload, context) -> context.server().execute(() -> TableNetworking.receive(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.TableRoomActionPayload.TYPE,
            (payload, context) -> context.server().execute(() -> TableNetworking.receive(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.McrNextHandPayload.TYPE,
            (payload, context) -> context.server().execute(() -> TableNetworking.receive(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.McrActionPayload.TYPE,
            (payload, context) -> context.server().execute(() -> TableNetworking.receive(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.TableVariantPayload.TYPE,
            (payload, context) -> context.server().execute(() -> TableNetworking.receive(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(RiichiControlPayload.TYPE,
            (payload, context) -> context.server().execute(() -> TableNetworking.receive(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.TableSessionControlPayload.TYPE,
            (payload, context) -> context.server().execute(() -> TableNetworking.receive(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.RiichiHandOrderPayload.TYPE,
            (payload, context) -> context.server().execute(() -> TableNetworking.receive(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(TableSeatPayload.TYPE,
            (payload, context) -> context.server().execute(() -> TableNetworking.receive(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.RiichiRulesPayload.TYPE,
            (payload, context) -> context.server().execute(() -> TableNetworking.receive(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.RiichiVisibilityPayload.TYPE,
            (payload, context) -> context.server().execute(() -> TableNetworking.receive(context.player(), payload)));
        LOGGER.info("Initializing {} for Fabric", MOD_ID);
    }
}
