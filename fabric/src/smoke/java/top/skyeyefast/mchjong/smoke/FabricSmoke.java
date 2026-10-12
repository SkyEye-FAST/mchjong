package top.skyeyefast.mchjong.smoke;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs native inventory and equipment checks once in an isolated world. */
public final class FabricSmoke implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("mchjong-smoke");
    private Path output;
    private boolean started, finished;
    private int ticks;
    private CompletableFuture<Boolean> checks;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("mchjong.smoke")) return;
        if (Boolean.getBoolean("mchjong.smoke.patchouliOnly")) {
            ClientTickEvents.END_CLIENT_TICK.register(new TableClientSmoke()::tick);
            return;
        }
        output = Path.of(System.getProperty("mchjong.smoke.output"));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (finished) return;
        try {
            if (++ticks > 2400) throw new IllegalStateException("Inventory checks timed out");
            if (!started && client.screen instanceof AccessibilityOnboardingScreen onboarding) {
                onboarding.onClose();
            } else if (!started && client.screen instanceof TitleScreen) {
                Files.createDirectories(output);
                client.options.pauseOnLostFocus = false;
                client.options.renderDistance().set(5);
                client.options.simulationDistance().set(5);
                client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                var rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
                started = true;
                client.createWorldOpenFlows().createFreshLevel("inventory-smoke-" + System.currentTimeMillis(),
                    new LevelSettings("MChjong inventory checks", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(12345, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
            } else if (started && client.player != null && client.getSingleplayerServer() != null) {
                if (checks == null) {
                    var server = client.getSingleplayerServer();
                    var id = client.player.getUUID();
                    checks = server.submit(() -> {
                        var player = server.getPlayerList().getPlayer(id);
                        if (player == null || !SurvivalSmoke.ready(player)) return false;
                        SurvivalSmoke.verify(player);
                        return true;
                    });
                } else if (checks.isDone()) {
                    if (!checks.join()) {
                        checks = null;
                        return;
                    }
                    Files.writeString(output.resolve("PASS.txt"), "Native inventory and equipment checks passed.\n");
                    LOG.info("MCHJONG_SMOKE_PASS");
                    finished = true;
                    client.stop();
                }
            }
        } catch (Throwable failure) {
            LOG.error("Inventory checks failed", failure);
            finished = true;
            try {
                Files.createDirectories(output);
                Files.writeString(output.resolve("FAIL.txt"), failure.toString());
            } catch (java.io.IOException writeFailure) {
                LOG.error("Cannot write failure marker", writeFailure);
            }
            client.stop();
        }
    }
}
