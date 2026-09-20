package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.mixin.GameRendererAccessor;
import top.skyeyefast.mchjong.network.TableActionPayload;
import top.skyeyefast.mchjong.network.TableControlPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Non-pausing table controls with seated world interaction and an independent immersive play surface. */
public final class TableScreen extends Screen {
    public static final int MIN_IMMERSIVE_WIDTH = 480;
    public static final int MIN_IMMERSIVE_HEIGHT = 300;
    private static final String[] WINDS = {"east", "south", "west", "north"};
    private final BlockPos pos;
    private final List<CalloutButton> callouts = new ArrayList<>();
    private List<TableScene.Piece> scene = List.of();
    private List<TableAnimation.Frame> frames = List.of();
    private final TableDecision decision = new TableDecision();
    private boolean choosingRiichi;
    private Button confirmButton;
    private int selectedTile = Tile.ABSENT;
    private int hoveredTile = Tile.ABSENT;
    private long lastRevision = -1;
    private boolean dragging;
    private boolean inspecting;
    private final boolean[] lookKeys = new boolean[4];
    public boolean inspecting() { return inspecting && cameraEnabled(); }
    private double dragDistance;
    private float framePartial;
    private long lastClickAt;
    private int lastClickedTile = Tile.ABSENT;
    private TableResults results;
    private boolean resultsExpanded = true;
    private Game.Phase lastPhase;
    private TableResults.Page resultPage = TableResults.Page.HAND;
    private long resultStarted;
    private Component informationTooltip;
    private final TableHud information = new TableHud();
    private int actionTop;
    private int actionLeft;
    private TableView handlingDrag;
    private Vec3 handlingStart;
    private Vec3 handlingPointer;
    private boolean immersive;
    private TableBoard board;
    private TableHand hand;
    private final TableHints hints = new TableHints();
    private final TableAutomation automation = new TableAutomation(this, () -> lastRevision = -1);

    public TableScreen(BlockPos pos) { super(Component.translatable("ui.mchjong.title")); this.pos = pos.immutable(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void removed() { clearCameraInput(); }
    private void clearCameraInput() {
        inspecting = false;
        dragging = false;
        java.util.Arrays.fill(lookKeys, false);
    }
    private boolean cameraEnabled() {
        return !immersive && minecraft != null && minecraft.player != null
            && minecraft.player.getVehicle() instanceof top.skyeyefast.mchjong.world.SeatEntity
            && minecraft.options.getCameraType().isFirstPerson();
    }
    private void syncCamera() {
        if (minecraft.player.getVehicle() instanceof top.skyeyefast.mchjong.world.SeatEntity seat) SeatedCamera.sync(seat);
    }
    public BlockPos tablePos() { return pos; }
    public boolean immersive() { return immersive; }

    public static boolean supportsImmersive(int width, int height) {
        return width >= MIN_IMMERSIVE_WIDTH && height >= MIN_IMMERSIVE_HEIGHT;
    }

    private void toggleView() {
        TableView view = view();
        if (view == null || view.viewerSeat() < 0 || view.phase() == Game.Phase.LOBBY) return;
        if (!immersive && !supportsImmersive(width, height)) return;
        immersive = !immersive;
        clearCameraInput();
        handlingDrag = null;
        rebuild();
    }

    public static TableScreen active(Screen screen) {
        if (screen instanceof TableScreen table) return table;
        if (screen instanceof TableOptionsScreen options) return options.tableScreen();
        if (screen instanceof TableSeatsScreen seats) return seats.tableScreen();
        if (screen instanceof TableSettingsScreen settings) return settings.tableScreen();
        if (screen instanceof TableClockScreen clock) return clock.tableScreen();
        if (screen instanceof TableRulesScreen rules) return rules.tableScreen();
        if (screen instanceof TableInviteScreen invite) return invite.tableScreen();
        return null;
    }

    public void resetView() {
        TableView view = view();
        if (minecraft == null || minecraft.player == null || view == null || view.viewerSeat() < 0) return;
        TableSettings settings = TableSettings.get();
        if (minecraft.player.getVehicle() instanceof top.skyeyefast.mchjong.world.SeatEntity seat) SeatedCamera.state(seat);
        settings.camera().reset(settings.cameraDistance, settings.cameraHeight);
        clearCameraInput();
        syncCamera();
    }

    TableView view() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table
            ? table.clientView() : null;
    }

    top.skyeyefast.mchjong.engine.RoomView room() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table
            ? table.clientRoom() : null;
    }

    boolean canSupplyReds(boolean sanma, top.skyeyefast.mchjong.engine.RedFives reds) {
        return reds == top.skyeyefast.mchjong.engine.RedFives.NONE || minecraft != null && minecraft.level != null
            && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table
            && (table.clientRedOptions() & 1 << ((sanma ? 3 : 0) + reds.ordinal())) != 0;
    }

    private TileFacePreset facePreset() {
        return ((MahjongTableBlockEntity) minecraft.level.getBlockEntity(pos)).equipment().preset();
    }

    private TableAnimation animation() {
        if (minecraft == null || minecraft.level == null || !(minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table)) return null;
        TableAnimation animation = TableAnimation.of(table);
        animation.accept(table.clientView(), Util.getMillis());
        return animation;
    }

    private void updateScene() {
        TableAnimation animation = animation();
        frames = animation == null ? List.of() : TableSettings.get().animations ? animation.sample(Util.getMillis()) : animation.settled();
        scene = frames.stream().map(TableAnimation.Frame::piece).toList();
    }

    private boolean dealing() {
        TableAnimation animation = animation();
        return animation != null && TableSettings.get().animations && animation.dealing(Util.getMillis());
    }

    private boolean handlingMoving() {
        TableAnimation animation = animation();
        return animation != null && TableSettings.get().animations && animation.moving(Util.getMillis());
    }

    public void receivedView() {
        refreshDecision(view());
        lastRevision = -1;
    }

    public void receivedControlReply() {
        automation.receivedControlReply();
        if (minecraft.screen instanceof TableRulesScreen rules && rules.tableScreen() == this) rules.receivedReply();
    }

    private void refreshDecision(TableView view) {
        if (handlingDrag != null && (view == null || !handlingDrag.tableId().equals(view.tableId())
            || handlingDrag.decision() != view.decision())) handlingDrag = null;
        if (decision.receive(view)) {
            selectedTile = lastClickedTile = hoveredTile = Tile.ABSENT;
            choosingRiichi = false;
        }
    }

    public static Component roundName(TableView view) {
        Component wind = Component.translatable("wind.mchjong." + WINDS[Math.min(3, view.round() / view.rules().players())]);
        return Component.translatable("ui.mchjong.round", wind, view.round() % view.rules().players() + 1, view.honba());
    }

    public boolean selected(BlockPos table, TableScene.Piece piece) {
        TableView view = view();
        return pos.equals(table) && view != null && piece.area() == TableScene.Area.HAND && piece.seat() == view.viewerSeat()
            && piece.tile() >= 0 && (piece.tile() == selectedTile || piece.tile() == hoveredTile);
    }

