package top.skyeyefast.mchjong.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
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

/** Runs the Patchouli installed or absent checks in an isolated world. */
public final class TableClientSmoke {
    private static final Logger LOG = LoggerFactory.getLogger("mchjong-smoke");
    private final Path output = Path.of(System.getProperty("mchjong.smoke.output"));
    private boolean started, finished;
    private int ticks;
    public void tick(Minecraft client) {
        if (finished) return;
        try {
            if (++ticks > 6600) throw new IllegalStateException("Handbook checks timed out");
            if (!started && client.screen instanceof AccessibilityOnboardingScreen onboarding) {
                onboarding.onClose();
            } else if (!started && client.screen instanceof TitleScreen && client.getOverlay() == null) {
                Files.createDirectories(output);
                client.options.pauseOnLostFocus = false;
                client.options.renderDistance().set(5);
                client.options.simulationDistance().set(5);
                client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                var rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
                top.skyeyefast.mchjong.client.TableSettings.get().reset();
                top.skyeyefast.mchjong.client.TableSettings.get().recommendPatchouli = true;
                client.options.guiScale().set(2);
                client.resizeDisplay();
                started = true;
                client.createWorldOpenFlows().createFreshLevel("handbook-smoke-" + System.currentTimeMillis(),
                    new LevelSettings("MChjong handbook checks", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(12345, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
            } else if (started && client.player != null && client.getSingleplayerServer() != null) {
                if (!ManualSmoke.tick(client, output)) return;
                Files.writeString(output.resolve("PASS.txt"), "Patchouli handbook checks passed.\n");
                LOG.info("MCHJONG_PATCHOULI_SMOKE_PASS");
                finished = true;
                client.stop();
            }
        } catch (Throwable failure) {
            LOG.error("Handbook checks failed", failure);
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
