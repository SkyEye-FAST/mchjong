package top.skyeyefast.mchjong;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mchjong implements ModInitializer {
    public static final String MOD_ID = "mchjong";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing {} for Fabric 1.21.1", MOD_ID);
    }
}