    /** The world renderer applies this color to the same animated mesh used for picking. */
    public int highlight(BlockPos table, TableScene.Piece piece) {
        TableView view = view();
        if (!pos.equals(table) || view == null || view.exitVote() != null || results != null) return 0;
        boolean highlights = TableSettings.get().highlightTiles;
        if (piece.area() == TableScene.Area.HAND && piece.seat() == view.viewerSeat()) {
            if (piece.tile() >= 0 && (piece.tile() == selectedTile || highlights && piece.tile() == hoveredTile))
                return MahjongUi.ACCENT;
            if (highlights && choosingRiichi && tileAction(view, piece.tile(), Action.Type.RIICHI) >= 0) return MahjongUi.POSITIVE;
            for (var button : callouts) if ((button.isFocused() || highlights && button.isHovered()) && showsConsumed(button.action)
                && button.action.tiles().contains(piece.tile())) return MahjongUi.POSITIVE;
        }
        if ((highlights || getFocused() instanceof PhysicalHandle) && TableHandling.action(view) >= 0) {
            // A held packet may contain more tiles than the stack used to begin the gesture.
            // Derive its outline from the same offset as the mesh, not the idle pickup hint.
            if (handlingDrag != null)
                return handlingOffset(table, piece).lengthSqr() > 0 ? MahjongUi.POSITIVE : 0;
            if (!handlingMoving() && TableHandling.source(view, piece)
                && (view.phase() != Game.Phase.SHUFFLE || piece.equals(TableHandling.source(view, scene))))
                return MahjongUi.ACCENT;
        }
        return 0;
    }

    @Override protected void init() { lastRevision = -1; rebuild(); }

    @Override public void tick() {
        if (!minecraft.isWindowActive()) clearCameraInput();
        if (cameraEnabled()) {
            TableSettings.get().camera().look((lookKeys[1] ? 1 : 0) - (lookKeys[0] ? 1 : 0),
                (lookKeys[3] ? 1 : 0) - (lookKeys[2] ? 1 : 0));
            syncCamera();
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.player.distanceToSqr(pos.getX()+0.5, pos.getY(), pos.getZ()+0.5) > 36
            || !(minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity)) onClose();
    }

    private boolean tileChoice(Action action) {
        return switch (action.type()) {
            case DISCARD, RIICHI, CLOSED_KAN, ADDED_KAN, NUKI -> true;
            default -> false;
        };
    }

    private void rebuild() {
        int automationFocus = automation.focusedIndex(getFocused());
        clearWidgets();
        callouts.clear();
        confirmButton = null;
        TableView view = view();
        if (view == null) return;
        refreshDecision(view);
        if (view.phase() == Game.Phase.LOBBY || view.viewerSeat() < 0 || !supportsImmersive(width, height)) immersive = false;
        boolean newResult = lastPhase != view.phase() && TableResults.available(view);
        if (newResult) {
            resultsExpanded = true;
            resultPage = TableResults.Page.HAND;
            resultStarted = Util.getMillis();
        }
        int selectedWinner = results == null || newResult ? 0 : results.selectedWinner();
        results = null;
        lastPhase = view.phase();
        lastRevision = view.revision();
        updateScene();
        board = null;
        int handHeight = height - (immersive ? TableResults.available(view) ? 54 : TableAutomation.available(view) ? 24 : 0 : 0);
        hand = immersive && view.viewerSeat() >= 0
            && !view.seats().get(view.viewerSeat()).hand().isEmpty()
            ? new TableHand(view.seats().get(view.viewerSeat()), view.viewerSeat(), width, handHeight, height < 360 ? 20 : 26) : null;
        if (view.viewerSeat() < 0 || !view.seats().get(view.viewerSeat()).hand().contains(selectedTile)) selectedTile = Tile.ABSENT;
        if (view.actions().stream().noneMatch(action -> action.type() == Action.Type.RIICHI)) choosingRiichi = false;
        buildToolbar(view);
        actionLeft = 10;
        automation.build(view, width, immersive || hand == null ? height - 17 : hand.top() - 8, immersive).forEach(this::addRenderableWidget);
        automation.restoreFocus(automationFocus);
        if (view.exitVote() != null) { buildExitVote(view); return; }
        if (view.phase() == Game.Phase.LOBBY) { buildLobby(view); return; }
        int physical = TableHandling.action(view);
        if (physical >= 0 && !immersive) addRenderableWidget(new PhysicalHandle(view, physical));
        List<Integer> choices = new ArrayList<>();
        for (int i = 0; i < view.actions().size(); i++) {
            Action action = view.actions().get(i);
            if (action.type() == Action.Type.DISCARD || action.type() == Action.Type.RIICHI
                || !immersive && TableHandling.physical(view, action)) continue;
            choices.add(i);
        }
        boolean riichi = view.actions().stream().anyMatch(action -> action.type() == Action.Type.RIICHI);
        int discard = (choosingRiichi || TableSettings.get().discardMode == TableSettings.DiscardMode.CONFIRM) && selectedTile >= 0
            ? tileAction(view, selectedTile, choosingRiichi ? Action.Type.RIICHI : Action.Type.DISCARD) : -1;
        int count = choices.size() + (riichi ? 1 : 0) + (discard >= 0 ? 1 : 0);
        boolean sideActions = immersive && (count <= 1 || count <= 3 && height >= 360);
        int actionWidth = sideActions ? Math.min(132, (width - 28) / 3)
            : width - 20 - (!immersive && TableAutomation.available(view) ? automation.width(width) + 8 : 0);
        actionLeft = width - 10 - actionWidth;
        int columns = Math.min(Math.max(1, count), Math.max(1, Math.min(3, actionWidth / 88)));
        if (immersive && !sideActions && !TableResults.available(view)) columns = Math.max(1, count);
        int boxWidth = Math.min(132, (actionWidth - (columns - 1) * 4) / columns);
        int rows = Math.max(1, (count + columns - 1) / columns);
        actionTop = (hand == null || TableResults.available(view) ? height - 43 : hand.top() - 34) - (rows - 1) * 30;
        if (immersive) board = new TableBoard(view, 8, width - 8, 38,
            hand == null ? height - 56 : sideActions ? hand.top() - 6 : actionTop - 6,
            sideActions && count > 0 ? actionTop - 4 : height);
        int startX = width - 10 - columns * (boxWidth + 4) + 4;
        int slot = 0;
        if (riichi) {
            var button = MahjongButton.create(Component.translatable(choosingRiichi ? "ui.mchjong.cancel_riichi" : "action.mchjong.riichi"),
                ignored -> toggleRiichi()).bounds(startX, actionTop, boxWidth, 26).build();
            button.setTooltip(Tooltip.create(button.getMessage()));
            addRenderableWidget(button);
            slot++;
        }
        for (int index : choices) {
            Action action = view.actions().get(index);
            Component label = Component.translatable(action.type() == Action.Type.NEXT && view.phase() == Game.Phase.MATCH_END
                    ? "ui.mchjong.new_match" : action.translationKey());
            final TableView snapshot = view;
            CalloutButton button = new CalloutButton(startX + slot % columns * (boxWidth + 4), actionTop + slot / columns * 30,
                boxWidth, 26, label, action, index,
                () -> send(snapshot, index));
            button.setTooltip(Tooltip.create(label));
            addRenderableWidget(button);
            callouts.add(button);
            slot++;
        }
        if (TableResults.available(view) && TableSettings.get().show(TableSettings.Information.RESULTS)) {
            addRenderableWidget(MahjongButton.create(Component.translatable(resultsExpanded ? "ui.mchjong.view_table" : "ui.mchjong.view_results"),
                ignored -> { resultsExpanded = !resultsExpanded; rebuild(); }).bounds(10, height - 48, boxWidth, 20).build());
            if (resultsExpanded) {
                int tabs = view.phase() == Game.Phase.MATCH_END ? 3 : 2;
                int tabWidth = (width - 20) / tabs;
                for (int i = 0; i < tabs; i++) {
                    var page = TableResults.Page.values()[i];
                    var button = MahjongButton.create(Component.translatable("ui.mchjong.result_page." + i), ignored -> {
                        resultPage = page; results = null; rebuild();
                    }).bounds(10 + i * tabWidth, view.exitVote() == null ? 34 : 58, tabWidth - 3, 20).build();
                    button.selected(page == resultPage);
                    addRenderableWidget(button);
                }
                int panelTop = view.exitVote() == null ? 58 : 82;
                results = addRenderableWidget(new TableResults(font, view, facePreset(), 10, panelTop, width - 20, height - panelTop - 54,
                    selectedWinner, resultPage, resultStarted));
            }
        }
        if (discard >= 0) {
                final TableView snapshot = view;
                final int actionIndex = discard;
                confirmButton = addRenderableWidget(MahjongButton.create(Component.translatable(choosingRiichi ? "ui.mchjong.confirm_riichi" : "action.mchjong.discard"), ignored -> send(snapshot, actionIndex))
                    .bounds(startX + slot % columns * (boxWidth + 4), actionTop + slot / columns * 30, boxWidth, 26).build().primary());
                confirmButton.setTooltip(Tooltip.create(confirmButton.getMessage()));
        }
    }

