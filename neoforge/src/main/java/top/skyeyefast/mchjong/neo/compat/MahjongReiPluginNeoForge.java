package top.skyeyefast.mchjong.neo.compat;

import me.shedaniel.rei.forge.REIPluginClient;
import top.skyeyefast.mchjong.compat.rei.MahjongReiPlugin;

/** NeoForge discovers REI plugins by annotation; Fabric uses the rei_client entrypoint. */
@REIPluginClient
public final class MahjongReiPluginNeoForge extends MahjongReiPlugin {}
