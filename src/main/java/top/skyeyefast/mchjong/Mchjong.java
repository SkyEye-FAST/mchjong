package top.skyeyefast.mchjong;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.skyeyefast.mchjong.network.TableActionPayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.TableViewPayload;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

public class Mchjong implements ModInitializer {
    public static final String MOD_ID = "mchjong";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.BLOCK, MahjongContent.id("mahjong_table"), MahjongContent.TABLE);
        Registry.register(BuiltInRegistries.BLOCK, MahjongContent.id("table_space"), MahjongContent.SPACE);
        Registry.register(BuiltInRegistries.BLOCK, MahjongContent.id("mahjong_stool"), MahjongContent.STOOL);
        Registry.register(BuiltInRegistries.ITEM, MahjongContent.id("mahjong_table"), MahjongContent.TABLE_ITEM);
        Registry.register(BuiltInRegistries.ITEM, MahjongContent.id("mahjong_stool"), MahjongContent.STOOL_ITEM);
        MahjongContent.TABLE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MahjongContent.id("mahjong_table"),
            BlockEntityType.Builder.of(MahjongTableBlockEntity::new, MahjongContent.TABLE).build(null));
        MahjongContent.SEAT_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, MahjongContent.id("seat"),
            EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC).sized(0.3f, 0.1f).noSave()
                .clientTrackingRange(10).updateInterval(10).build("mchjong:seat"));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            entries.accept(MahjongContent.TABLE_ITEM); entries.accept(MahjongContent.STOOL_ITEM);
        });
        PayloadTypeRegistry.playC2S().register(TableActionPayload.TYPE, TableActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TableViewPayload.TYPE, TableViewPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TableActionPayload.TYPE,
            (payload, context) -> context.server().execute(() -> TableNetworking.receive(context.player(), payload)));
        LOGGER.info("Initializing {} for Fabric", MOD_ID);
    }
}