    private void buildToolbar(TableView view) {
        int right = width - 8;
        addRenderableWidget(MahjongButton.create(Component.literal("…"), ignored -> minecraft.setScreen(new TableOptionsScreen(this)))
            .bounds(right - 22, 8, 22, 20).tooltip(Tooltip.create(Component.translatable("settings.mchjong.scopes"))).build());
        right -= 26;
        if (view.viewerSeat() >= 0) {
            var exit = MahjongButton.create(Component.translatable("ui.mchjong.exit"), ignored ->
                control(view, TableControlPayload.Operation.REQUEST_EXIT, view.decision(), false))
                .bounds(right - 48, 8, 48, 20).build();
            exit.active = view.exitVote() == null;
            addRenderableWidget(exit);
            right -= 52;
        }
        boolean fits = supportsImmersive(width, height);
        Component cameraHelp = fits ? Component.translatable("ui.mchjong.switch_view", TableKeys.VIEW.getTranslatedKeyMessage())
            .append("\n").append(Component.translatable("ui.mchjong.camera_help",
                TableKeys.INSPECT.getTranslatedKeyMessage(), TableKeys.RESET.getTranslatedKeyMessage()))
            : Component.translatable("ui.mchjong.immersive_window_small", MIN_IMMERSIVE_WIDTH, MIN_IMMERSIVE_HEIGHT);
        var camera = MahjongButton.create(Component.translatable(immersive ? "ui.mchjong.view_seated" : "ui.mchjong.view_immersive"), ignored -> toggleView())
            .bounds(right - 64, 8, 64, 20).tooltip(Tooltip.create(cameraHelp)).build();
        camera.active = fits && view.viewerSeat() >= 0 && view.phase() != Game.Phase.LOBBY;
        addRenderableWidget(camera);
        right -= 68;
        addRenderableWidget(MahjongButton.create(Component.translatable("replay.mchjong.title"), ignored -> ClientReplays.list(0, "", false))
            .bounds(right - 52, 8, 52, 20).build());
    }

    void control(TableView view, TableControlPayload.Operation operation, long token, boolean enabled) {
        if (minecraft.getConnection() == null) return;
        minecraft.getConnection().send(new ServerboundCustomPayloadPacket(new TableControlPayload(pos, view.tableId(), operation, token, enabled)));
    }

    private void buildExitVote(TableView view) {
        var vote = view.exitVote();
        if (vote == null || view.viewerSeat() < 0) return;
        int span = Math.min(360, width - 24), left = (width - span) / 2;
        int y = height / 2 + 30;
        var agree = MahjongButton.create(Component.translatable("ui.mchjong.exit_agree"), ignored ->
            control(view, TableControlPayload.Operation.ANSWER_EXIT, vote.id(), true)).bounds(left, y, (span - 4) / 2, 20).build();
        agree.active = !vote.agreed().contains(view.viewerSeat());
        addRenderableWidget(agree);
        addRenderableWidget(MahjongButton.create(Component.translatable("ui.mchjong.exit_reject"), ignored ->
            control(view, TableControlPayload.Operation.ANSWER_EXIT, vote.id(), false)).bounds(left + (span + 4) / 2, y, (span - 4) / 2, 20).build());
    }

