package top.skyeyefast.mchjong.smoke;

import com.mojang.math.Axis;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import top.skyeyefast.mchjong.client.FurnitureMesh;
import top.skyeyefast.mchjong.client.MahjongUi;
import top.skyeyefast.mchjong.client.McrSceneRenderer;
import top.skyeyefast.mchjong.client.McrTableScene;
import top.skyeyefast.mchjong.client.SichuanSceneRenderer;
import top.skyeyefast.mchjong.client.SichuanTableScene;
import top.skyeyefast.mchjong.client.TableIndicator;
import top.skyeyefast.mchjong.engine.McrView;
import top.skyeyefast.mchjong.engine.SichuanView;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.McrDeck;
import top.skyeyefast.mchjong.item.SichuanDeck;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Shared-client physical and immersive render fixtures, without a second gameplay smoke. */
final class McrDisplaySmoke {
    private static final String[] IMAGES = {"mcr-wall.png", "mcr-dealt.png", "mcr-midgame.png", "mcr-late.png",
        "sichuan-wall-east-west.png", "sichuan-dealt-east-west.png", "sichuan-midgame-east-west.png", "sichuan-late-east-west.png",
        "sichuan-wall-north-south.png", "sichuan-dealt-north-south.png", "sichuan-midgame-north-south.png", "sichuan-late-north-south.png",
        "mcr-immersive.png"};
    private Display display;
    private int ticks;
    private int frameTicks;
    private int stage;

    boolean tick(Minecraft client, Path output) throws Exception {
        if (++ticks > 900) throw new IllegalStateException("MCR display smoke timed out");
        if (client.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding) onboarding.onClose();
        if (display == null) {
            if (!(client.screen instanceof TitleScreen) || client.getOverlay() != null) return false;
            Files.createDirectories(output);
            client.options.guiScale().set(2);
            client.options.pauseOnLostFocus = false;
            client.resizeGui();
            display = new Display();
            client.setScreen(display);
            return false;
        }
        if (client.getOverlay() != null) return false;
        if (display.frames < 20) return false;
        if (++frameTicks == 1)
            SmokeScreenshots.grab(output.toFile(), IMAGES[stage], client.getMainRenderTarget(), 1, message -> {});
        boolean capture = List.of(System.getProperty("mchjong.smoke.screenshots", "").split(",")).contains(IMAGES[stage]);
        if (frameTicks > 2 && (!capture || Files.isRegularFile(output.resolve("screenshots").resolve(IMAGES[stage])))) {
            if (++stage == IMAGES.length) return true;
            display.stage = stage;
            display.frames = 0;
            frameTicks = 0;
        }
        return false;
    }

    private static final class Display extends Screen {
        private final List<McrView> mcr = List.of(mcrPosition(91, 0), mcrPosition(40, 0), mcrPosition(4, 0));
        private final List<SichuanView> eastWest = List.of(sichuanPosition(true, 55, 0), sichuanPosition(true, 25, 0), sichuanPosition(true, 4, 0));
        private final List<SichuanView> northSouth = List.of(sichuanPosition(false, 55, 0), sichuanPosition(false, 25, 0), sichuanPosition(false, 4, 0));
        private final McrDeck deck = new McrDeck(TileMaterial.BONE, DyeColor.BLUE, TileFacePreset.KANSAI,
            Identifier.fromNamespaceAndPath("mchjong", "default"));
        private final SichuanDeck sichuanDeck = new SichuanDeck(TileMaterial.BONE, DyeColor.BLUE, TileFacePreset.KANSAI,
            Identifier.fromNamespaceAndPath("mchjong", "default"));

        private int stage;
        private int frames;

        Display() {
            super(Component.literal("MCR layout verification"));

        }

        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            frames++;
            graphics.fill(0, 0, width, height, MahjongUi.PANEL);
            String variant = stage < 4 || stage == 12 ? "MCR" : stage < 8 ? "Sichuan SBR / East-West long wall" : "Sichuan TFMJ / North-South long wall";
            String phase = stage == 12 ? "actual midgame / wall-free display" : switch (stage % 4) { case 0 -> "complete wall"; case 1 -> "actual post-deal"; case 2 -> "actual midgame"; default -> "actual late game"; };
            graphics.centeredText(font, variant + " | " + phase + " | original Chinese tile size / 12-degree tilt",
                width / 2, 12, MahjongUi.TEXT);
            float scale = (float) Math.min(width / (2 * TableGeometry.OUTER_HALF_WIDTH + .4),
                (height - 48) / (2 * TableGeometry.OUTER_HALF_WIDTH + .4));
            SmokeMesh.extract(graphics, 0, 24, width, height - 24, scale, (pose, buffers) -> {
                // Native picture-in-picture flips z; restore the layout fixture's world axes.
                pose.scale(1, -1, -1);
                pose.mulPose(Axis.XP.rotationDegrees(64));
                pose.translate(0, -TableGeometry.FELT_Y, 0);
                FurnitureMesh.table(pose, buffers, 0xf000f0, FurnitureWood.OAK, DyeColor.CYAN, true);
                if (stage % 4 == 0 && stage != 12) TableIndicator.renderStandby(pose, buffers, 0xf000f0);
                if (stage < 4 || stage == 12) {
                    var view = mcr.get(stage == 12 ? 1 : Math.max(0, stage - 1));
                    if (stage != 0) TableIndicator.render(view, pose, buffers, 0xf000f0);
                    McrSceneRenderer.render(stage == 0 ? McrTableScene.fullWall()
                        : stage == 12 ? McrTableScene.immersive(view) : McrTableScene.build(view), deck, pose, buffers, 0xf000f0);
                } else {
                    boolean eastWestLong = stage < 8;
                    int phaseIndex = stage % 4;
                    var view = (eastWestLong ? eastWest : northSouth).get(Math.max(0, phaseIndex - 1));
                    if (phaseIndex != 0) TableIndicator.render(view, pose, buffers, 0xf000f0);
                    SichuanSceneRenderer.render(phaseIndex == 0 ? SichuanTableScene.fullWall(eastWestLong) : SichuanTableScene.build(view),
                        sichuanDeck, pose, buffers, 0xf000f0);
                }
            });
            graphics.centeredText(font, "Shared tile mesh and physical-slot scene; no room mode is enabled",

                width / 2, height - 20, MahjongUi.MUTED);
        }
    }

    static McrView mcrPosition(int remaining, int viewer) {
        return top.skyeyefast.mchjong.fixture.ChineseGameplayFixtures.mcr(711, remaining, viewer, view -> {
            if (McrTableScene.build(view).size() != 144) throw new IllegalStateException("MCR capture lost physical stock");
        });
    }

    static SichuanView sichuanPosition(boolean eastWest, int remaining, int viewer) {
        return top.skyeyefast.mchjong.fixture.ChineseGameplayFixtures.sichuan(711, eastWest, remaining, viewer, false, view -> {
            if (SichuanTableScene.build(view).size() != 108) throw new IllegalStateException("Sichuan capture lost physical stock");
        });
    }
}
