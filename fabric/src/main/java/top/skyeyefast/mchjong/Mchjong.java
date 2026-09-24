package top.skyeyefast.mchjong;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
import top.skyeyefast.mchjong.network.TableActionPayload;
import top.skyeyefast.mchjong.network.TableControlPayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.TableSeatPayload;
import top.skyeyefast.mchjong.network.TableViewPayload;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

public class Mchjong implements ModInitializer {
    public static final String MOD_ID = "mchjong";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final CreativeModeTab TAB = FabricCreativeModeTab.builder()
        .icon(() -> MahjongSupplies.tile(new TileData(32, TileMaterial.BONE, false), 1))
        .title(Component.translatable("itemGroup.mchjong"))
        .displayItems((params, output) -> top.skyeyefast.mchjong.item.MahjongCatalog.entries().forEach(output::accept))
        .build();

    @Override
    public void onInitialize() {
        top.skyeyefast.mchjong.compat.maid.MaidData.register();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(
            top.skyeyefast.mchjong.world.WorldSettings::of);
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
            FabricBlockEntityTypeBuilder.create(MahjongTableBlockEntity::new, MahjongContent.TABLE, MahjongContent.AUTO_TABLE).build());
        MahjongContent.STOOL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MahjongContent.id("mahjong_stool"),
            FabricBlockEntityTypeBuilder.create(top.skyeyefast.mchjong.world.FurnitureBlockEntity::new, MahjongContent.STOOL).build());
        MahjongContent.SEAT_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, MahjongContent.id("seat"),
            EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC).sized(0.3f, 0.1f)
                .clientTrackingRange(10).updateInterval(10)
                .build(ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, MahjongContent.id("seat"))));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MahjongContent.TAB_KEY, TAB);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            top.skyeyefast.mchjong.item.MahjongCatalog.entries().forEach(entries::accept);
        });
        PayloadTypeRegistry.serverboundPlay().register(TableActionPayload.TYPE, TableActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TableControlPayload.TYPE, TableControlPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TableSeatPayload.TYPE, TableSeatPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(top.skyeyefast.mchjong.network.BoxPrintPayload.TYPE, top.skyeyefast.mchjong.network.BoxPrintPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.BoxPrintPayload.TYPE,
            (payload, context) -> context.server().execute(() -> payload.handle(context.player())));
        PayloadTypeRegistry.serverboundPlay().register(top.skyeyefast.mchjong.network.TableRulesPayload.TYPE, top.skyeyefast.mchjong.network.TableRulesPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(top.skyeyefast.mchjong.network.TableVisibilityPayload.TYPE, top.skyeyefast.mchjong.network.TableVisibilityPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TableViewPayload.TYPE, TableViewPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(top.skyeyefast.mchjong.network.ReplayPayload.TYPE, top.skyeyefast.mchjong.network.ReplayPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TableActionPayload.TYPE,
            (payload, context) -> context.server().execute(() -> TableNetworking.receive(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(TableControlPayload.TYPE,
            (payload, context) -> context.server().execute(() -> TableNetworking.receive(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(TableSeatPayload.TYPE,
            (payload, context) -> context.server().execute(() -> TableNetworking.receive(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.TableRulesPayload.TYPE,
            (payload, context) -> context.server().execute(() -> TableNetworking.receive(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.TableVisibilityPayload.TYPE,
            (payload, context) -> context.server().execute(() -> TableNetworking.receive(context.player(), payload)));
        LOGGER.info("Initializing {} for Fabric", MOD_ID);
    }
}