    private void buildLobby(TableView view) {
        var room = room();
        buildSeatControls(view);
        if (room != null && room.seating() != top.skyeyefast.mchjong.engine.RoomSeating.Stage.GATHERING) {
            buildSeating(view, room);
            return;
        }
        int span = Math.min(400, width - 20), left = (width - span) / 2;
        int half = (span - 4) / 2;
        int bodyTop = height < 300 ? 82 : 94;
        boolean host = view.actions().stream().anyMatch(action -> action.type() == Action.Type.CHANGE_RULE);
        for (int players : new int[]{4, 3}) {
            RuleSet rule = view.rules().preset().tenhou() ? players == 4 ? RuleSet.TENHOU_4 : RuleSet.TENHOU_3
                : players == 4 ? RuleSet.MAHJONG_SOUL_4 : RuleSet.MAHJONG_SOUL_3;
            int action = ruleAction(view, rule);
            var config = view.rules().withPreset(rule);
            var mode = MahjongButton.create(Component.translatable("ui.mchjong.players." + players), ignored -> send(view, action))
                .bounds(left + (4 - players) * (half + 4), bodyTop, half, 20)
                .tooltip(Tooltip.create(Component.translatable(canSupplyReds(config.sanma(), config.redFives())
                    ? "ui.mchjong.players." + players : "rules.mchjong.insufficient_reds"))).build();
            mode.active = host && view.rules().players() != players && action >= 0 && canSupplyReds(config.sanma(), config.redFives());
            mode.selected(view.rules().players() == players);
            addRenderableWidget(mode);
        }
        var presets = java.util.Arrays.stream(RuleSet.values()).filter(rule -> rule.players() == view.rules().players()).toList();
        int columns = Math.min(3, presets.size());
        int presetWidth = (span - (columns - 1) * 4) / columns;
        for (int i = 0; i < presets.size(); i++) {
            RuleSet rule = presets.get(i);
            int action = ruleAction(view, rule);
            var config = view.rules().withPreset(rule);
            boolean available = canSupplyReds(config.sanma(), config.redFives());
            var preset = MahjongButton.create(Component.translatable(rule.presetKey()), ignored -> send(view, action))
                .bounds(left + i % columns * (presetWidth + 4), bodyTop + 24 + i / columns * 24, presetWidth, 20)
                .tooltip(Tooltip.create(Component.translatable(available ? rule.translationKey() : "rules.mchjong.insufficient_reds"))).build();
            preset.active = host && action >= 0 && available;
            preset.selected(!view.rules().custom() && view.rules().preset() == rule);
            addRenderableWidget(preset);
        }
        int y = bodyTop + 24 + (presets.size() + columns - 1) / columns * 24;
        addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.title"), ignored ->
            minecraft.setScreen(new TableRulesScreen(this, view)))
            .bounds(left + half + 4, y, half, 20).build().selected(view.rules().custom()));
        addRenderableWidget(MahjongButton.create(Component.translatable("settings.mchjong.scopes"), ignored ->
            minecraft.setScreen(new TableOptionsScreen(this))).bounds(left, y, half, 20).build());
        y += 32;
        int primary = TableSeatsScreen.find(view, Action.Type.BEGIN_SEATING, List.of());
        if (primary < 0) primary = TableSeatsScreen.find(view, Action.Type.FILL_BOTS, List.of());
        for (int i = 0; i < view.actions().size(); i++) {
            var action = view.actions().get(i);
            if (i != primary) continue;
            int index = i;
            int buttonWidth = Math.min(260, span);
            var button = new CalloutButton((width - buttonWidth) / 2, y, buttonWidth, 26,
                Component.translatable(action.type() == Action.Type.FILL_BOTS ? "room.mchjong.start_bots"
                    : automatic() ? "room.mchjong.start_auto" : "room.mchjong.start_manual"),
                action, i, () -> send(view, index));
            button.primary();
            addRenderableWidget(button); callouts.add(button);
        }
        if (primary < 0 && view.viewerSeat() >= 0 && view.exitVote() == null) {
            var waiting = MahjongButton.create(Component.translatable("room.mchjong.wait_host"), ignored -> {})
                .bounds(left, y, span, 26).build();
            waiting.active = false;
            addRenderableWidget(waiting);
        }
        actionTop = y;
    }

    boolean automatic() {
        return minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table && table.automatic();
    }

    private void buildSeatControls(TableView view) {
        var room = room();
        if (room == null) return;
        int cardWidth = (width - 16 - (view.seats().size() - 1) * 4) / view.seats().size();
        for (int seat = 0; seat < view.seats().size(); seat++) {
            var player = view.seats().get(seat);
            if (player.occupied() && !player.bot() && room.seats().get(seat).present()) continue;
            var difficulty = room.seats().get(seat).difficulty();
            var next = !player.bot() ? top.skyeyefast.mchjong.engine.BotDifficulty.EASY : switch (difficulty) {
                case EASY -> top.skyeyefast.mchjong.engine.BotDifficulty.HARD;
                case HARD -> null;
            };
            int index = next == null ? TableSeatsScreen.find(view, Action.Type.REMOVE_BOT, List.of(seat))
                : TableSeatsScreen.find(view, Action.Type.SET_BOT, List.of(seat, next.ordinal()));
            var label = Component.translatable(player.bot() ? difficulty.translationKey() : "room.mchjong.add_bot").append(" ›");
            var button = MahjongButton.create(label, ignored -> send(view, index))
                .bounds(8 + seat * (cardWidth + 4), 56, cardWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("room.mchjong.bot_next", Component.translatable(
                    next == null ? "room.mchjong.empty" : next.translationKey())))).build();
            button.active = index >= 0 && view.exitVote() == null;
            addRenderableWidget(button);
        }
    }

    private void buildSeating(TableView view, top.skyeyefast.mchjong.engine.RoomView room) {
        int span = Math.min(440, width - 24), left = (width - span) / 2;
        boolean drawing = room.seating() == top.skyeyefast.mchjong.engine.RoomSeating.Stage.DRAWING;
        var heading = MahjongButton.create(Component.translatable(drawing ? "room.mchjong.draw_winds" : "room.mchjong.take_seats"), ignored -> {})
            .bounds(left, 82, span, 20).build();
        heading.active = false;
        addRenderableWidget(heading);
        for (int seat = 0; seat < view.seats().size(); seat++) {
            var state = room.seats().get(seat);
            var player = view.seats().get(seat);
            Component status = drawing ? TableSeatsScreen.wind(state.wind())
                : Component.translatable(player.ready() ? "ui.mchjong.ready" : state.present() ? "room.mchjong.present" : "room.mchjong.absent");
            Component label = Component.translatable("room.mchjong.seat_status", drawing ? Component.literal(Integer.toString(seat + 1))
                : TableSeatsScreen.wind(seat), playerName(view, seat), status);
            var entry = MahjongButton.create(label, ignored -> {}).bounds(left, 106 + seat * 18, span, 16)
                .tooltip(Tooltip.create(drawing ? label : TableSeatsScreen.position(this, seat))).build();
            entry.active = false;
            addRenderableWidget(entry);
        }
        int actionY = Math.min(height - 58, 182);
        if (drawing) {
            int count = view.seats().size(), cell = (span - 4 * (count - 1)) / count;
            for (int slot = 0; slot < count; slot++) {
                int index = TableSeatsScreen.find(view, Action.Type.DRAW_WIND, List.of(slot));
                var button = MahjongButton.create(Component.translatable("room.mchjong.wind_tile", slot + 1), ignored -> send(view, index))
                    .bounds(left + slot * (cell + 4), actionY, cell, 22).build();
                button.active = index >= 0;
                addRenderableWidget(button);
            }
        } else {
            int ready = TableSeatsScreen.find(view, Action.Type.READY, List.of());
            boolean present = view.viewerSeat() >= 0 && room.seats().get(view.viewerSeat()).present();
            var button = MahjongButton.create(Component.translatable(ready >= 0 ? "action.mchjong.ready"
                : present ? "ui.mchjong.equipment_needed" : "room.mchjong.take_seats"), ignored -> send(view, ready))
                .bounds(left, actionY, span, 22).build().primary();
            button.active = ready >= 0;
            addRenderableWidget(button);
        }
        addRenderableWidget(MahjongButton.create(Component.translatable("room.mchjong.participants"), ignored ->
            minecraft.setScreen(new TableSeatsScreen(this))).bounds(left, height - 30, span, 20).build());
        actionTop = actionY;
    }

    private static int ruleAction(TableView view, RuleSet rule) {
        for (int i = 0; i < view.actions().size(); i++) {
            var action = view.actions().get(i);
            if (action.type() == Action.Type.CHANGE_RULE && action.tiles().getFirst() == rule.ordinal()) return i;
        }
        return -1;
    }

    void send(TableView snapshot, int index) {
        TableView current = view();
        refreshDecision(current);
        if (dealing() || TableResults.available(snapshot) && Util.getMillis() - resultStarted < 500
            || minecraft.getConnection() == null || !decision.submit(snapshot, index)) return;
        minecraft.getConnection().send(new ServerboundCustomPayloadPacket(new TableActionPayload(pos, snapshot.tableId(), snapshot.decision(), index)));
        callouts.forEach(button -> button.active = false);
        selectedTile = lastClickedTile = Tile.ABSENT;
        choosingRiichi = false;
        if (confirmButton != null) confirmButton.active = false;
    }

    private static boolean showsConsumed(Action action) {
        return ActionPreview.consumesHand(action);
    }

    private int actionPreviewWidth(TableView view, Action action) {
        ActionPreview preview = ActionPreview.of(view, action);
        if (preview.meld() != null) return TileGui.meldWidth(preview.meld(), view.viewerSeat(), 11) + 8;
        return preview.tiles().isEmpty() ? 0 : preview.tiles().size() * 13 + 6;
    }

    private Vec3 anchor(TableView view, Action action) {
        if (view.focus() != null) {
            TableScene.Area area = view.focus().declaration() ? TableScene.Area.HAND : TableScene.Area.RIVER;
            for (TableScene.Piece piece : scene)
                if (piece.area() == area && piece.seat() == view.focus().seat() && piece.index() == view.focus().index()) return piece.position();
        }
        if (tileChoice(action) || action.type() == Action.Type.TSUMO) {
            for (TableScene.Piece piece : scene)
                if (piece.area() == TableScene.Area.HAND && piece.seat() == view.viewerSeat()
                    && (action.tiles().contains(piece.tile()) || action.type() == Action.Type.TSUMO
                        && piece.tile() == view.seats().get(view.viewerSeat()).drawn())) return piece.position();
        }
        if (action.type() == Action.Type.ABORT_NINE && view.viewerSeat() >= 0)
            return TableGeometry.orient(0, TableGeometry.FELT_Y + 0.08, TableScene.HAND_Z, view.viewerSeat());
        return new Vec3(0, TableGeometry.FELT_Y + 0.045, 0);
    }

    private record Projected(double x, double y, double scale) {}

    private Projected projectHand(TableScene.Piece piece) {
        if (hand != null && hand.centerX(piece.tile()) >= 0)
            return new Projected(hand.centerX(piece.tile()), hand.top(), 1);
        return project(piece.position());
    }

    private Projected project(Vec3 relative) {
        if (immersive) return null;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 delta = TableGeometry.world(pos, relative).subtract(camera.getPosition());
        double yaw = Math.toRadians(camera.getYRot()), pitch = Math.toRadians(camera.getXRot());
        Vec3 forward = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = right.cross(forward);
        double depth = delta.dot(forward);
        if (depth <= 0.05) return null;
        double fov = ((GameRendererAccessor) minecraft.gameRenderer).mchjong$getFov(camera, framePartial, true);
        double scale = height / (2 * Math.tan(Math.toRadians(fov) / 2)) / depth;
        return new Projected(width / 2.0 + delta.dot(right) * scale, height / 2.0 - delta.dot(up) * scale, scale);
    }

    private int pick(double mouseX, double mouseY) {
        TableView view = view();
        if (view == null || view.viewerSeat() < 0 || view.exitVote() != null || dealing() || TableResults.available(view)
            || overWidget(mouseX, mouseY) || overInformation(mouseX, mouseY)) return Tile.ABSENT;
        if (hand != null) {
            int tile = hand.pick(mouseX, mouseY, selectedTile);
            return choosingRiichi && tileAction(view, tile, Action.Type.RIICHI) < 0 ? Tile.ABSENT : tile;
        }
        if (immersive) return Tile.ABSENT;
        Pointer pointer = pointer(mouseX, mouseY);
        TableScene.Piece best = null;
        double closest = Double.MAX_VALUE;
        for (TableAnimation.Frame frame : frames) {
            TableScene.Piece piece = frame.piece();
            if (piece.area() != TableScene.Area.HAND || piece.seat() != view.viewerSeat()) continue;
            if (choosingRiichi && tileAction(view, piece.tile(), Action.Type.RIICHI) < 0) continue;
            double distance = TilePicking.distanceSquared(frame, pointer.origin, pointer.ray, selected(pos, piece));
            if (distance < closest) {
                closest = distance;
                best = piece;
            }
        }
        return best == null ? Tile.ABSENT : best.tile();
    }

    private record Pointer(Vec3 origin, Vec3 ray) {}

    private Pointer pointer(double mouseX, double mouseY) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        double yaw = Math.toRadians(camera.getYRot()), pitch = Math.toRadians(camera.getXRot());
        Vec3 forward = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        double fov = ((GameRendererAccessor) minecraft.gameRenderer).mchjong$getFov(camera, framePartial, true);
        double focal = height / (2 * Math.tan(Math.toRadians(fov) / 2));
        return new Pointer(camera.getPosition().subtract(TableGeometry.world(pos, Vec3.ZERO)),
            forward.add(right.scale((mouseX - width / 2.0) / focal))
                .add(right.cross(forward).scale((height / 2.0 - mouseY) / focal)));
    }

    private Vec3 tablePoint(double mouseX, double mouseY) {
        Pointer pointer = pointer(mouseX, mouseY);
        if (Math.abs(pointer.ray.y) < 1e-6) return null;
        double distance = (TableGeometry.FELT_Y - pointer.origin.y) / pointer.ray.y;
        return distance > 0 ? pointer.origin.add(pointer.ray.scale(distance)) : null;
    }

    private TableScene.Piece pickPhysical(double mouseX, double mouseY) {
        TableView view = view();
        if (immersive || TableHandling.action(view) < 0 || handlingMoving() || decision.pending()
            || results != null || overWidget(mouseX, mouseY) || overInformation(mouseX, mouseY)) return null;
        Pointer pointer = pointer(mouseX, mouseY);
        TableScene.Piece nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (var frame : frames) {
            double candidate = TilePicking.distanceSquared(frame, pointer.origin, pointer.ray, false);
            if (candidate < distance) { distance = candidate; nearest = frame.piece(); }
        }
        return TableHandling.source(view, nearest) ? nearest : null;
    }

    public Vec3 handlingOffset(BlockPos table, TableScene.Piece piece) {
        if (!pos.equals(table) || handlingDrag == null || handlingStart == null || handlingPointer == null) return Vec3.ZERO;
        int index = TableHandling.action(handlingDrag);
        if (index < 0) return Vec3.ZERO;
        boolean held = switch (handlingDrag.actions().get(index).type()) {
            case SHUFFLE -> piece.area() == TableScene.Area.LOOSE && piece.position().distanceToSqr(handlingStart) < .2;
            case BUILD_WALL -> piece.area() == TableScene.Area.LOOSE && piece.seat() == handlingDrag.viewerSeat();
            case TAKE_PACKET, DRAW -> piece.area() == TableScene.Area.WALL
                && piece.index() >= handlingDrag.handling().sourceSlot()
                && piece.index() < handlingDrag.handling().sourceSlot() + handlingDrag.handling().packetSize();
            case NEXT -> piece.area() != TableScene.Area.WALL && piece.seat() == handlingDrag.viewerSeat();
            default -> false;
        };
        return held ? handlingPointer.subtract(handlingStart).add(0, .05, 0) : Vec3.ZERO;
    }

    private boolean openOwnDrawer() {
        TableView view = view();
        if (view == null || view.handling() == null || view.viewerSeat() < 0 || view.exitVote() != null || minecraft.gameMode == null) return false;
        int side = view.viewerSeat();
        Vec3 location = TableGeometry.world(pos, TableGeometry.drawerBounds(side).getCenter());
        var hit = new net.minecraft.world.phys.BlockHitResult(location, TableGeometry.SIDES[side], BlockPos.containing(location), false);
        handlingDrag = null;
        minecraft.gameMode.useItemOn(minecraft.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        return true;
    }

    private boolean openDrawer(double mouseX, double mouseY) {
        TableView view = view();
        if (immersive || view == null || view.handling() == null || view.viewerSeat() < 0 || view.exitVote() != null
            || results != null || minecraft.gameMode == null) return false;
        Pointer pointer = pointer(mouseX, mouseY);
        // Use vanilla block interaction: native reach, loaded-block and container checks stay on the server.
        Vec3 start = TableGeometry.world(pos, pointer.origin);
        Vec3 end = start.add(pointer.ray.normalize().scale(6));
        var hit = minecraft.level.clip(new net.minecraft.world.level.ClipContext(start, end,
            net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, minecraft.player));
        if (!(minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table) || table.drawerAt(hit) < 0) return false;
        minecraft.gameMode.useItemOn(minecraft.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        return true;
    }

    private Vec3 grip(TableScene.Piece piece) {
        Vec3 eye = minecraft.gameRenderer.getMainCamera().getPosition().subtract(TableGeometry.world(pos, Vec3.ZERO));
        return TableHandling.grip(piece, eye);
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
        TableView view = view();
        if (view == null) return;
        if (view.revision() != lastRevision) rebuild();
        updateScene();
        information.clear();
        if (immersive) {
            graphics.fill(0, 0, width, height, MahjongUi.INPUT);
            graphics.fill(4, 34, width - 4, height - 17, MahjongUi.SURFACE);
            if (board != null) board.render(graphics, view, facePreset());
        }
        if (view.phase() == Game.Phase.LOBBY && room() != null
                && room().seating() == top.skyeyefast.mchjong.engine.RoomSeating.Stage.GATHERING) {
            int span = Math.min(400, width - 20), left = (width - span) / 2;
            int top = 78;
            MahjongUi.panel(graphics, left - 4, top, span + 8, actionTop + 32 - top);
        }
        if (view.exitVote() != null) {
            renderExitVote(graphics, view);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        if (!TableResults.available(view) || immersive && results == null)
            information.render(font, graphics, view, width, facePreset(), board);
        if (view.phase() == Game.Phase.LOBBY && view.rules().redFives() == top.skyeyefast.mchjong.engine.RedFives.NONE) {
            var lines = font.split(Component.translatable("rules.mchjong.no_red_warning"), width - 24);
            int y = height - 6 - lines.size() * font.lineHeight;
            for (var line : lines) {
                graphics.drawCenteredString(font, line, width / 2, y, MahjongUi.NEGATIVE);
                y += font.lineHeight;
            }
        }
        if (height >= 300 && view.phase() == Game.Phase.LOBBY && room() != null
                && room().seating() == top.skyeyefast.mchjong.engine.RoomSeating.Stage.GATHERING) {
            int span = Math.min(400, width - 20), left = (width - span) / 2;
            MahjongUi.text(graphics, font, Component.translatable(automatic() ? "room.mchjong.flow_auto" : "room.mchjong.flow_manual"),
                left, 82, span, MahjongUi.ACCENT, true);
        }
        hoveredTile = pick(mouseX, mouseY);
        renderHandling(graphics, view, mouseX, mouseY);
        informationTooltip = information.tooltip(mouseX, mouseY);
        TableSettings settings = TableSettings.get();
        boolean inputEnabled = !decision.pending() && !dealing()
            && (!TableResults.available(view) || Util.getMillis() - resultStarted >= 500);
        callouts.forEach(button -> button.active = inputEnabled);
        if (confirmButton != null) confirmButton.active = inputEnabled;
        for (CalloutButton button : callouts) {
            boolean guide = settings.guideLines == TableSettings.GuideLines.ALWAYS
                || settings.guideLines == TableSettings.GuideLines.HOVER && button.isHoveredOrFocused();
            if (guide && ActionPreview.hasGuide(button.action)) {
                Projected point = actionPoint(view, button.action);
                if (point != null) elbow(graphics, point, button, button.isHoveredOrFocused() ? 0xffffdc89 : 0xff8fbca9);
            }
            if (settings.highlightTiles && showsConsumed(button.action) && button.isHoveredOrFocused()) {
                for (TableScene.Piece piece : scene) if (piece.area() == TableScene.Area.HAND && piece.seat() == view.viewerSeat()
                    && button.action.tiles().contains(piece.tile())) {
                    Projected own = projectHand(piece);
                    if (own != null) elbow(graphics, own, button, 0xffdfc98e);
                }
            }
        }
        if (choosingRiichi) MahjongUi.text(graphics, font, Component.translatable("ui.mchjong.choose_riichi"),
            actionLeft, actionTop - 14, width - actionLeft - 10, MahjongUi.ACCENT, true, true);
        if (hand != null && results == null) hand.render(graphics, selectedTile, tile -> {
            for (var piece : scene) if (piece.area() == TableScene.Area.HAND && piece.seat() == view.viewerSeat() && piece.tile() == tile)
                return highlight(pos, piece);
            return 0;
        }, facePreset());
        if (settings.convenienceHints && !dealing() && !decision.pending())
            hints.render(graphics, font, view, hoveredTile >= 0 ? hoveredTile : selectedTile,
                width, actionLeft, actionTop - (choosingRiichi ? 32 : 18), hand != null, facePreset());
        super.render(graphics, mouseX, mouseY, partialTick);
        boolean footerClock = false;
        if (!TableResults.available(view) && view.viewerSeat() >= 0 && view.viewerSeat() < view.clocks().size()) {
            var table = (MahjongTableBlockEntity) minecraft.level.getBlockEntity(pos);
            var clock = view.clocks().get(view.viewerSeat()).after(table.clientViewAgeMillis());
            if (clock.active()) {
                footerClock = hand != null;
                Component text = Component.translatable("ui.mchjong.clock", clock.moveSeconds(), clock.reserveSeconds());
                MahjongUi.text(graphics, font, text, footerClock ? 10 : actionLeft,
                    footerClock ? height - 13 : actionTop - (choosingRiichi ? 28 : 14),
                    footerClock ? width - 20 : width - actionLeft - 10,
                    clock.moveTicks() + clock.reserveTicks() <= 100 ? MahjongUi.NEGATIVE : MahjongUi.ACCENT, true, true);
            }
        }
        if (TableResults.available(view)) {
            long ready = view.seats().stream().filter(TableView.Seat::ready).count();
            graphics.drawString(font, Component.translatable("ui.mchjong.ready_count", ready, view.seats().size()),
                10, 14, 0xffd1e4d9);
        }
        if (dealing()) MahjongUi.text(graphics, font, Component.translatable("ui.mchjong.dealing"),
            actionLeft, actionTop - 14, width - actionLeft - 10, MahjongUi.ACCENT, true, true);
        else if (TableSettings.get().animations && animation() != null && !TableResults.available(view)) {
            int cueY = immersive ? actionTop - 14 : 88;
            for (var cue : animation().cues(Util.getMillis())) {
                MahjongUi.text(graphics, font, playerName(view, cue.seat()).copy().append(" · ").append(Component.translatable(cue.key())),
                    actionLeft, cueY, width - actionLeft - 10, MahjongUi.ACCENT, true, true);
                cueY += 13;
            }
        }
        if (view.phase() != Game.Phase.LOBBY && !footerClock && settings.show(TableSettings.Information.HELP)) {
            String helpKey = TableResults.available(view) ? "ui.mchjong.result_help" : view.viewerSeat() < 0 ? "ui.mchjong.spectator_help"
                : choosingRiichi ? "ui.mchjong.riichi_help" : "ui.mchjong.help_" + settings.discardMode.name().toLowerCase(java.util.Locale.ROOT);
            Component help = choosingRiichi || TableResults.available(view) || view.viewerSeat() < 0
                ? Component.translatable(helpKey)
                : Component.translatable(helpKey, TableKeys.RIICHI.getTranslatedKeyMessage(), TableKeys.PASS.getTranslatedKeyMessage());
            if (view.handling() != null && view.viewerSeat() >= 0)
                help = Component.translatable("sticks.mchjong.access", TableKeys.DRAWER.getTranslatedKeyMessage()).append(" · ").append(help);
            graphics.drawString(font, font.plainSubstrByWidth(help.getString(), width - 20), 10, height - 13, 0xffe0deca, true);
        }
        if (informationTooltip != null && !overWidget(mouseX, mouseY))
            graphics.renderTooltip(font, font.split(informationTooltip, Math.min(320, width - 24)), mouseX, mouseY);
    }

    private void renderHandling(GuiGraphics graphics, TableView view, int mouseX, int mouseY) {
        if (immersive) return;
        int index = TableHandling.action(view);
        if (index < 0 || results != null) return;
        TableScene.Piece source = TableHandling.source(view, scene);
        if (source != null) {
            Projected target = project(TableHandling.destination(view));
            if (target != null && handlingDrag != null) {
                graphics.renderOutline((int) target.x - 12, (int) target.y - 6, 24, 12, MahjongUi.POSITIVE);
                graphics.hLine(Math.min(mouseX, (int) target.x), Math.max(mouseX, (int) target.x), mouseY, MahjongUi.POSITIVE);
                graphics.vLine((int) target.x, Math.min(mouseY, (int) target.y), Math.max(mouseY, (int) target.y), MahjongUi.POSITIVE);
            }
        }
        Component help = Component.translatable(TableHandling.help(view));
        var lines = font.split(help, width - 24);
        int y = height - 29 - lines.size() * 10;
        for (var line : lines) {
            graphics.drawCenteredString(font, line, width / 2, y, MahjongUi.ACCENT);
            y += 10;
        }
    }

    private void renderExitVote(GuiGraphics graphics, TableView view) {
        var vote = view.exitVote();
        int span = Math.min(360, width - 24), left = (width - span) / 2, top = height / 2 - 64;
        graphics.fill(left - 6, top, left + span + 6, height / 2 + 56, 0xf21a2a2e);
        graphics.renderOutline(left - 6, top, span + 12, 120, 0xffc4a469);
        graphics.drawCenteredString(font, Component.translatable("ui.mchjong.exit_title"), width / 2, top + 9, 0xffffd487);
        int y = top + 25;
        Component requester = Component.translatable("ui.mchjong.exit_requester", playerName(view, vote.requester()));
        for (var line : font.split(requester, span - 12)) {
            if (y > top + 38) break;
            graphics.drawCenteredString(font, line, width / 2, y, 0xffe0eade); y += 10;
        }
        graphics.drawCenteredString(font, Component.translatable("ui.mchjong.exit_status", vote.agreed().size(), vote.required(), vote.secondsLeft()),
            width / 2, top + 50, 0xffffd487);
        y = top + 65;
        for (var line : font.split(Component.translatable("ui.mchjong.exit_paused"), span - 12)) {
            graphics.drawCenteredString(font, line, width / 2, y, 0xffadd8c4); y += 10;
        }
    }

    public static Component playerName(TableView view, int seat) {
        TableView.Seat player = view.seats().get(seat);
        if (!player.occupied()) return Component.translatable("ui.mchjong.empty");
        return player.bot() ? Component.translatable("ui.mchjong.bot", seat + 1) : Component.literal(player.name());
    }

    private static void elbow(GuiGraphics graphics, Projected point, CalloutButton button, int color) {
        int x = (int) point.x, y = (int) point.y;
        int endX = button.getX() - 4, endY = button.getY() + button.getHeight() / 2;
        int bendX = endX - 18;
        graphics.hLine(Math.min(x, bendX), Math.max(x, bendX), y, color);
        graphics.vLine(bendX, Math.min(y, endY), Math.max(y, endY), color);
        graphics.hLine(bendX, endX, endY, color);
        graphics.fill(x - 2, y - 2, x + 2, y + 2, color);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mappedClick(button)) return true;
        if (overWidget(mouseX, mouseY)) { super.mouseClicked(mouseX, mouseY, button); return true; }
        if (overInformation(mouseX, mouseY)) return true;
        if (button == 1) { dragging = true; dragDistance = 0; return true; }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            if (decision.pending() || dealing()) return true;
            updateScene();
            boolean overHand = hand != null && hand.contains(mouseX, mouseY);
            if (!overHand && openDrawer(mouseX, mouseY)) return true;
            TableScene.Piece physical = overHand ? null : pickPhysical(mouseX, mouseY);
            if (physical != null) {
                handlingDrag = view();
                handlingStart = tablePoint(mouseX, mouseY);
                handlingPointer = handlingStart;
                setFocused(null);
                return true;
            }
            int tile = pick(mouseX, mouseY);
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
        TableView view = view();
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

    private static int discardAction(TableView view, int tile) {
        return tileAction(view, tile, Action.Type.DISCARD);
    }

    private static int tileAction(TableView view, int tile, Action.Type type) {
        for (int i = 0; i < view.actions().size(); i++) {
            Action candidate = view.actions().get(i);
            if (candidate.type() == type && candidate.tiles().contains(tile)) return i;
        }
        return -1;
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (TableKeys.INSPECT.matchesMouse(button)) { inspecting = false; return true; }
        if (button == 0 && handlingDrag != null) {
            TableView snapshot = handlingDrag;
            handlingDrag = null;
            if (view() != null && snapshot.tableId().equals(view().tableId()) && snapshot.decision() == view().decision()
                && !overWidget(mouseX, mouseY) && !overInformation(mouseX, mouseY)
                && TableHandling.completes(snapshot, handlingStart, tablePoint(mouseX, mouseY)))
                send(snapshot, TableHandling.action(snapshot));
            handlingStart = null;
            return true;
        }
        if (button == 1 && dragging) {
            dragging = false;
            if (dragDistance < 4) cancelSelection();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0 && handlingDrag != null) { handlingPointer = tablePoint(mouseX, mouseY); return true; }
        if (button == 1 && dragging && minecraft.player != null) {
            double previous = dragDistance;
            dragDistance += Math.abs(dx) + Math.abs(dy);
            if (!cameraEnabled() || dragDistance <= 4) return true;
            double fraction = previous >= 4 ? 1 : (dragDistance - 4) / (dragDistance - previous);
            var camera = TableSettings.get().camera();
            if (hasShiftDown()) camera.pan(-dx * fraction * .004, -dy * fraction * .004);
            else camera.look(dx * fraction * .35, dy * fraction * .35);
            syncCamera();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        TableView view = view();
        if (key == GLFW.GLFW_KEY_ESCAPE && handlingDrag != null) { handlingDrag = null; handlingStart = null; return true; }
        if (TableKeys.DRAWER.matches(key, scanCode) && openOwnDrawer()) return true;
        if (results != null && results.isFocused() && results.keyPressed(key, scanCode, modifiers)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE && (choosingRiichi || selectedTile >= 0)) { cancelSelection(); return true; }
        if (TableKeys.RESET.matches(key, scanCode)) { resetView(); return true; }
        if (TableKeys.VIEW.matches(key, scanCode)) { toggleView(); return true; }
        if (TableKeys.INSPECT.matches(key, scanCode) && cameraEnabled()) { inspecting = true; return true; }
        if (TableKeys.RIICHI.matches(key, scanCode) && view != null && view.actions().stream().anyMatch(action -> action.type() == Action.Type.RIICHI)) {
            toggleRiichi(); return true;
        }
        if (TableKeys.PASS.matches(key, scanCode) && view != null) {
            for (int i = 0; i < view.actions().size(); i++) if (view.actions().get(i).type() == Action.Type.PASS) { send(view, i); return true; }
        }
        int arrow = arrow(key);
        if (arrow >= 0 && cameraEnabled()) { lookKeys[arrow] = true; return true; }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && selectedTile >= 0 && view != null && getFocused() == null) {
            int action = tileAction(view, selectedTile, choosingRiichi ? Action.Type.RIICHI : Action.Type.DISCARD);
            if (action >= 0) send(view, action);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (TableKeys.INSPECT.matches(key, scanCode)) { inspecting = false; return true; }
        int arrow = arrow(key);
        if (arrow >= 0) { lookKeys[arrow] = false; return true; }
        return super.keyReleased(key, scanCode, modifiers);
    }

    private static int arrow(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_LEFT -> 0;
            case GLFW.GLFW_KEY_RIGHT -> 1;
            case GLFW.GLFW_KEY_UP -> 2;
            case GLFW.GLFW_KEY_DOWN -> 3;
            default -> -1;
        };
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!cameraEnabled() || overWidget(x, y)) return super.mouseScrolled(x, y, horizontal, vertical);
        TableSettings.get().camera().scroll(vertical);
        return true;
    }

    private boolean mappedClick(int button) {
        if (TableKeys.INSPECT.matchesMouse(button) && cameraEnabled()) { inspecting = true; return true; }
        if (TableKeys.RESET.matchesMouse(button)) { resetView(); return true; }
        if (TableKeys.VIEW.matchesMouse(button)) { toggleView(); return true; }
        if (TableKeys.DRAWER.matchesMouse(button)) return openOwnDrawer();
        var view = view();
        if (view == null) return false;
        if (TableKeys.RIICHI.matchesMouse(button) && view.actions().stream().anyMatch(action -> action.type() == Action.Type.RIICHI)) {
            toggleRiichi(); return true;
        }
        if (TableKeys.PASS.matchesMouse(button)) {
            for (int i = 0; i < view.actions().size(); i++) if (view.actions().get(i).type() == Action.Type.PASS) {
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

    private Projected actionPoint(TableView view, Action action) {
        if (!immersive) return project(anchor(view, action));
        int tile = view.focus() == null ? action.tiles().isEmpty() ? Tile.ABSENT : action.tiles().getFirst() : view.focus().tile();
        if (hand != null && hand.centerX(tile) >= 0) return new Projected(hand.centerX(tile), hand.top(), 1);
        var point = board == null ? null : board.point(tile);
        return point == null ? null : new Projected(point.x(), point.y(), 1);
    }

    private void cancelSelection() {
        choosingRiichi = false;
        selectedTile = lastClickedTile = Tile.ABSENT;
        rebuild();
        setFocused(null);
    }

    /** Keyboard focus is attached to the physical source; pointer input uses the actual tile mesh. */
    private final class PhysicalHandle extends MahjongButton {
        private final TableView snapshot;
        PhysicalHandle(TableView snapshot, int index) {
            super(0, 0, 20, 20, Component.translatable(snapshot.actions().get(index).translationKey()), ignored -> send(snapshot, index));
            this.snapshot = snapshot;
            setTooltip(null);
        }
        @Override protected boolean clicked(double mouseX, double mouseY) { return false; }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            TableScene.Piece source = TableHandling.source(snapshot, scene);
            Projected point = source == null ? null : project(grip(source));
            active = point != null && !decision.pending() && !handlingMoving() && results == null;
            if (point == null) return;
            setX((int) point.x - 10); setY((int) point.y - 10);
            if (active && isFocused()) {
                graphics.drawCenteredString(font, getMessage(), (int) point.x, (int) point.y - 22, MahjongUi.TEXT);
            }
        }
    }

    private final class CalloutButton extends MahjongButton {
        final Action action;
        final int actionIndex;
        CalloutButton(int x, int y, int w, int h, Component label, Action action, int actionIndex, Runnable click) {
            super(x, y, w, h, label, ignored -> click.run());
            this.action = action; this.actionIndex = actionIndex;
            if (action.type() == Action.Type.RON || action.type() == Action.Type.TSUMO || action.type() == Action.Type.READY) primary();
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderSurface(graphics);
            TableView view = view();
            int icons = view != null && TableSettings.get().actionTiles ? actionPreviewWidth(view, action) : 0;
            boolean caption = icons == 0 || width >= icons + 36;
            int captionWidth = Math.max(16, width - icons - 16);
            var lines = caption ? font.split(getMessage(), captionWidth).stream().limit(2).toList() : List.<net.minecraft.util.FormattedCharSequence>of();
            int y = getY() + (height - lines.size() * font.lineHeight) / 2;
            for (var line : lines) {
                graphics.drawString(font, line, getX() + 8 + (captionWidth - font.width(line)) / 2,
                    y, active ? MahjongUi.TEXT : MahjongUi.DISABLED, false);
                y += font.lineHeight;
            }
            if (icons > 0 && view != null) {
                ActionPreview preview = ActionPreview.of(view, action);
                int x = getX() + (caption ? width - icons + 3 : (width - icons) / 2 + 3);
                if (preview.meld() != null) TileGui.meld(graphics, preview.meld(), view.viewerSeat(), x, getY() + 4, 9, facePreset());
                else for (int i = 0; i < preview.tiles().size(); i++)
                    TileGui.tile(graphics, preview.tiles().get(i), x + i * 13, getY() + 4, 9, false, false, false, facePreset());
            }
        }
    }
}
