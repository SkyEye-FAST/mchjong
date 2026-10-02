package top.skyeyefast.mchjong.smoke;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import top.skyeyefast.mchjong.client.FurnitureMesh;
import top.skyeyefast.mchjong.client.MahjongUi;
import top.skyeyefast.mchjong.client.McrSceneRenderer;
import top.skyeyefast.mchjong.client.McrTableScene;
import top.skyeyefast.mchjong.client.SichuanSceneRenderer;
import top.skyeyefast.mchjong.client.SichuanTableScene;
import top.skyeyefast.mchjong.client.TableIndicator;
import top.skyeyefast.mchjong.engine.McrDiscard;
import top.skyeyefast.mchjong.engine.McrGame;
import top.skyeyefast.mchjong.engine.McrOpening;
import top.skyeyefast.mchjong.engine.McrView;
import top.skyeyefast.mchjong.engine.McrWallLayout;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.SichuanGame;
import top.skyeyefast.mchjong.engine.SichuanPreset;
import top.skyeyefast.mchjong.engine.SichuanPlayerState;
import top.skyeyefast.mchjong.engine.SichuanView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.McrDeck;
import top.skyeyefast.mchjong.item.SichuanDeck;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Shared-client physical and immersive render fixtures, without a second gameplay smoke. */
final class McrDisplaySmoke {
    private static final String[] IMAGES = {"mcr-wall.png", "mcr-play.png", "mcr-immersive.png",
        "sichuan-wall-east-west.png", "sichuan-wall-north-south.png",
        "mcr-wall-rivers.png", "sichuan-wall-east-west-rivers.png", "sichuan-wall-north-south-rivers.png",
        "mcr-dealt.png", "sichuan-dealt-east-west.png", "sichuan-dealt-north-south.png"};
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
            client.resizeDisplay();
            display = new Display();
            client.setScreen(display);
            return false;
        }
        if (client.getOverlay() != null) return false;
        if (display.frames < 20) return false;
        if (++frameTicks == 1)
            SmokeScreenshots.grab(output.toFile(), IMAGES[stage], client.getMainRenderTarget(), message -> {});
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
        private final List<McrTableScene.Piece> wall = McrTableScene.fullWall();
        private final List<McrTableScene.Piece> play = McrTableScene.build(position());
        private final McrDeck deck = new McrDeck(TileMaterial.BONE, DyeColor.BLUE, TileFacePreset.KANSAI,
            ResourceLocation.fromNamespaceAndPath("mchjong", "default"));
        private final SichuanDeck sichuanDeck = new SichuanDeck(TileMaterial.BONE, DyeColor.BLUE, TileFacePreset.KANSAI,
            ResourceLocation.fromNamespaceAndPath("mchjong", "default"));
        private final List<SichuanTableScene.Piece> eastWestWall = SichuanTableScene.fullWall(true);
        private final List<SichuanTableScene.Piece> northSouthWall = SichuanTableScene.fullWall(false);
        private final List<McrTableScene.Piece> space = java.util.stream.Stream.concat(McrTableScene.fullWall().stream(),
            McrTableScene.build(railFixture(0)).stream()).toList();
        private final List<SichuanTableScene.Piece> eastWestSpace = SichuanTableScene.build(railFixture(true, 0));
        private final List<SichuanTableScene.Piece> northSouthSpace = SichuanTableScene.build(railFixture(false, 0));
        private final List<McrTableScene.Piece> dealt = McrTableScene.build(new McrGame(711).view(0));
        private final List<SichuanTableScene.Piece> eastWestDealt = SichuanTableScene.build(dealt(true, 0));
        private final List<SichuanTableScene.Piece> northSouthDealt = SichuanTableScene.build(dealt(false, 0));
        private int stage;
        private int frames;

        Display() {
            super(Component.literal("MCR layout verification"));
            if (wall.size() != 144 || play.size() != 144) throw new IllegalStateException("Display lost physical tiles");
            if (eastWestWall.size() != 108 || northSouthWall.size() != 108)
                throw new IllegalStateException("Sichuan display lost physical tiles");
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            frames++;
            graphics.fill(0, 0, width, height, MahjongUi.PANEL);
            graphics.drawCenteredString(font, switch (stage) {
                case 0 -> "MCR | 144 tiles / four 18-stack walls / 12-degree tilt";
                case 3 -> "Sichuan | 108 tiles / East-West 14 stacks / 12-degree tilt";
                case 4 -> "Sichuan | 108 tiles / North-South 14 stacks / 12-degree tilt";
                case 5 -> "MCR | full wall, four river rows, hands and melds / capacity fixture";
                case 6 -> "Sichuan | East-West long wall, rivers, hands and melds / capacity fixture";
                case 7 -> "Sichuan | North-South long wall, rivers, hands and melds / capacity fixture";
                case 8 -> "MCR | actual post-deal scene";
                case 9 -> "Sichuan | East-West long wall / actual post-deal scene";
                case 10 -> "Sichuan | North-South long wall / actual post-deal scene";
                default -> "MCR | six-column rivers, public melds and flowers";
            }, width / 2, 12, MahjongUi.TEXT);
            graphics.flush();
            var pose = graphics.pose();
            float scale = (float) Math.min(width / (2 * TableGeometry.OUTER_HALF_WIDTH + .4),
                (height - 48) / (2 * TableGeometry.OUTER_HALF_WIDTH + .4));
            pose.pushPose();
            pose.translate(width / 2.0, height / 2.0 + 12, 500);
            pose.scale(scale, -scale, scale);
            pose.mulPose(Axis.XP.rotationDegrees(64));
            pose.translate(0, -TableGeometry.FELT_Y, 0);
            RenderSystem.enableDepthTest();
            FurnitureMesh.table(pose, graphics.bufferSource(), 0xf000f0, FurnitureWood.OAK, DyeColor.CYAN, true);
            TableIndicator.renderStandby(pose, graphics.bufferSource(), 0xf000f0);
            if (stage == 3 || stage == 4 || stage == 6 || stage == 7 || stage >= 9)
                SichuanSceneRenderer.render(switch (stage) {
                    case 3 -> eastWestWall;
                    case 4 -> northSouthWall;
                    case 6 -> eastWestSpace;
                    case 7 -> northSouthSpace;
                    case 9 -> eastWestDealt;
                    default -> northSouthDealt;
                }, sichuanDeck, pose, graphics.bufferSource(), 0xf000f0);
            else McrSceneRenderer.render(switch (stage) {
                case 0 -> wall;
                case 1 -> play;
                case 5 -> space;
                case 8 -> dealt;
                default -> McrTableScene.immersive(position());
            }, deck, pose, graphics.bufferSource(), 0xf000f0);
            graphics.flush();
            pose.popPose();
            graphics.drawCenteredString(font, "Shared tile mesh and physical-slot scene; no room mode is enabled",
                width / 2, height - 20, MahjongUi.MUTED);
        }
    }

    /** Capacity fixtures intentionally occupy full walls, rivers and outer rails together. */
    static McrView railFixture(int viewer) {
        var base = new McrGame(711).view(viewer);
        var seats = new ArrayList<McrView.Seat>();
        for (int seat = 0; seat < 4; seat++) {
            var river = new ArrayList<McrDiscard>();
            for (int index = 0; index < 24; index++) river.add(new McrDiscard(index, false, false));
            var hand = seat == viewer ? java.util.stream.IntStream.range(0, 8).boxed().toList() : Collections.nCopies(8, Tile.HIDDEN);
            seats.add(new McrView.Seat(Tile.EAST + seat, 0, hand, hand.getLast(), railMelds(seat), river,
                seat == 0 ? java.util.stream.IntStream.range(136, 144).boxed().toList() : List.of(), false));
        }
        return new McrView(1, 1, 1, McrGame.Phase.TURN, viewer, 0, Tile.EAST, 0, 0, base.opening(),
            Collections.nCopies(144, Tile.ABSENT), null, seats, List.of(), false, false, null, List.of());
    }

    static SichuanView dealt(boolean eastWestLongWall, int viewer) {
        var rules = (eastWestLongWall ? SichuanPreset.SBR_2025 : SichuanPreset.TFMJ_2024).config();
        return new SichuanGame(711, rules, Tile.sichuanSet()).view(viewer);
    }

    static SichuanView railFixture(boolean eastWestLongWall, int viewer) {
        var base = dealt(eastWestLongWall, viewer);
        var seats = new ArrayList<SichuanView.Seat>();
        for (int seat = 0; seat < 4; seat++) {
            var river = new ArrayList<SichuanPlayerState.Discard>();
            for (int index = 0; index < 24; index++) river.add(new SichuanPlayerState.Discard(index, false));
            var hand = seat == viewer ? java.util.stream.IntStream.range(0, 8).boxed().toList() : Collections.nCopies(8, Tile.HIDDEN);
            seats.add(new SichuanView.Seat(hand, railMelds(seat), river, 0, false, hand.getLast(), Tile.ABSENT));
        }
        return new SichuanView(1, 1, base.rules(), SichuanGame.Phase.TURN, 1, 0, base.scores(), viewer, 0,
            new SichuanView.Wall(Collections.nCopies(108, Tile.HIDDEN), 0, 1, 1, eastWestLongWall), seats,
            Tile.ABSENT, -1, false, false, List.of(), List.of(), List.of(), null, -1);
    }

    private static List<Meld> railMelds(int seat) {
        return List.of(new Meld(Meld.Type.OPEN_QUAD, List.of(0, 1, 2, 3), (seat + 3) % 4, 0),
            new Meld(Meld.Type.OPEN_QUAD, List.of(4, 5, 6, 7), (seat + 1) % 4, 4));
    }

    /** Allocate one complete physical stock, then redact opponents exactly at the view boundary. */
    private static McrView position() {
        var opening = McrOpening.of(0, new McrOpening.Roll(2, 3), new McrOpening.Roll(3, 4));
        var stock = new ArrayList<>(Tile.mcrSet());
        var melds = List.of(
            List.of(new Meld(Meld.Type.SEQUENCE, List.of(0, 4, 8), 3, 0),
                new Meld(Meld.Type.TRIPLET, List.of(124, 125, 126), 2, 124),
                new Meld(Meld.Type.OPEN_QUAD, List.of(48, 49, 50, 51), 1, 48)),
            List.of(new Meld(Meld.Type.CONCEALED_QUAD, List.of(88, 89, 90, 91), 1, Tile.ABSENT)),
            List.of(new Meld(Meld.Type.ADDED_QUAD, List.of(72, 73, 74, 75), 0, 72)),
            List.<Meld>of());
        for (var groups : melds) for (var meld : groups) stock.removeAll(meld.tiles());
        var seats = new ArrayList<McrView.Seat>();
        for (int seat = 0; seat < 4; seat++) {
            int size = (seat == 0 ? 14 : 13) - melds.get(seat).size() * 3;
            var hand = new ArrayList<Integer>();
            for (int i = 0; i < size; i++) hand.add(stock.remove(0));
            var river = new ArrayList<McrDiscard>();
            for (int i = 0; i < (seat == 0 ? 19 : 13); i++) river.add(new McrDiscard(stock.remove(0), false, false));
            // These history entries refer to physical tiles already allocated to another player's meld.
            int called = switch (seat) { case 0 -> 72; case 1 -> 48; case 2 -> 124; default -> 0; };
            river.add(4, new McrDiscard(called, true, false));
            var flowers = List.of(136 + seat * 2, 137 + seat * 2);
            stock.removeAll(flowers);
            var publicMelds = melds.get(seat).stream().map(meld -> meld.closed()
                ? new Meld(meld.type(), Collections.nCopies(4, Tile.HIDDEN), meld.fromSeat(), Tile.ABSENT) : meld).toList();
            seats.add(new McrView.Seat(Tile.EAST + seat, 0, seat == 0 ? hand : Collections.nCopies(size, Tile.HIDDEN),
                seat == 0 ? hand.get(hand.size() - 1) : Tile.ABSENT, publicMelds, river, flowers, false));
        }
        var wall = new ArrayList<>(Collections.nCopies(144, Tile.ABSENT));
        for (int i = 0; i < stock.size(); i++) wall.set(McrWallLayout.drawSlot(opening, 108 + i), Tile.HIDDEN);
        return new McrView(1, 1, 1, McrGame.Phase.TURN, 0, 0, Tile.EAST, 0, stock.size(), opening,
            wall, null, seats, List.of(), false, false, null, List.of());
    }
}
