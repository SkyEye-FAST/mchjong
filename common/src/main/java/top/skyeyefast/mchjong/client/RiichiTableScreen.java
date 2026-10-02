package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.network.PayloadPackets;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.engine.RiichiAction;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.engine.RiichiRoomSettings;
import top.skyeyefast.mchjong.engine.RiichiRules;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.network.RiichiActionPayload;
import top.skyeyefast.mchjong.network.RiichiControlPayload;
import top.skyeyefast.mchjong.network.TableSessionControlPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Non-pausing table controls with seated world interaction and an independent immersive play surface. */
public final class RiichiTableScreen extends Screen {
    private final RoomLobby lobby;
    private final TableViewController presentation = new TableViewController();
    private static final String[] WINDS = {"east", "south", "west", "north"};
    private final BlockPos pos;
    private final List<CalloutButton> callouts = new ArrayList<>();
    private final List<AbstractWidget> decisionButtons = new ArrayList<>();
    private final TableTurnClock turnClock = new TableTurnClock();
    private List<RiichiTableScene.Piece> scene = List.of();
    private List<RiichiAnimation.Frame> frames = List.of();
    private List<RiichiTableScene.Piece> settledScene = List.of();
    private List<RiichiAnimation.Frame> settledFrames = List.of();
    private final RiichiDecision decision = new RiichiDecision();
    private boolean choosingRiichi;
    private Button confirmButton;
    private int selectedTile = Tile.ABSENT;
    private int hoveredTile = Tile.ABSENT;
    private long lastRevision = -1;
    public boolean inspecting() { return presentation.inspecting(); }
    private float framePartial;
    private long lastClickAt;
    private int lastClickedTile = Tile.ABSENT;
    private TableResults results;
    private boolean resultsExpanded = true;
    private RiichiView.Phase lastPhase;
    private TableResults.Page resultPage = TableResults.Page.HAND;
    private long resultStarted;
    private boolean finalSummaryShown;
    private Component informationTooltip;
    private final RiichiHud information = new RiichiHud();
    private int actionTop;
    private int actionLeft;
    private int actionHeight;
    private ScreenRectangle privateHandBounds;
    private RiichiView handlingDrag;
    private Vec3 handlingStart;
    private Vec3 handlingPointer;
    private RiichiView handDrag;
    private int handDragTile = Tile.ABSENT;
    private double handDragStartX, handDragStartY, handDragX, handDragY;
    private boolean handDragMoved;
    private TableBoard board;
    private TableHand hand;
    private RiichiView presentedView;
    private ImmersiveDiscardMotion immersiveDiscard;
    private ImmersiveDrawMotion immersiveDraw;
    private record ImmersiveDiscardMotion(int tile, int seat, boolean tsumogiri, boolean riichi,
                                          long started, long duration, TableHand.Point source, int sourceWidth, double opponentX) {}
    private record ImmersiveDrawMotion(int tile, long started, long duration, TableHand.Point target, int targetWidth) {}
    private final RiichiHints hints = new RiichiHints();
    private final RiichiDice dice = new RiichiDice(() -> {
        var current = view();
        if (current != null) {
            int index = findAction(current, RiichiAction.Type.PICK_UP_DICE, List.of());
            if (index >= 0) send(current, index);
        }
    });
    private final TableAutomation automation = new TableAutomation(this, this::automationOptions, () -> lastRevision = -1);

    private List<TableAutomation.Toggle> automationOptions() {
        var current = view();
        if (current == null || current.autoPlay() == null || current.viewerSeat() < 0
            || (current.phase() != RiichiView.Phase.TURN && current.phase() != RiichiView.Phase.REACTION)
            || current.exitVote() != null) return List.of();
        var choices = new ArrayList<TableAutomation.Toggle>();
        choices.add(new TableAutomation.Toggle("ui.mchjong.auto_sort", current.autoPlay().sort(),
            () -> control(current, RiichiControlPayload.Operation.AUTO_SORT, current.decision(), !current.autoPlay().sort())));
        choices.addAll(TableAutomation.common(pos, room(), current.decision()));
        if (current.rules().sanma()) choices.add(new TableAutomation.Toggle("ui.mchjong.auto_kita", current.autoPlay().kita(),
            () -> control(current, RiichiControlPayload.Operation.AUTO_KITA, current.decision(), !current.autoPlay().kita())));
        return choices;
    }

    public RiichiTableScreen(BlockPos pos) { super(Component.translatable("ui.mchjong.title")); this.pos = pos.immutable(); lobby = new RoomLobby(this, this.pos, this::rebuild); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void removed() {
        presentation.clearInput();
        handDrag = null;
        if (RiichiAudio.result(presentedView) != null) {
            RiichiAudio.finishResult();
            VoicePresets.stop();
        }
    }
    public BlockPos tablePos() { return pos; }
    public boolean immersive() { return presentation.immersive(); }
    private int uiWidth() { return canvas().width(); }
    private int uiHeight() { return canvas().height(); }
    private TableCanvas canvas() { return presentation.canvas(width, height); }
    private double canvasX(double x) { return canvas().localX(x); }
    private double canvasY(double y) { return canvas().localY(y); }
    private boolean insideImmersiveCanvas(double x, double y) { return !immersive() || canvas().contains(x, y); }

    private void toggleView() {
        RiichiView view = view();
        if (view == null) return;
        presentation.toggle();
        handlingDrag = null;
        handDrag = null;
        rebuild();
    }

    public static RiichiTableScreen active(Screen screen) {
        screen = TableChildScreen.root(screen);
        if (screen instanceof RiichiTableScreen table) return table;
        if (screen instanceof RiichiRulesScreen rules) return rules.tableScreen();
        return null;
    }

    public void resetView() {
        RiichiView view = view();
        if (minecraft == null || minecraft.player == null || view == null) return;
        presentation.reset();
    }

    RiichiView view() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table
            ? table.clientView() : null;
    }

