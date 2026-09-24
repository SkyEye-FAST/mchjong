package top.skyeyefast.mchjong.neo.compat;

import me.shedaniel.rei.forge.REIPluginCommon;
import top.skyeyefast.mchjong.compat.rei.MahjongReiCommonPlugin;

/** NeoForge discovers common REI plugins by annotation; Fabric uses the rei_common entrypoint. */
@REIPluginCommon
public final class MahjongReiCommonPluginNeoForge extends MahjongReiCommonPlugin {}