    top.skyeyefast.mchjong.engine.TableRoomView room() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table
            ? table.clientRoom() : null;
    }

    RiichiRoomSettings roomSettings() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table
            ? table.clientRiichiSettings() : null;
    }

    top.skyeyefast.mchjong.world.BotServiceState botService() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table
            ? table.clientBotService() : null;
    }

    top.skyeyefast.mchjong.world.WorldSettings.Policy worldPolicy() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table
            ? table.clientWorldPolicy() : null;
    }

    boolean canSupplyReds(boolean sanma, top.skyeyefast.mchjong.engine.RedFives reds) {
        return minecraft != null && minecraft.level != null
            && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table
            && (table.clientRedOptions() & 1 << ((sanma ? 3 : 0) + reds.ordinal())) != 0;
    }

    private TileFacePreset facePreset() {
        return ((MahjongTableBlockEntity) minecraft.level.getBlockEntity(pos)).equipment().preset();
    }

    private top.skyeyefast.mchjong.item.TileMaterial tileMaterial() {
        return ((MahjongTableBlockEntity) minecraft.level.getBlockEntity(pos)).equipment().material();
    }

    private net.minecraft.world.item.DyeColor tileBack() {
        return ((MahjongTableBlockEntity) minecraft.level.getBlockEntity(pos)).equipment().back();
    }
    private net.minecraft.world.item.DyeColor clothColor() {
        var equipment = ((MahjongTableBlockEntity) minecraft.level.getBlockEntity(pos)).equipment();
        return equipment.hasCloth() ? equipment.clothColor() : null;
    }
    private net.minecraft.resources.ResourceLocation tileBackPreset() {
        return ((MahjongTableBlockEntity) minecraft.level.getBlockEntity(pos)).equipment().backPreset();
    }

    private RiichiAnimation animation() {
        if (minecraft == null || minecraft.level == null || !(minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table)) return null;
        RiichiAnimation animation = RiichiAnimation.of(table);
        animation.accept(table.clientView(), Util.getMillis());
        return animation;
    }

    private void updateScene() {
        RiichiAnimation animation = animation();
        settledFrames = animation == null ? List.of() : animation.settled();
        settledScene = settledFrames.stream().map(RiichiAnimation.Frame::piece).toList();
        frames = animation == null ? List.of() : TableSettings.get().animations ? animation.sample(Util.getMillis()) : settledFrames;
        scene = frames.stream().map(RiichiAnimation.Frame::piece).toList();
    }

    private static int findAction(RiichiView view, RiichiAction.Type type, List<Integer> arguments) {
        for (int index = 0; index < view.actions().size(); index++) {
            var action = view.actions().get(index);
            if (action.type() == type && action.tiles().equals(arguments)) return index;
        }
        return -1;
    }

    private boolean dealing() {
        RiichiAnimation animation = animation();
        return animation != null && TableSettings.get().animations && animation.dealing(Util.getMillis());
    }

    private boolean handlingMoving() {
        RiichiAnimation animation = animation();
        return animation != null && TableSettings.get().animations && animation.moving(Util.getMillis());
    }

    public void receivedView() {
        lobby.receivedView();
        refreshDecision(view());
        lastRevision = -1;
    }

    public void receivedControlReply() {
        automation.receivedControlReply();
        if (minecraft.screen instanceof RiichiRulesScreen rules && rules.tableScreen() == this) rules.receivedReply();
    }

    private void refreshDecision(RiichiView view) {
        if (handlingDrag != null && (view == null || !handlingDrag.tableId().equals(view.tableId())
            || handlingDrag.decision() != view.decision())) handlingDrag = null;
        if (handDrag != null && (view == null || !handDrag.tableId().equals(view.tableId())
            || handDrag.decision() != view.decision() || view.viewerSeat() < 0
            || !view.seats().get(view.viewerSeat()).hand().contains(handDragTile))) handDrag = null;
        if (decision.receive(view)) {
            selectedTile = lastClickedTile = hoveredTile = Tile.ABSENT;
            hints.clearPreview();
            choosingRiichi = false;
        }
    }

    public static Component roundName(RiichiView view) {
        Component wind = Component.translatable("wind.mchjong." + WINDS[Math.min(3, view.round() / view.rules().players())]);
        return Component.translatable("ui.mchjong.round", wind, view.round() % view.rules().players() + 1, view.honba());
    }

    public boolean selected(BlockPos table, RiichiTableScene.Piece piece) {
        RiichiView view = view();
        return pos.equals(table) && view != null && piece.area() == RiichiTableScene.Area.HAND && piece.seat() == view.viewerSeat()
            && piece.tile() >= 0 && (piece.tile() == selectedTile || piece.tile() == hoveredTile);
    }

    /** The world renderer applies this color to the same animated mesh used for picking. */
    public int highlight(BlockPos table, RiichiTableScene.Piece piece) {
        RiichiView view = view();
        if (!pos.equals(table) || view == null || view.exitVote() != null || results != null) return 0;
        boolean highlights = TableSettings.get().highlightTiles;
        if (piece.area() == RiichiTableScene.Area.HAND && piece.seat() == view.viewerSeat()) {
            if (piece.tile() >= 0 && (piece.tile() == selectedTile || highlights && piece.tile() == hoveredTile))
                return MahjongUi.ACCENT;
            if (highlights && choosingRiichi && tileAction(view, piece.tile(), RiichiAction.Type.RIICHI) >= 0) return MahjongUi.POSITIVE;
            for (var button : callouts) if ((button.isFocused() || highlights && button.isHovered()) && showsConsumed(button.action)
                && button.action.tiles().contains(piece.tile())) return MahjongUi.POSITIVE;
        }
        if ((highlights || getFocused() instanceof PhysicalHandle) && RiichiHandling.action(view) >= 0) {
            // Keep the held outline attached to the displaced mesh throughout the gesture.
            if (handlingDrag != null)
                return handlingOffset(table, piece).lengthSqr() > 0 ? MahjongUi.POSITIVE : 0;
            if (!handlingMoving() && RiichiHandling.source(view, piece)
                && (view.phase() != RiichiView.Phase.SHUFFLE || piece.equals(RiichiHandling.source(view, scene))))
                return MahjongUi.ACCENT;
        }
        return 0;
    }

    @Override protected void init() { lastRevision = -1; rebuild(); }

    @Override public void tick() {
        presentation.tick();
        var current = view();
        var world = worldPolicy();
        if (current != null && world != null && current.viewerSeat() < 0
            && !world.spectatingEnabled()) {
            onClose();
            return;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.player.distanceToSqr(pos.getX()+0.5, pos.getY(), pos.getZ()+0.5) > 36
            || !(minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity)) onClose();
    }

    private boolean tileChoice(RiichiAction action) {
        return switch (action.type()) {
            case DISCARD, RIICHI, CLOSED_KAN, ADDED_KAN, NUKI -> true;
            default -> false;
        };
    }

    private void rebuild() {
        boolean hintFocus = getFocused() == hints;
        int automationFocus = automation.focusedIndex(getFocused());
        clearWidgets();
        callouts.clear();
        decisionButtons.clear();
        turnClock.visible = false;
        privateHandBounds = null;
        confirmButton = null;
        TableRoomView room = room();
        RiichiRoomSettings roomSettings = roomSettings();
        if (room != null && room.lobby()) {
            if (roomSettings == null) return;
            presentation.immersive(false);
            presentedView = null;
            lastPhase = null;
            results = null;
            board = null;
            hand = null;
            lastRevision = room.revision();
            buildLobby(room, roomSettings);
            return;
        }
        RiichiView view = view();
        if (view == null) return;
        RiichiView previous = presentedView;
        int oldSelected = selectedTile, oldHovered = hoveredTile;
        refreshDecision(view);
        boolean newResult = TableResults.available(view) && (lastPhase != view.phase() || previous == null
            || !previous.tableId().equals(view.tableId()) || previous.handNumber() != view.handNumber()
            || !previous.wins().equals(view.wins()));
        if (newResult) {
            resultsExpanded = true;
            resultPage = TableResults.Page.HAND;
            resultStarted = Util.getMillis();
            finalSummaryShown = false;
        }
        if (view.phase() == RiichiView.Phase.MATCH_END
            && view.settlementTicks() <= RiichiGame.SETTLEMENT_TICKS && !finalSummaryShown) {
            RiichiAudio.finishResult();
            resultPage = TableResults.Page.MATCH;
            resultsExpanded = true;
            finalSummaryShown = true;
        }
        int selectedWinner = results == null || newResult ? 0 : results.selectedWinner();
        results = null;
        lastPhase = view.phase();
        lastRevision = view.revision();
        updateScene();
        board = null;
        int layoutWidth = uiWidth(), layoutHeight = uiHeight();
        int handHeight = layoutHeight - (immersive() ? TableResults.available(view) ? 116 : automation.available() ? 60 : 30 : 0);
        hand = immersive() && view.viewerSeat() >= 0
            && !view.seats().get(view.viewerSeat()).hand().isEmpty()
            ? new TableHand(view.seats().get(view.viewerSeat()), view.viewerSeat(), layoutWidth, handHeight, 58, true) : null;
        prepareImmersiveMotion(previous, view, handHeight, oldSelected, oldHovered);
        presentedView = view;
        if (view.viewerSeat() < 0 || !view.seats().get(view.viewerSeat()).hand().contains(selectedTile)) selectedTile = Tile.ABSENT;
        if (view.actions().stream().noneMatch(action -> action.type() == RiichiAction.Type.RIICHI)) choosingRiichi = false;
        buildToolbar(view);
        actionLeft = 10;
        automation.build(layoutWidth, immersive() ? layoutHeight - 32 : hand == null ? layoutHeight - 17 : hand.top() - 8,
            immersive()).forEach(this::addRenderableWidget);
        automation.restoreFocus(automationFocus);
        if (view.exitVote() != null) { buildExitVote(view); return; }
        int physical = RiichiHandling.action(view);
        if (physical >= 0 && !immersive()) addRenderableWidget(new PhysicalHandle(view, physical));
        List<Integer> choices = new ArrayList<>();
        for (int i = 0; i < view.actions().size(); i++) {
            RiichiAction action = view.actions().get(i);
            if (action.type() == RiichiAction.Type.DISCARD || action.type() == RiichiAction.Type.RIICHI || action.type() == RiichiAction.Type.NEXT
                || action.type() == RiichiAction.Type.SKIP_SETTLEMENT || action.type() == RiichiAction.Type.SETTLEMENT_DONE
                || !immersive() && RiichiHandling.physical(view, action)) continue;
            choices.add(i);
        }
        boolean riichi = view.actions().stream().anyMatch(action -> action.type() == RiichiAction.Type.RIICHI);
        int discard = (choosingRiichi || TableSettings.get().discardMode == TableSettings.DiscardMode.CONFIRM) && selectedTile >= 0
            ? tileAction(view, selectedTile, choosingRiichi ? RiichiAction.Type.RIICHI : RiichiAction.Type.DISCARD) : -1;
        int count = choices.size() + (riichi ? 1 : 0) + (discard >= 0 ? 1 : 0);
        boolean compactActions = immersive() && !TableResults.available(view);
        int columns, boxWidth, rows, startX, buttonHeight, buttonGap;
        if (compactActions) {
            columns = Math.max(1, Math.min(3, count));
            rows = Math.max(1, (count + columns - 1) / columns);
            int maxStrip = 416;
            boxWidth = Math.clamp((maxStrip - (columns - 1) * 8) / columns, 104, 168);
            buttonHeight = 40;
            buttonGap = 8;
            int stripWidth = columns * boxWidth + (columns - 1) * buttonGap;
            startX = layoutWidth - stripWidth - 32;
            actionTop = (hand == null ? layoutHeight - 112 : hand.top() - 12) - rows * (buttonHeight + buttonGap);
            actionLeft = startX;
        } else {
            int scale = immersive() ? 2 : 1;
            int actionWidth = layoutWidth - 20 * scale - (!immersive() && automation.available() ? automation.width(layoutWidth) + 8 : 0);
            actionLeft = layoutWidth - 10 * scale - actionWidth;
            columns = Math.min(Math.max(1, count), Math.max(1, Math.min(3, actionWidth / (88 * scale))));
            boxWidth = Math.min(132 * scale, (actionWidth - (columns - 1) * 4 * scale) / columns);
            rows = Math.max(1, (count + columns - 1) / columns);
            buttonHeight = 26 * scale;
            buttonGap = 4 * scale;
            actionTop = (hand == null || TableResults.available(view) ? layoutHeight - 43 * scale : hand.top() - 34 * scale) - (rows - 1) * 30 * scale;
            startX = layoutWidth - 10 * scale - columns * (boxWidth + buttonGap) + buttonGap;
        }
        if (immersive()) board = new TableBoard(TableBoardState.live(view), 20, layoutWidth - 20, 68,
            hand == null ? layoutHeight - 112 : hand.top() - 24, layoutHeight, true);
        actionHeight = count == 0 ? 0 : rows * (buttonHeight + buttonGap) - buttonGap;
        int slot = 0;
        if (riichi) {
            var button = MahjongButton.create(Component.translatable(choosingRiichi ? "ui.mchjong.cancel_riichi" : "action.mchjong.riichi"),
                ignored -> toggleRiichi()).bounds(startX, actionTop, boxWidth, buttonHeight).build();
            button.setTooltip(Tooltip.create(button.getMessage()));
            addRenderableWidget(button);
            decisionButtons.add(button);
            slot++;
        }
        for (int index : choices) {
            RiichiAction action = view.actions().get(index);
            Component label = Component.translatable(action.translationKey());
            final RiichiView snapshot = view;
            CalloutButton button = new CalloutButton(startX + slot % columns * (boxWidth + buttonGap),
                actionTop + slot / columns * (buttonHeight + buttonGap), boxWidth, buttonHeight, label, action,
                () -> send(snapshot, index));
            button.setTooltip(Tooltip.create(label));
            addRenderableWidget(button);
            callouts.add(button);
            decisionButtons.add(button);
            slot++;
        }
        if (TableResults.available(view) && TableSettings.get().show(TableSettings.Information.RESULTS)) {
            int scale = immersive() ? 2 : 1;
            addRenderableWidget(MahjongButton.create(Component.translatable(resultsExpanded ? "ui.mchjong.view_table" : "ui.mchjong.view_results"),
                ignored -> { RiichiAudio.finishResult(); resultsExpanded = !resultsExpanded; rebuild(); }).bounds(10 * scale, uiHeight() - 48 * scale, boxWidth, 20 * scale).build());
            if (resultsExpanded) {
                int tabs = view.phase() == RiichiView.Phase.MATCH_END ? 3 : 2;
                int panelWidth = Math.min(layoutWidth - 20 * scale, 520 * scale);
                int panelLeft = (layoutWidth - panelWidth) / 2;
                int tabWidth = panelWidth / tabs;
                for (int i = 0; i < tabs; i++) {
                    var page = TableResults.Page.values()[i];
                    var button = MahjongButton.create(Component.translatable("ui.mchjong.result_page." + i), ignored -> {
                        RiichiAudio.finishResult();
                        resultPage = page; results = null; rebuild();
                    }).bounds(panelLeft + i * tabWidth, 34 * scale, tabWidth - 3 * scale, 20 * scale).build();
                    button.selected(page == resultPage);
                    addRenderableWidget(button);
                }
                int panelTop = 58 * scale;
                int panelHeight = layoutHeight - panelTop - 54 * scale;
                if (resultPage != TableResults.Page.HAND) panelHeight = Math.min(panelHeight, (48 + view.seats().size() * 26) * scale);
                results = addRenderableWidget(new TableResults(font, view, facePreset(), tileMaterial(), tileBack(), tileBackPreset(),
                    panelLeft, panelTop, panelWidth, panelHeight,
                    selectedWinner, resultPage, resultStarted, immersive() ? 2 : 1).readout(RiichiAudio.result(view)));
            }
        }
        if (discard >= 0) {
                final RiichiView snapshot = view;
                final int actionIndex = discard;
                confirmButton = addRenderableWidget(MahjongButton.create(Component.translatable(choosingRiichi ? "ui.mchjong.confirm_riichi" : "action.mchjong.discard"), ignored -> send(snapshot, actionIndex))
                    .bounds(startX + slot % columns * (boxWidth + buttonGap),
                        actionTop + slot / columns * (buttonHeight + buttonGap), boxWidth, buttonHeight).build().primary());
                confirmButton.setTooltip(Tooltip.create(confirmButton.getMessage()));
                decisionButtons.add(confirmButton);
        }
        addRenderableWidget(turnClock);
        addRenderableWidget(hints);
        addRenderableWidget(dice);
        if (immersive()) for (var child : children())
            if (child instanceof MahjongButton button && button.getHeight() >= 30) button.textScale(2);
        if (hintFocus && hints.visible) setFocused(hints);
    }

    private void prepareImmersiveMotion(RiichiView previous, RiichiView next, int handHeight, int oldSelected, int oldHovered) {
        if (!immersive() || !TableSettings.get().animations || previous == null
            || !previous.tableId().equals(next.tableId()) || previous.handNumber() != next.handNumber()
            || previous.viewerSeat() != next.viewerSeat()) {
            immersiveDiscard = null;
            immersiveDraw = null;
            return;
        }
        if (next.revision() <= previous.revision()) return;
        if (immersiveDiscard != null && next.seats().get(immersiveDiscard.seat()).river().stream()
            .noneMatch(d -> d.tile() == immersiveDiscard.tile() && !d.called())) immersiveDiscard = null;
        if (immersiveDraw != null && !next.seats().get(next.viewerSeat()).hand().contains(immersiveDraw.tile())) immersiveDraw = null;
        int viewer = next.viewerSeat();
        if (viewer >= 0) {
            var oldSeat = previous.seats().get(viewer);
            var newSeat = next.seats().get(viewer);
            if (newSeat.hand().size() == oldSeat.hand().size() + 1 && newSeat.drawn() != Tile.ABSENT && hand != null) {
                var target = hand.point(newSeat.drawn());
                if (target != null) immersiveDraw = new ImmersiveDrawMotion(newSeat.drawn(), Util.getMillis(), 340,
                    target, hand.tileWidth());
            }
        }
        for (int seat = 0; seat < next.seats().size(); seat++) {
            var before = previous.seats().get(seat).river();
            var after = next.seats().get(seat).river();
            if (after.size() != before.size() + 1) continue;
            var discard = after.getLast();
            if (discard.called()) continue;
            TableHand.Point source = null;
            int sourceWidth = 16;
            if (seat == next.viewerSeat()) {
                var oldHand = new TableHand(previous.seats().get(seat), seat, TableCanvas.WIDTH, handHeight, 58, true);
                source = oldHand.point(discard.tile(), oldSelected, oldHovered);
                if (source == null && discard.tsumogiri() && previous.seats().get(seat).drawn() != Tile.ABSENT)
                    source = oldHand.point(previous.seats().get(seat).drawn());
                sourceWidth = oldHand.tileWidth();
            }
            double opponentX = TableImmersiveTable.discardSourceX(TableBoardState.seat(previous.seats().get(seat)), seat,
                previous.viewerSeat(), previous.seats().size(), discard.tile(), discard.tsumogiri());
            long duration = ImmersiveMotion.duration(discard.tsumogiri());
            immersiveDiscard = new ImmersiveDiscardMotion(discard.tile(), seat, discard.tsumogiri(), discard.riichi(),
                Util.getMillis(), duration, source, sourceWidth, opponentX);
            return;
        }
    }

    private boolean immersiveDiscardActive(long now) {
        return TableSettings.get().animations && immersiveDiscard != null && now < immersiveDiscard.started() + immersiveDiscard.duration();
    }

    private boolean immersiveDrawActive(long now) {
        return TableSettings.get().animations && immersiveDraw != null && now < immersiveDraw.started() + immersiveDraw.duration();
    }

    private void renderImmersiveDiscard(GuiGraphics graphics, long now) {
        if (!immersiveDiscardActive(now) || board == null) return;
        var motion = immersiveDiscard;
        double fraction = Math.clamp((now - motion.started()) / (double) motion.duration(), 0, 1);
        board.discard(graphics, motion.tile(), motion.source(), motion.sourceWidth(), motion.opponentX(),
            motion.tsumogiri(), motion.riichi(), fraction);
    }

    private void renderImmersiveDraw(GuiGraphics graphics, long now) {
        if (!immersiveDrawActive(now) || board == null || hand == null) return;
        var motion = immersiveDraw;
        var source = board.drawSource();
        double fraction = Math.clamp((now - motion.started()) / (double) motion.duration(), 0, 1);
        double progress = ImmersiveMotion.smooth(fraction);
        double x = source.x() + (motion.target().x() - source.x()) * progress;
        double y = source.y() + (motion.target().y() - source.y()) * progress - Math.sin(Math.PI * fraction) * 22;
        int tileWidth = Math.max(16, (int) Math.round(20 + (motion.targetWidth() - 20) * progress));
        int tileHeight = Math.round(tileWidth * TileMesh.HEIGHT / TileMesh.WIDTH);
        TileGui.tile3d(graphics, motion.tile(), (int) Math.round(x) - tileWidth / 2,
            (int) Math.round(y) - tileHeight / 2, tileWidth, false, false, false, false,
            Math.max(2, tileWidth / 8), facePreset(), tileMaterial(), tileBack(), tileBackPreset());
    }

    private void buildToolbar(RiichiView view) {
        if (TableResults.available(view)) {
            int skip = findAction(view, RiichiAction.Type.SKIP_SETTLEMENT, List.of());
            boolean skipped = view.viewerSeat() >= 0
                && (view.settlementSkippedSeats() & (1 << view.viewerSeat())) != 0;
            int ticks = view.settlementTicks();
            boolean standings = view.phase() == RiichiView.Phase.MATCH_END && ticks > RiichiGame.SETTLEMENT_TICKS;
            int seconds = (Math.max(0, ticks - (standings ? RiichiGame.SETTLEMENT_TICKS : 0)) + 19) / 20;
            String key = view.phase() == RiichiView.Phase.HAND_END ? "ui.mchjong.next_hand"
                : standings ? "ui.mchjong.final_scores" : "ui.mchjong.lobby";
            int scale = immersive() ? 2 : 1;
            int countdownWidth = uiWidth() - 20 * scale;
            var readout = RiichiAudio.result(view);
            boolean reading = !view.wins().isEmpty() && ticks - (standings ? RiichiGame.SETTLEMENT_TICKS : 0) > RiichiGame.SETTLEMENT_TICKS;
            Component caption = Component.translatable(key, seconds);
            Component help = Component.translatable(skip < 0 || skipped
                ? "ui.mchjong.readout_waiting" : "action.mchjong.skip_settlement");
            if (skip >= 0 && !skipped && reading) help = help.copy().append("\n").append(Component.translatable(
                readout != null && !readout.complete() ? "ui.mchjong.readout_active" : "ui.mchjong.readout_waiting"));
            var countdown = MahjongButton.create(caption, ignored -> send(view, skip))
                .bounds(10 * scale, 8 * scale, countdownWidth, 20 * scale)
                .tooltip(Tooltip.create(help)).build().textScale(scale).selected(true);
            countdown.active = skip >= 0 && !skipped;
            addRenderableWidget(countdown);
            return;
        }
        TableToolbar.build(this, pos, room(), uiWidth(), immersive(), this::toggleView)
            .forEach(this::addRenderableWidget);
    }

    void configureVisibility(top.skyeyefast.mchjong.engine.PlayerHandVisibility visibility) {
        var room = room();
        if (minecraft.getConnection() == null || room == null) return;
        minecraft.getConnection().send(PayloadPackets.serverbound(
            new top.skyeyefast.mchjong.network.RiichiVisibilityPayload(pos, room.tableId(), room.decision(), visibility)));
    }

    void configureRules(TableRoomView room, RiichiRules rules) {
        if (minecraft.getConnection() == null || room == null || !room.lobby()) return;
        minecraft.getConnection().send(PayloadPackets.serverbound(
            new top.skyeyefast.mchjong.network.RiichiRulesPayload(pos, room.tableId(), room.decision(), rules)));
    }

    void control(TableRoomView room, RiichiControlPayload.Operation operation, long token, boolean enabled) {
        if (minecraft.getConnection() == null) return;
        minecraft.getConnection().send(PayloadPackets.serverbound(new RiichiControlPayload(pos, room.tableId(), operation, token, enabled)));
    }

    void control(RiichiView view, RiichiControlPayload.Operation operation, long token, boolean enabled) {
        if (minecraft.getConnection() == null) return;
        minecraft.getConnection().send(PayloadPackets.serverbound(new RiichiControlPayload(pos, view.tableId(), operation, token, enabled)));
    }

    private void sessionControl(java.util.UUID tableId, TableSessionControlPayload.Operation operation, long token, boolean enabled) {
        if (minecraft.getConnection() == null) return;
        minecraft.getConnection().send(PayloadPackets.serverbound(
            new TableSessionControlPayload(pos, tableId, operation, token, enabled)));
    }

    private void buildExitVote(RiichiView view) {
        var vote = view.exitVote();
        if (vote == null || view.viewerSeat() < 0) return;
        int layoutWidth = uiWidth(), layoutHeight = uiHeight();
        int scale = immersive() ? 2 : 1;
        int span = Math.min(360 * scale, layoutWidth - 24 * scale), left = (layoutWidth - span) / 2;
        int y = layoutHeight / 2 + 30 * scale;
        var agree = MahjongButton.create(Component.translatable("ui.mchjong.exit_agree"), ignored ->
            sessionControl(view.tableId(), TableSessionControlPayload.Operation.ANSWER_EXIT, vote.id(), true)).bounds(left, y, (span - 4 * scale) / 2, 20 * scale).build().textScale(scale);
        agree.active = !vote.agreed().contains(view.viewerSeat());
        addRenderableWidget(agree);
        addRenderableWidget(MahjongButton.create(Component.translatable("ui.mchjong.exit_reject"), ignored ->
            sessionControl(view.tableId(), TableSessionControlPayload.Operation.ANSWER_EXIT, vote.id(), false)).bounds(left + (span + 4 * scale) / 2, y, (span - 4 * scale) / 2, 20 * scale).build().textScale(scale));
    }

    private void buildLobby(TableRoomView room, RiichiRoomSettings settings) {
        lobby.build(room, width, height).forEach(this::addRenderableWidget);
    }

    boolean automatic() {
        return minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table && table.automatic();
    }

    top.skyeyefast.mchjong.engine.MahjongVariant variant() {
        return minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table
            ? table.clientVariant() : top.skyeyefast.mchjong.engine.MahjongVariant.RIICHI;
    }

    void send(RiichiView snapshot, int index) {
        RiichiView current = view();
        refreshDecision(current);
        if (dealing() || TableResults.available(snapshot) && Util.getMillis() - resultStarted < 500
            || minecraft.getConnection() == null) return;
        if (index >= 0 && index < snapshot.actions().size()
            && snapshot.actions().get(index).type() == RiichiAction.Type.SKIP_SETTLEMENT && RiichiAudio.finishResult()) {
            rebuild();
            return;
        }
        if (!decision.submit(snapshot, index)) return;
        minecraft.getConnection().send(PayloadPackets.serverbound(new RiichiActionPayload(pos, snapshot.tableId(), snapshot.decision(), index)));
        callouts.forEach(button -> button.active = false);
        selectedTile = lastClickedTile = Tile.ABSENT;
        choosingRiichi = false;
        if (confirmButton != null) confirmButton.active = false;
    }

    void sendRoom(top.skyeyefast.mchjong.engine.TableRoomView room, int index) {
        RoomLobbyControls.send(pos, room, index);
    }

    private static boolean showsConsumed(RiichiAction action) {
        return ActionPreview.consumesHand(action);
    }

    private int actionPreviewWidth(RiichiView view, RiichiAction action) {
        ActionPreview preview = ActionPreview.of(view, action);
        if (preview.meld() != null) return TileGui.meldWidth(preview.meld(), view.viewerSeat(), 11) + 8;
        return preview.tiles().isEmpty() ? 0 : preview.tiles().size() * 13 + 6;
    }

    private Vec3 anchor(RiichiView view, RiichiAction action) {
        if (view.focus() != null) {
            RiichiTableScene.Area area = view.focus().declaration() ? RiichiTableScene.Area.HAND : RiichiTableScene.Area.RIVER;
            for (RiichiTableScene.Piece piece : area == RiichiTableScene.Area.HAND ? settledScene : scene)
                if (piece.area() == area && piece.seat() == view.focus().seat() && piece.index() == view.focus().index()) return piece.position();
        }
        if (tileChoice(action) || action.type() == RiichiAction.Type.TSUMO) {
            for (RiichiTableScene.Piece piece : settledScene)
                if (piece.area() == RiichiTableScene.Area.HAND && piece.seat() == view.viewerSeat()
                    && (action.tiles().contains(piece.tile()) || action.type() == RiichiAction.Type.TSUMO
                        && piece.tile() == view.seats().get(view.viewerSeat()).drawn())) return piece.position();
        }
        if (action.type() == RiichiAction.Type.ABORT_NINE && view.viewerSeat() >= 0)
            return TableGeometry.orient(0, TableGeometry.FELT_Y + 0.08, RiichiTableScene.HAND_Z, view.viewerSeat());
        return new Vec3(0, TableGeometry.FELT_Y + 0.045, 0);
    }


    private SeatedTableProjection.Point projectHand(RiichiTableScene.Piece piece) {
        if (hand != null && hand.centerX(piece.tile()) >= 0)
            return new SeatedTableProjection.Point(hand.centerX(piece.tile()), hand.top(), 1);
        return project(piece.position());
    }

    private SeatedTableProjection.Point project(Vec3 relative) {
        if (immersive()) return null;
        return projection().project(relative, .05);
    }

    private int pick(double mouseX, double mouseY) {
        RiichiView view = view();
        if (view == null || view.viewerSeat() < 0 || view.exitVote() != null || dealing() || TableResults.available(view)
            || overWidget(mouseX, mouseY) || overInformation(mouseX, mouseY)) return Tile.ABSENT;
        if (hand != null) {
            int tile = hand.pick(mouseX, mouseY, selectedTile);
            return choosingRiichi && tileAction(view, tile, RiichiAction.Type.RIICHI) < 0 ? Tile.ABSENT : tile;
        }
        if (immersive()) return Tile.ABSENT;
        SeatedTableProjection.Pointer pointer = pointer(mouseX, mouseY);
        RiichiTableScene.Piece best = null;
        double closest = Double.MAX_VALUE;
        for (RiichiAnimation.Frame frame : frames) {
            RiichiTableScene.Piece piece = frame.piece();
            if (piece.area() != RiichiTableScene.Area.HAND || piece.seat() != view.viewerSeat()) continue;
            if (choosingRiichi && tileAction(view, piece.tile(), RiichiAction.Type.RIICHI) < 0) continue;
            double distance = TilePicking.distanceSquared(frame, pointer.origin(), pointer.ray(), selected(pos, piece));
            if (distance < closest) {
                closest = distance;
                best = piece;
            }
        }
        return best == null ? Tile.ABSENT : best.tile();
    }

    private double handTileCenterX(int tile) {
        RiichiView view = view();
        if (view == null) return width / 2.0;
        for (RiichiTableScene.Piece piece : settledScene) {
            if (piece.area() != RiichiTableScene.Area.HAND || piece.seat() != view.viewerSeat() || piece.tile() != tile) continue;
            SeatedTableProjection.Point center = projectHand(piece);
            if (center != null) return center.x();
        }
        return width / 2.0;
    }

    private SeatedTableProjection projection() { return SeatedTableProjection.capture(pos, width, height, framePartial); }
    private SeatedTableProjection.Pointer pointer(double mouseX, double mouseY) { return projection().pointer(mouseX, mouseY); }

    private Vec3 tablePoint(double mouseX, double mouseY) {
        SeatedTableProjection.Pointer pointer = pointer(mouseX, mouseY);
        if (Math.abs(pointer.ray().y) < 1e-6) return null;
        double distance = (TableGeometry.FELT_Y - pointer.origin().y) / pointer.ray().y;
        return distance > 0 ? pointer.origin().add(pointer.ray().scale(distance)) : null;
    }

    private RiichiTableScene.Piece pickPhysical(double mouseX, double mouseY) {
        RiichiView view = view();
        if (immersive() || RiichiHandling.action(view) < 0 || handlingMoving() || decision.pending()
            || results != null || overWidget(mouseX, mouseY) || overInformation(mouseX, mouseY)) return null;
        SeatedTableProjection.Pointer pointer = pointer(mouseX, mouseY);
        RiichiTableScene.Piece nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (var frame : frames) {
            if (!RiichiHandling.source(view, frame.piece())) continue;
            double candidate = TilePicking.distanceSquared(frame, pointer.origin(), pointer.ray(), false, .018);
            if (candidate < distance) { distance = candidate; nearest = frame.piece(); }
        }
        return nearest;
    }

    public Vec3 handlingOffset(BlockPos table, RiichiTableScene.Piece piece) {
        if (!pos.equals(table) || handlingDrag == null || handlingStart == null || handlingPointer == null) return Vec3.ZERO;
        int index = RiichiHandling.action(handlingDrag);
        if (index < 0) return Vec3.ZERO;
        boolean held = switch (handlingDrag.actions().get(index).type()) {
            case SHUFFLE -> piece.area() == RiichiTableScene.Area.LOOSE && piece.position().distanceToSqr(handlingStart) < .2;
            case BUILD_WALL -> piece.area() == RiichiTableScene.Area.LOOSE && piece.seat() == handlingDrag.viewerSeat();
            case TAKE_PACKET, DRAW -> RiichiHandling.source(handlingDrag, piece);
            case NEXT -> piece.area() != RiichiTableScene.Area.WALL && piece.seat() == handlingDrag.viewerSeat();
            default -> false;
        };
        return held ? handlingPointer.subtract(handlingStart).add(0, .05, 0) : Vec3.ZERO;
    }

    private boolean openOwnDrawer() {
        TableRoomView room = room();
        RiichiView view = view();
        int side;
        if (room != null && room.lobby()) {
            if (!room.manual() || room.viewerSeat() < 0 || room.exitVote() != null || minecraft.gameMode == null) return false;
            side = room.viewerSeat();
        } else {
            if (view == null || view.handling() == null || view.viewerSeat() < 0 || view.exitVote() != null || minecraft.gameMode == null) return false;
            side = view.viewerSeat();
        }
        Vec3 location = TableGeometry.world(pos, TableGeometry.drawerBounds(side).getCenter());
        var hit = new net.minecraft.world.phys.BlockHitResult(location, TableGeometry.SIDES[side], BlockPos.containing(location), false);
        handlingDrag = null;
        minecraft.gameMode.useItemOn(minecraft.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        return true;
    }

    private boolean openDrawer(double mouseX, double mouseY) {
        RiichiView view = view();
        if (immersive() || view == null || view.handling() == null || view.viewerSeat() < 0 || view.exitVote() != null
            || results != null || minecraft.gameMode == null) return false;
        SeatedTableProjection.Pointer pointer = pointer(mouseX, mouseY);
        // Use vanilla block interaction: native reach, loaded-block and container checks stay on the server.
        Vec3 start = TableGeometry.world(pos, pointer.origin());
        Vec3 end = start.add(pointer.ray().normalize().scale(6));
        var hit = minecraft.level.clip(new net.minecraft.world.level.ClipContext(start, end,
            net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, minecraft.player));
        if (!(minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table) || table.drawerAt(hit) < 0) return false;
        minecraft.gameMode.useItemOn(minecraft.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        return true;
    }

    private Vec3 grip(RiichiTableScene.Piece piece) {
        Vec3 eye = minecraft.gameRenderer.getMainCamera().getPosition().subtract(TableGeometry.world(pos, Vec3.ZERO));
        return RiichiHandling.grip(piece, eye);
    }

    private boolean overWidget(double x, double y) {
        return children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> !(widget instanceof PhysicalHandle))
            .anyMatch(widget -> widget.visible && x >= widget.getX() && x < widget.getRight() && y >= widget.getY() && y < widget.getBottom());
    }

    private boolean overInformation(double x, double y) {
        return information.contains(x, y);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        framePartial = partialTick;
        TableRoomView room = room();
        if (room != null && room.lobby()) {
            if (room.revision() != lastRevision) rebuild();
            renderLobby(graphics, mouseX, mouseY, partialTick, room);
            return;
        }
        RiichiView view = view();
        if (view == null) return;
        if (view.revision() != lastRevision) rebuild();
        updateScene();
        information.clear();
        boolean canvas = immersive();
        int drawMouseX = mouseX, drawMouseY = mouseY;
        TableCanvas transform = canvas();
        if (canvas) {
            drawMouseX = (int) Math.floor(transform.localX(mouseX));
            drawMouseY = (int) Math.floor(transform.localY(mouseY));
        }
        transform.begin(graphics);
        int layoutWidth = uiWidth(), layoutHeight = uiHeight();
        try {
        if (immersive()) {
            graphics.fill(0, 0, layoutWidth, layoutHeight, MahjongUi.INPUT);
            long now = Util.getMillis();
            int suppressed = immersiveDiscardActive(now) ? immersiveDiscard.tile() : Tile.ABSENT;
            if (board != null) board.render(graphics, TableBoardState.live(view), facePreset(), suppressed,
                tileMaterial(), tileBack(), tileBackPreset(), clothColor(), dealing() ? animation() : null, now);
            renderImmersiveDiscard(graphics, now);
            renderImmersiveDraw(graphics, now);
        }
        if (view.exitVote() != null) {
            renderExitVote(graphics, view);
            super.render(graphics, drawMouseX, drawMouseY, partialTick);
            return;
        }
        if (!TableResults.available(view) || immersive() && results == null)
            information.render(font, graphics, view, room(), botService(), layoutWidth, facePreset(), tileMaterial(), tileBack(), tileBackPreset(), board);
        var dicePoint = !immersive() ? project(new Vec3(0, TableGeometry.FELT_Y + .075, -.06)) : null;
        int diceX = dicePoint == null ? -100 : (int) dicePoint.x();
        int diceY = dicePoint == null ? -100 : (int) dicePoint.y();
        int diceWidth = dicePoint == null ? 24 : Math.max(24, (int) (dicePoint.scale() * .24));
        dice.update(view, diceX - diceWidth / 2, diceY - 10, diceWidth, 20, !immersive(), decision.pending());
        layoutTurnControls(view);
        hoveredTile = transform.contains(mouseX, mouseY) ? pick(drawMouseX, drawMouseY) : Tile.ABSENT;
        renderHandling(graphics, view, drawMouseX, drawMouseY);
        informationTooltip = information.tooltip(drawMouseX, drawMouseY);
        TableSettings settings = TableSettings.get();
        boolean inputEnabled = !decision.pending() && !dealing()
            && (!TableResults.available(view) || Util.getMillis() - resultStarted >= 500);
        callouts.forEach(button -> button.active = inputEnabled);
        if (confirmButton != null) confirmButton.active = inputEnabled;
        for (CalloutButton button : callouts) {
            boolean guide = settings.guideLines == TableSettings.GuideLines.ALWAYS
                || settings.guideLines == TableSettings.GuideLines.HOVER && button.isHoveredOrFocused();
            if (guide && ActionPreview.hasGuide(button.action)) {
                SeatedTableProjection.Point point = actionPoint(view, button.action);
                if (point != null) elbow(graphics, point, button, button.isHoveredOrFocused() ? 0xffffdc89 : 0xff8fbca9);
            }
            if (settings.highlightTiles && showsConsumed(button.action) && button.isHoveredOrFocused()) {
                for (RiichiTableScene.Piece piece : scene) if (piece.area() == RiichiTableScene.Area.HAND && piece.seat() == view.viewerSeat()
                    && button.action.tiles().contains(piece.tile())) {
                    SeatedTableProjection.Point own = projectHand(piece);
                    if (own != null) elbow(graphics, own, button, 0xffdfc98e);
                }
            }
        }
        int statusScale = immersive() ? 2 : 1;
        if (choosingRiichi) renderStatus(graphics, Component.translatable("ui.mchjong.choose_riichi"),
            actionTop - 14 * statusScale, MahjongUi.ACCENT);
        if (hand != null && results == null) hand.render(graphics, selectedTile, hoveredTile, tile -> {
            for (var piece : scene) if (piece.area() == RiichiTableScene.Area.HAND && piece.seat() == view.viewerSeat() && piece.tile() == tile)
                return highlight(pos, piece);
            return 0;
        }, handDrag != null && handDragMoved ? handDragTile
            : immersiveDrawActive(Util.getMillis()) ? immersiveDraw.tile() : Tile.ABSENT,
            facePreset(), tileMaterial(), tileBack(), tileBackPreset(),
            dealing() ? animation() : null, board == null ? null : board.drawSource(), Util.getMillis());
        if (handDrag != null && handDragMoved) {
            int tileWidth = hand == null ? Math.min(32, Math.max(16, uiWidth() / 14)) : hand.tileWidth();
            int tileHeight = Math.round(tileWidth * TileMesh.HEIGHT / TileMesh.WIDTH);
            TileGui.tile3d(graphics, handDragTile, (int) handDragX - tileWidth / 2,
                (int) handDragY - tileHeight / 2, tileWidth, false, false, false, false,
                Math.max(2, tileWidth / 8), facePreset(), tileMaterial(), tileBack(), tileBackPreset());
        }
        updateHints(view);
        super.render(graphics, drawMouseX, drawMouseY, partialTick);
        if (dealing()) renderStatus(graphics, Component.translatable(immersive() ? "ui.mchjong.dealing.immersive" : "ui.mchjong.dealing"),
            actionTop - 14 * statusScale, MahjongUi.ACCENT);
        else if (TableSettings.get().animations && animation() != null && !TableResults.available(view)) {
            var cues = animation().cues(Util.getMillis());
            int cueY = actionTop - ((choosingRiichi ? 14 : 0) + cues.size() * 13 + 1) * statusScale;
            for (var cue : cues) {
                renderStatus(graphics, playerName(view, cue.seat()).copy().append("  ").append(Component.translatable(cue.key())),
                    cueY, MahjongUi.ACCENT);
                cueY += 13 * statusScale;
            }
        }
        if (!turnClock.visible && settings.show(TableSettings.Information.HELP)) {
            String helpKey = TableResults.available(view) ? "ui.mchjong.result_help" : view.viewerSeat() < 0 ? "ui.mchjong.spectator_help"
                : choosingRiichi ? "ui.mchjong.riichi_help" : "ui.mchjong.help." + settings.discardMode.name().toLowerCase(java.util.Locale.ROOT);
            Component help = choosingRiichi || TableResults.available(view) || view.viewerSeat() < 0
                ? Component.translatable(helpKey)
                : Component.translatable(helpKey, TableKeys.RIICHI.getTranslatedKeyMessage(), TableKeys.PASS.getTranslatedKeyMessage());
            if (view.handling() != null && view.viewerSeat() >= 0)
                help = Component.translatable("sticks.mchjong.access", TableKeys.DRAWER.getTranslatedKeyMessage()).append("  ").append(help);
            renderFooter(graphics, help, 0xffe0deca);
        }
        if (informationTooltip != null && !overWidget(drawMouseX, drawMouseY)) {
            graphics.pose().pushPose();
            graphics.pose().scale(statusScale, statusScale, 1);
            graphics.renderTooltip(font, font.split(informationTooltip, Math.min(320, layoutWidth / statusScale - 24)),
                (screenWidth, screenHeight, x, y, w, h) -> net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE
                    .positionTooltip(layoutWidth / statusScale, layoutHeight / statusScale, x, y, w, h),
                drawMouseX / statusScale, drawMouseY / statusScale);
            graphics.pose().popPose();
        }
        hints.renderPopup(graphics, font, facePreset());
        dice.renderTooltip(graphics, drawMouseX, drawMouseY);
        } finally {
            transform.end(graphics);
        }
    }

    private void renderLobby(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, TableRoomView room) {
        information.clear();
        lobby.paint(graphics, room, width, height, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void layoutTurnControls(RiichiView view) {
        if (TableResults.available(view) || view.viewerSeat() < 0) return;
        int scale = immersive() ? 2 : 1;
        privateHandBounds = privateHandBounds(view);
        int bottom = privateHandBounds.top() - 6 * scale;
        // Keep a stable clock lane even between turns, so controls never jump as time expires.
        int captions = (choosingRiichi ? 14 : 0) + (dealing() ? 14
            : TableSettings.get().animations && animation() != null ? animation().cues(Util.getMillis()).size() * 13 : 0);
        if (!immersive() && bottom - 28 - actionHeight - captions < information.bottom() + 4
            && privateHandBounds.bottom() + 6 + captions + actionHeight + 28 <= uiHeight() - 24)
            bottom = uiHeight() - 24;
        int top = bottom - 28 * scale - actionHeight;
        int shift = top - actionTop;
        for (var button : decisionButtons) button.setY(button.getY() + shift);
        actionTop = top;
        if (view.viewerSeat() < view.clocks().size()) {
            var table = (MahjongTableBlockEntity) minecraft.level.getBlockEntity(pos);
            turnClock.update(view.clocks().get(view.viewerSeat()).after(table.clientViewAgeMillis()),
                uiWidth() - 16 * scale, bottom, scale);
        }
    }

    private ScreenRectangle privateHandBounds(RiichiView view) {
        if (hand != null) return new ScreenRectangle(0, hand.top(), uiWidth(), uiHeight() - hand.top());
        double top = Double.POSITIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY;
        double left = Double.POSITIVE_INFINITY, right = Double.NEGATIVE_INFINITY;
        boolean standing = settledFrames.stream().anyMatch(frame -> frame.piece().seat() == view.viewerSeat()
            && frame.piece().area() == RiichiTableScene.Area.HAND && !frame.piece().flat());
        for (var frame : settledFrames) {
            var piece = frame.piece();
            if (piece.seat() != view.viewerSeat() || piece.area() != RiichiTableScene.Area.HAND
                || standing && piece.flat()) continue;
            // Project the hand at its table position, reserving the maximum selection lift.
            var transform = new org.joml.Matrix4f().translation((float) piece.position().x,
                (float) piece.position().y, (float) piece.position().z)
                .rotateY((float) Math.toRadians(piece.yaw())).rotateX((float) Math.toRadians(frame.pitch()))
                .scale(RiichiTableScene.TILE_SCALE);
            for (int x = -1; x <= 1; x += 2) for (int y = -1; y <= 1; y += 2) for (int z = -1; z <= 1; z += 2) {
                var corner = transform.transformPosition(new org.joml.Vector3f(x * TileMesh.WIDTH / 2,
                    y * TileMesh.HEIGHT / 2, z * TileMesh.DEPTH / 2));
                for (int lift = 0; lift <= (piece.area() == RiichiTableScene.Area.HAND ? 1 : 0); lift++) {
                    var point = project(new Vec3(corner.x, corner.y + lift * .035, corner.z));
                    if (point != null) {
                        top = Math.min(top, point.y());
                        bottom = Math.max(bottom, point.y());
                        left = Math.min(left, point.x());
                        right = Math.max(right, point.x());
                    }
                }
            }
        }
        if (top >= uiHeight() || bottom <= 0 || left >= uiWidth() || right <= 0)
            return new ScreenRectangle(0, uiHeight() - 24, uiWidth(), 0);
        int x = (int) Math.max(0, Math.floor(left)), y = (int) Math.max(0, Math.floor(top));
        return new ScreenRectangle(x, y, (int) Math.min(uiWidth(), Math.ceil(right)) - x,
            (int) Math.min(uiHeight(), Math.ceil(bottom)) - y);
    }

    private void renderStatus(GuiGraphics graphics, Component text, int y, int color) {
        int scale = immersive() ? 2 : 1;
        int left = immersive() ? Math.min(actionLeft, uiWidth() - 560) : actionLeft;
        graphics.pose().pushPose();
        graphics.pose().translate(left, y, 0);
        graphics.pose().scale(scale, scale, 1);
        MahjongUi.text(graphics, font, text, 0, 0,
            (uiWidth() - left - (board == null && hints.visible ? 36 : 10)) / scale, color, true, true);
        graphics.pose().popPose();
    }

    private void renderFooter(GuiGraphics graphics, Component text, int color) {
        int scale = immersive() ? 2 : 1;
        int left = immersive() ? 208 : 10;
        graphics.pose().pushPose();
        graphics.pose().translate(left, uiHeight() - 13 * scale, 0);
        graphics.pose().scale(scale, scale, 1);
        int available = (uiWidth() - left - (hints.visible ? 48 * scale : 10)) / scale;
        if (TableResults.available(view())) {
            var lines = font.split(text, available);
            for (int line = 0; line < lines.size(); line++)
                graphics.drawString(font, lines.get(line), 0, (line - lines.size() + 1) * 11, color, true);
        } else graphics.drawString(font, font.plainSubstrByWidth(text.getString(), available), 0, 0, color, true);
        graphics.pose().popPose();
    }

    private void updateHints(RiichiView view) {
        if (room() == null || !room().convenienceHints() || dealing() || decision.pending() || results != null) {
            hints.clearPreview();
            return;
        }
        int layoutWidth = uiWidth(), layoutHeight = uiHeight();
        int hintBottom = privateHandBounds == null ? layoutHeight - 52 : privateHandBounds.top();
        int hintCenter = hand == null ? layoutWidth / 2 : hand.centerX();
        double handLeft = Double.POSITIVE_INFINITY, handRight = Double.NEGATIVE_INFINITY;
        boolean standing = settledScene.stream().anyMatch(piece -> piece.area() == RiichiTableScene.Area.HAND
            && piece.seat() == view.viewerSeat() && !piece.flat());
        if (hand == null) for (var piece : settledScene) {
            if (piece.area() != RiichiTableScene.Area.HAND || piece.seat() != view.viewerSeat()
                || standing && piece.flat()) continue;
            var point = project(piece.position().add(0,
                (piece.flat() ? TileMesh.DEPTH : TileMesh.HEIGHT) * RiichiTableScene.TILE_SCALE / 2.0, 0));
            if (point != null) {
                handLeft = Math.min(handLeft, point.x());
                handRight = Math.max(handRight, point.x());
            }
        }
        if (handLeft != Double.POSITIVE_INFINITY) hintCenter = (int) Math.round((handLeft + handRight) / 2);
        int hintScale = immersive() ? 2 : 1;
        int halfWidth = information.hintHalfWidth(hintCenter, hintBottom, Math.min(hintCenter - 8, layoutWidth - 8 - hintCenter), 57 * hintScale);
        for (var child : children()) if (child instanceof AbstractWidget widget && widget != hints && widget != dice
            && widget.visible && widget.getY() < hintBottom && widget.getBottom() > hintBottom - 57 * hintScale) {
            if (widget.getX() > hintCenter) halfWidth = Math.min(halfWidth, widget.getX() - hintCenter - 4);
            else if (widget.getRight() < hintCenter) halfWidth = Math.min(halfWidth, hintCenter - widget.getRight() - 4);
        }
        int hintLeft = hintCenter - halfWidth, hintRight = hintCenter + halfWidth;
        hints.update(view, hoveredTile, selectedTile, layoutWidth, board == null ? actionTop - 22 : layoutHeight - 16 * hintScale, hintLeft, hintRight,
            hintBottom, board == null ? information.bottom() + 4 : 38, hintScale);
    }

    private void renderHandling(GuiGraphics graphics, RiichiView view, int mouseX, int mouseY) {
        if (immersive()) return;
        int index = RiichiHandling.action(view);
        if (index < 0 || results != null) return;
        RiichiTableScene.Piece source = RiichiHandling.source(view, scene);
        if (source != null) {
            SeatedTableProjection.Point target = project(RiichiHandling.destination(view));
            if (target != null && handlingDrag != null) {
                int halfWidth = Math.clamp((int) Math.round(target.scale() * .45), 36, Math.max(36, Math.min(120, width / 4)));
                int halfHeight = Math.clamp((int) Math.round(target.scale() * .12), 12, 28);
                graphics.fill((int) target.x() - halfWidth, (int) target.y() - halfHeight,
                    (int) target.x() + halfWidth, (int) target.y() + halfHeight, 0x443c8c68);
                graphics.renderOutline((int) target.x() - halfWidth, (int) target.y() - halfHeight,
                    halfWidth * 2, halfHeight * 2, MahjongUi.POSITIVE);
                graphics.hLine(Math.min(mouseX, (int) target.x()), Math.max(mouseX, (int) target.x()), mouseY, MahjongUi.POSITIVE);
                graphics.vLine((int) target.x(), Math.min(mouseY, (int) target.y()), Math.max(mouseY, (int) target.y()), MahjongUi.POSITIVE);
            }
        }
        Component help = Component.translatable(RiichiHandling.help(view));
        var lines = font.split(help, width - 24);
        int y = height - 29 - lines.size() * 10;
        for (var line : lines) {
            graphics.drawCenteredString(font, line, width / 2, y, MahjongUi.ACCENT);
            y += 10;
        }
    }

    private void renderExitVote(GuiGraphics graphics, RiichiView view) {
        var vote = view.exitVote();
        int scale = immersive() ? 2 : 1;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1);
        int layoutWidth = uiWidth() / scale, layoutHeight = uiHeight() / scale;
        int span = Math.min(360, layoutWidth - 24), left = (layoutWidth - span) / 2, top = layoutHeight / 2 - 64;
        graphics.fill(left - 6, top, left + span + 6, layoutHeight / 2 + 56, 0xf21a2a2e);
        graphics.renderOutline(left - 6, top, span + 12, 120, 0xffc4a469);
        graphics.drawCenteredString(font, Component.translatable("ui.mchjong.exit_title"), layoutWidth / 2, top + 9, 0xffffd487);
        int y = top + 25;
        Component requester = Component.translatable("ui.mchjong.exit_requester", playerName(view, vote.requester()));
        for (var line : font.split(requester, span - 12)) {
            if (y > top + 38) break;
            graphics.drawCenteredString(font, line, layoutWidth / 2, y, 0xffe0eade); y += 10;
        }
        graphics.drawCenteredString(font, Component.translatable("ui.mchjong.exit_status", vote.agreed().size(), vote.required(), vote.secondsLeft()),
            layoutWidth / 2, top + 50, 0xffffd487);
        y = top + 65;
        for (var line : font.split(Component.translatable("ui.mchjong.exit_paused"), span - 12)) {
            graphics.drawCenteredString(font, line, layoutWidth / 2, y, 0xffadd8c4); y += 10;
        }
        graphics.pose().popPose();
    }

    public static Component playerName(RiichiView view, int seat) {
        RiichiView.Seat player = view.seats().get(seat);
        if (!player.occupied()) return Component.translatable("ui.mchjong.empty");
        if (player.bot() && !player.entityBot()) return Component.translatable("ui.mchjong.bot", seat + 1);
        return player.entityBot() ? Component.translatable(player.name()) : Component.literal(player.name());
    }

    public static Component playerName(TableRoomView room, int seat) {
        var player = room.seats().get(seat).participant();
        if (player.id() == null) return Component.translatable("ui.mchjong.empty");
        if (player.bot() && !player.entityBot()) return Component.translatable("ui.mchjong.bot", seat + 1);
        return player.entityBot() ? Component.translatable(player.name()) : Component.literal(player.name());
    }

    private static void elbow(GuiGraphics graphics, SeatedTableProjection.Point point, CalloutButton button, int color) {
        int x = (int) point.x(), y = (int) point.y();
        int endX = button.getX() - 4, endY = button.getY() + button.getHeight() / 2;
        int bendX = endX - 18;
        graphics.hLine(Math.min(x, bendX), Math.max(x, bendX), y, color);
        graphics.vLine(bendX, Math.min(y, endY), Math.max(y, endY), color);
        graphics.hLine(bendX, endX, endY, color);
        graphics.fill(x - 2, y - 2, x + 2, y + 2, color);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mappedClick(button)) return true;
        if (immersive()) {
            if (!insideImmersiveCanvas(mouseX, mouseY)) return false;
            mouseX = canvasX(mouseX);
            mouseY = canvasY(mouseY);
        }
        if (overWidget(mouseX, mouseY)) { super.mouseClicked(mouseX, mouseY, button); return true; }
        if (overInformation(mouseX, mouseY)) return true;
        if (room() != null && room().lobby()) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 1) { presentation.startDrag(); return true; }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            if (decision.pending() || dealing()) return true;
            updateScene();
            boolean overHand = hand != null && hand.contains(mouseX, mouseY);
            if (!overHand && openDrawer(mouseX, mouseY)) return true;
            RiichiTableScene.Piece physical = overHand ? null : pickPhysical(mouseX, mouseY);
            if (physical != null) {
                handlingDrag = view();
                handlingStart = tablePoint(mouseX, mouseY);
                handlingPointer = handlingStart;
                setFocused(null);
                return true;
            }
            int tile = pick(mouseX, mouseY);
            if (tile >= 0 && choosingRiichi) {
                RiichiView snapshot = view();
                int action = tileAction(snapshot, tile, RiichiAction.Type.RIICHI);
                if (action >= 0) {
                    send(snapshot, action);
                    return true;
                }
            }
            RiichiView snapshot = view();
            if (tile >= 0 && !choosingRiichi && !hasShiftDown() && snapshot != null
                && snapshot.autoPlay() != null && !snapshot.autoPlay().sort()) {
                handDrag = snapshot;
                handDragTile = tile;
                handDragStartX = handDragX = mouseX;
                handDragStartY = handDragY = mouseY;
                handDragMoved = false;
                selectedTile = tile;
                setFocused(null);
                rebuild();
                return true;
            }
            if (tile >= 0 && !choosingRiichi && !hasShiftDown() && discardFromClick(tile)) return true;
            selectedTile = tile;
            lastClickedTile = Tile.ABSENT;
            setFocused(null);
            rebuild();
            return true;
        }
        return false;
    }

    private boolean discardFromClick(int tile) {
        RiichiView view = view();
        if (view == null) return false;
        int action = discardAction(view, tile);
        if (action < 0) return false;
        TableSettings.DiscardMode mode = TableSettings.get().discardMode;
        long now = Util.getMillis();
        if (mode == TableSettings.DiscardMode.SINGLE_CLICK) {
            send(view, action);
            return true;
        }
        if (mode == TableSettings.DiscardMode.DOUBLE_CLICK && lastClickedTile == tile && now - lastClickAt <= 450) {
            lastClickedTile = Tile.ABSENT;
            send(view, action);
            return true;
        }
        lastClickedTile = tile;
        lastClickAt = now;
        selectedTile = tile;
        rebuild();
        setFocused(null);
        return true;
    }

    private static int discardAction(RiichiView view, int tile) {
        return tileAction(view, tile, RiichiAction.Type.DISCARD);
    }

    private static int tileAction(RiichiView view, int tile, RiichiAction.Type type) {
        for (int i = 0; i < view.actions().size(); i++) {
            RiichiAction candidate = view.actions().get(i);
            if (candidate.type() == type && candidate.tiles().contains(tile)) return i;
        }
        return -1;
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (presentation.releaseInspect(button)) return true;
        if (immersive()) {
            if (!insideImmersiveCanvas(mouseX, mouseY) && handDrag == null) return false;
            mouseX = canvasX(mouseX);
            mouseY = canvasY(mouseY);
        }
        if (button == 0 && handDrag != null) {
            RiichiView snapshot = handDrag;
            int tile = handDragTile;
            boolean moved = handDragMoved || Math.hypot(mouseX - handDragStartX, mouseY - handDragStartY) > 4;
            double startY = handDragStartY;
            handDrag = null;
            handDragTile = Tile.ABSENT;
            RiichiView current = view();
            if (current == null || !snapshot.tableId().equals(current.tableId()) || snapshot.decision() != current.decision()
                || current.autoPlay() == null || current.autoPlay().sort()) return true;
            if (!moved) {
                if (!discardFromClick(tile)) { selectedTile = tile; rebuild(); }
            } else if (mouseY <= startY - (immersive() ? 96 : 48)) {
                int action = discardAction(current, tile);
                if (action >= 0) send(current, action);
            } else {
                int target = pick(mouseX, mouseY);
                if (target >= 0 && target != tile && minecraft.getConnection() != null) {
                    double center = hand == null ? handTileCenterX(target) : hand.centerX(target);
                    minecraft.getConnection().send(PayloadPackets.serverbound(
                        new top.skyeyefast.mchjong.network.RiichiHandOrderPayload(pos, current.tableId(),
                            current.decision(), tile, target, mouseX > center)));
                }
            }
            return true;
        }
        if (button == 0 && handlingDrag != null) {
            RiichiView snapshot = handlingDrag;
            handlingDrag = null;
            if (view() != null && snapshot.tableId().equals(view().tableId()) && snapshot.decision() == view().decision()
                && !overWidget(mouseX, mouseY) && !overInformation(mouseX, mouseY)
                && RiichiHandling.completes(snapshot, handlingStart, tablePoint(mouseX, mouseY)))
                send(snapshot, RiichiHandling.action(snapshot));
            handlingStart = null;
            return true;
        }
        if (presentation.releaseDrag(button, this::cancelSelection)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (immersive()) {
            if (!insideImmersiveCanvas(mouseX, mouseY) && handDrag == null) return false;
            double scale = canvas().scale();
            mouseX = canvasX(mouseX);
            mouseY = canvasY(mouseY);
            dx /= scale;
            dy /= scale;
        }
        if (button == 0 && handDrag != null) {
            handDragX = mouseX;
            handDragY = mouseY;
            handDragMoved |= Math.hypot(mouseX - handDragStartX, mouseY - handDragStartY) > 4;
            return true;
        }
        if (button == 0 && handlingDrag != null) { handlingPointer = tablePoint(mouseX, mouseY); return true; }
        if (presentation.drag(button, dx, dy, hasShiftDown())) return true;
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        RiichiView view = view();
        if (key == GLFW.GLFW_KEY_ESCAPE && handlingDrag != null) { handlingDrag = null; handlingStart = null; return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE && handDrag != null) { handDrag = null; return true; }
        if (TableKeys.DRAWER.matches(key, scanCode) && openOwnDrawer()) return true;
        if (results != null && results.isFocused() && results.keyPressed(key, scanCode, modifiers)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE && (choosingRiichi || selectedTile >= 0)) { cancelSelection(); return true; }
        if (presentation.keyPressed(key, scanCode, this::toggleView, this::resetView)) return true;
        if (TableKeys.RIICHI.matches(key, scanCode) && view != null && view.actions().stream().anyMatch(action -> action.type() == RiichiAction.Type.RIICHI)) {
            toggleRiichi(); return true;
        }
        if (TableKeys.PASS.matches(key, scanCode) && view != null) {
            for (int i = 0; i < view.actions().size(); i++) if (view.actions().get(i).type() == RiichiAction.Type.PASS) { send(view, i); return true; }
        }
        if (presentation.lookPressed(key)) return true;
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && selectedTile >= 0 && view != null && getFocused() == null) {
            int action = tileAction(view, selectedTile, choosingRiichi ? RiichiAction.Type.RIICHI : RiichiAction.Type.DISCARD);
            if (action >= 0) send(view, action);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public boolean keyReleased(int key, int scanCode, int modifiers) {
        return presentation.keyReleased(key, scanCode) || super.keyReleased(key, scanCode, modifiers);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (immersive()) {
            if (!insideImmersiveCanvas(x, y)) return false;
            x = canvasX(x);
            y = canvasY(y);
        }
        if (!overWidget(x, y) && presentation.scroll(vertical, hasShiftDown())) return true;
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    private boolean mappedClick(int button) {
        if (presentation.mouseBinding(button, this::toggleView, this::resetView)) return true;
        if (TableKeys.DRAWER.matchesMouse(button)) return openOwnDrawer();
        var view = view();
        if (view == null) return false;
        if (TableKeys.RIICHI.matchesMouse(button) && view.actions().stream().anyMatch(action -> action.type() == RiichiAction.Type.RIICHI)) {
            toggleRiichi(); return true;
        }
        if (TableKeys.PASS.matchesMouse(button)) {
            for (int i = 0; i < view.actions().size(); i++) if (view.actions().get(i).type() == RiichiAction.Type.PASS) {
                send(view, i); return true;
            }
        }
        return false;
    }

    private void toggleRiichi() {
        if (decision.pending() || dealing()) return;
        choosingRiichi = !choosingRiichi;
        selectedTile = lastClickedTile = Tile.ABSENT;
        rebuild();
        setFocused(null);
    }

    private SeatedTableProjection.Point actionPoint(RiichiView view, RiichiAction action) {
        if (!immersive()) return project(anchor(view, action));
        int tile = view.focus() == null ? action.tiles().isEmpty() ? Tile.ABSENT : action.tiles().getFirst() : view.focus().tile();
        if (hand != null && hand.centerX(tile) >= 0) return new SeatedTableProjection.Point(hand.centerX(tile), hand.top(), 1);
        var point = board == null ? null : board.point(tile);
        return point == null ? null : new SeatedTableProjection.Point(point.x(), point.y(), 1);
    }

    private void cancelSelection() {
        choosingRiichi = false;
        hints.clearPreview();
        selectedTile = lastClickedTile = Tile.ABSENT;
        rebuild();
        setFocused(null);
    }

    /** Keyboard focus is attached to the physical source; pointer input uses the actual tile mesh. */
    private final class PhysicalHandle extends MahjongButton {
        private final RiichiView snapshot;
        PhysicalHandle(RiichiView snapshot, int index) {
            super(0, 0, 20, 20, Component.translatable(snapshot.actions().get(index).translationKey()), ignored -> send(snapshot, index));
            this.snapshot = snapshot;
            setTooltip(null);
        }
        @Override protected boolean clicked(double mouseX, double mouseY) { return false; }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            RiichiTableScene.Piece source = RiichiHandling.source(snapshot, scene);
            SeatedTableProjection.Point point = source == null ? null : project(grip(source));
            active = point != null && !decision.pending() && !handlingMoving() && results == null;
            if (point == null) return;
            setX((int) point.x() - 10); setY((int) point.y() - 10);
            if (active && isFocused()) {
                graphics.drawCenteredString(font, getMessage(), (int) point.x(), (int) point.y() - 22, MahjongUi.TEXT);
            }
        }
    }

    private final class CalloutButton extends MahjongButton {
        final RiichiAction action;
        CalloutButton(int x, int y, int w, int h, Component label, RiichiAction action, Runnable click) {
            super(x, y, w, h, label, ignored -> click.run());
            this.action = action;
            if (action.type() == RiichiAction.Type.RON || action.type() == RiichiAction.Type.TSUMO) primary();
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderSurface(graphics);
            RiichiView view = view();
            float scale = immersive() ? 2 : 1;
            int icons = view != null && TableSettings.get().actionTiles ? Math.round(actionPreviewWidth(view, action) * scale) : 0;
            boolean caption = icons == 0 || width >= icons + 36;
            int captionWidth = Math.max(16, (int) ((width - icons - 16) / scale));
            var lines = caption ? font.split(getMessage(), captionWidth).stream().limit(2).toList() : List.<net.minecraft.util.FormattedCharSequence>of();
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + 8, getY() + height / 2f, 0);
            graphics.pose().scale(scale, scale, 1);
            int y = -lines.size() * font.lineHeight / 2;
            for (var line : lines) {
                graphics.drawString(font, line, (captionWidth - font.width(line)) / 2,
                    y, active ? MahjongUi.TEXT : MahjongUi.DISABLED, false);
                y += font.lineHeight;
            }
            graphics.pose().popPose();
            if (icons > 0 && view != null) {
                ActionPreview preview = ActionPreview.of(view, action);
                int x = getX() + (caption ? width - icons + 3 : (width - icons) / 2 + 3);
                graphics.pose().pushPose();
                graphics.pose().translate(x, getY() + (height - 14 * scale) / 2, 0);
                graphics.pose().scale(scale, scale, 1);
                if (preview.meld() != null) TileGui.meld(graphics, preview.meld(), view.viewerSeat(), 0, 0, 9, facePreset());
                else for (int i = 0; i < preview.tiles().size(); i++)
                    TileGui.tile(graphics, preview.tiles().get(i), i * 13, 0, 9, false, false, false, facePreset());
                graphics.pose().popPose();
            }
        }
    }
}
