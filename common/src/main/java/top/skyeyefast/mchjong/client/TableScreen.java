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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.mixin.GameRendererAccessor;
import top.skyeyefast.mchjong.network.TableActionPayload;
import top.skyeyefast.mchjong.network.TableControlPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** A transparent, non-pausing interaction layer over the actual world, not a second table renderer. */
public final class TableScreen extends Screen {
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
    private double dragDistance;
    private float framePartial;
    private long lastClickAt;
    private int lastClickedTile = Tile.ABSENT;
    private TableResults results;
    private boolean resultsExpanded = true;
    private Game.Phase lastPhase;
    private TableResults.Page resultPage = TableResults.Page.HAND;
    private long resultStarted;
    private long resultPageStarted;
    private Component informationTooltip;
    private final TableHud information = new TableHud();
    private int actionTop;

    public TableScreen(BlockPos pos) { super(Component.translatable("ui.mchjong.title")); this.pos = pos.immutable(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    public BlockPos tablePos() { return pos; }

    public static TableScreen active(Screen screen) {
        if (screen instanceof TableScreen table) return table;
        if (screen instanceof TableSettingsScreen settings) return settings.tableScreen();
        if (screen instanceof TableClockScreen clock) return clock.tableScreen();
        if (screen instanceof TableInviteScreen invite) return invite.tableScreen();
        return null;
    }

    public void resetView() {
        TableView view = view();
        if (minecraft == null || minecraft.player == null || view == null || view.viewerSeat() < 0) return;
        minecraft.player.setYRot(TableGeometry.yaw(view.viewerSeat()));
        TableSettings settings = TableSettings.get();
        minecraft.player.setXRot((float) Math.toDegrees(Math.atan2(settings.cameraHeight - TableGeometry.FELT_Y, settings.cameraDistance)));
        minecraft.player.yRotO = minecraft.player.getYRot();
        minecraft.player.xRotO = minecraft.player.getXRot();
    }

    private TableView view() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table
            ? table.clientView() : null;
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

    public void receivedView() {
        refreshDecision(view());
        lastRevision = -1;
    }

    private void refreshDecision(TableView view) {
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

    @Override protected void init() { lastRevision = -1; rebuild(); }

    @Override public void tick() {
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
        clearWidgets();
        callouts.clear();
        confirmButton = null;
        TableView view = view();
        if (view == null) return;
        refreshDecision(view);
        boolean newResult = lastPhase != view.phase() && TableResults.available(view);
        if (newResult) {
            resultsExpanded = true;
            resultPage = TableResults.Page.HAND;
            resultStarted = Util.getMillis();
            resultPageStarted = resultStarted;
        }
        int selectedWinner = results == null || newResult ? 0 : results.selectedWinner();
        results = null;
        lastPhase = view.phase();
        lastRevision = view.revision();
        updateScene();
        if (view.viewerSeat() < 0 || !view.seats().get(view.viewerSeat()).hand().contains(selectedTile)) selectedTile = Tile.ABSENT;
        if (view.actions().stream().noneMatch(action -> action.type() == Action.Type.RIICHI)) choosingRiichi = false;
        buildToolbar(view);
        if (view.exitVote() != null) { buildExitVote(view); return; }
        if (view.phase() == Game.Phase.LOBBY) { buildLobby(view); return; }
        List<Integer> choices = new ArrayList<>();
        for (int i = 0; i < view.actions().size(); i++) {
            Action action = view.actions().get(i);
            if (action.type() == Action.Type.DISCARD || action.type() == Action.Type.RIICHI) continue;
            choices.add(i);
        }
        boolean riichi = view.actions().stream().anyMatch(action -> action.type() == Action.Type.RIICHI);
        int discard = (choosingRiichi || TableSettings.get().discardMode == TableSettings.DiscardMode.CONFIRM) && selectedTile >= 0
            ? tileAction(view, selectedTile, choosingRiichi ? Action.Type.RIICHI : Action.Type.DISCARD) : -1;
        int count = choices.size() + (riichi ? 1 : 0) + (discard >= 0 ? 1 : 0);
        int columns = Math.min(Math.max(1, count), Math.max(1, Math.min(3, (width - 20) / 100)));
        int boxWidth = Math.min(132, (width - 20 - (columns - 1) * 4) / columns);
        int rows = Math.max(1, (count + columns - 1) / columns);
        actionTop = height - 43 - (rows - 1) * 30;
        int startX = width - 10 - columns * (boxWidth + 4) + 4;
        int slot = 0;
        if (riichi) {
            addRenderableWidget(Button.builder(Component.translatable(choosingRiichi ? "ui.mchjong.cancel_riichi" : "action.mchjong.riichi"),
                ignored -> toggleRiichi()).bounds(startX, actionTop, boxWidth, 26).build());
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
            addRenderableWidget(Button.builder(Component.translatable(resultsExpanded ? "ui.mchjong.view_table" : "ui.mchjong.view_results"),
                ignored -> { resultsExpanded = !resultsExpanded; rebuild(); }).bounds(10, height - 48, boxWidth, 20).build());
            if (resultsExpanded) {
                int tabs = view.phase() == Game.Phase.MATCH_END ? 3 : 2;
                int tabWidth = (width - 20) / tabs;
                for (int i = 0; i < tabs; i++) {
                    var page = TableResults.Page.values()[i];
                    var button = Button.builder(Component.translatable("ui.mchjong.result_page." + i), ignored -> {
                        resultPage = page; resultPageStarted = Util.getMillis(); results = null; rebuild();
                    }).bounds(10 + i * tabWidth, view.exitVote() == null ? 34 : 58, tabWidth - 3, 20).build();
                    button.active = page != resultPage;
                    addRenderableWidget(button);
                }
                int panelTop = view.exitVote() == null ? 58 : 82;
                results = addRenderableWidget(new TableResults(font, view, 10, panelTop, width - 20, height - panelTop - 54,
                    selectedWinner, resultPage, resultPageStarted));
            }
        }
        if (discard >= 0) {
                final TableView snapshot = view;
                final int actionIndex = discard;
                confirmButton = addRenderableWidget(Button.builder(Component.translatable(choosingRiichi ? "ui.mchjong.confirm_riichi" : "action.mchjong.discard"), ignored -> send(snapshot, actionIndex))
                    .bounds(startX + slot % columns * (boxWidth + 4), actionTop + slot / columns * 30, boxWidth, 26).build());
                confirmButton.setTooltip(Tooltip.create(Component.translatable("ui.mchjong.confirm_hint")));
        }
    }

    private void buildToolbar(TableView view) {
        int right = width - 8;
        addRenderableWidget(Button.builder(Component.literal("…"), ignored -> minecraft.setScreen(new TableSettingsScreen(this)))
            .bounds(right - 22, 8, 22, 20).tooltip(Tooltip.create(Component.translatable("settings.mchjong.title"))).build());
        right -= 26;
        if (view.viewerSeat() >= 0) {
            var exit = Button.builder(Component.translatable("ui.mchjong.exit"), ignored ->
                control(view, TableControlPayload.Operation.REQUEST_EXIT, view.decision(), false))
                .bounds(right - 48, 8, 48, 20).tooltip(Tooltip.create(Component.translatable("ui.mchjong.exit_hint"))).build();
            exit.active = view.exitVote() == null;
            addRenderableWidget(exit);
            right -= 52;
        }
        addRenderableWidget(Button.builder(Component.translatable("ui.mchjong.center_short"), ignored -> resetView())
            .bounds(right - 48, 8, 48, 20).tooltip(Tooltip.create(Component.translatable("ui.mchjong.center_view"))).build());
        right -= 52;
        addRenderableWidget(Button.builder(Component.translatable("replay.mchjong.title"), ignored -> ClientReplays.list(0))
            .bounds(right - 52, 8, 52, 20).build());
    }

    private void control(TableView view, TableControlPayload.Operation operation, long token, boolean enabled) {
        if (minecraft.getConnection() == null) return;
        minecraft.getConnection().send(new ServerboundCustomPayloadPacket(new TableControlPayload(pos, view.tableId(), operation, token, enabled)));
    }

    private void buildExitVote(TableView view) {
        var vote = view.exitVote();
        if (vote == null || view.viewerSeat() < 0) return;
        int span = Math.min(360, width - 24), left = (width - span) / 2;
        int y = height / 2 + 30;
        var agree = Button.builder(Component.translatable("ui.mchjong.exit_agree"), ignored ->
            control(view, TableControlPayload.Operation.ANSWER_EXIT, vote.id(), true)).bounds(left, y, (span - 4) / 2, 20).build();
        agree.active = !vote.agreed().contains(view.viewerSeat());
        addRenderableWidget(agree);
        addRenderableWidget(Button.builder(Component.translatable("ui.mchjong.exit_reject"), ignored ->
            control(view, TableControlPayload.Operation.ANSWER_EXIT, vote.id(), false)).bounds(left + (span + 4) / 2, y, (span - 4) / 2, 20).build());
    }

    private void buildLobby(TableView view) {
        int span = Math.min(400, width - 20), left = (width - span) / 2;
        int half = (span - 4) / 2;
        boolean host = view.actions().stream().anyMatch(action -> action.type() == Action.Type.PRACTICE);
        for (int players : new int[]{4, 3}) {
            RuleSet rule = view.rules().tenhou() ? players == 4 ? RuleSet.TENHOU_4 : RuleSet.TENHOU_3
                : players == 4 ? RuleSet.MAHJONG_SOUL_4 : RuleSet.MAHJONG_SOUL_3;
            int action = ruleAction(view, rule);
            var mode = Button.builder(Component.translatable("ui.mchjong.players." + players), ignored -> send(view, action))
                .bounds(left + (4 - players) * (half + 4), 64, half, 20).build();
            mode.active = host && view.rules().players() != players && action >= 0;
            addRenderableWidget(mode);
        }
        var presets = java.util.Arrays.stream(RuleSet.values()).filter(rule -> rule.players() == view.rules().players()).toList();
        int presetWidth = (span - (presets.size() - 1) * 4) / presets.size();
        for (int i = 0; i < presets.size(); i++) {
            RuleSet rule = presets.get(i);
            int action = ruleAction(view, rule);
            var preset = Button.builder(Component.translatable(rule.presetKey()), ignored -> send(view, action))
                .bounds(left + i * (presetWidth + 4), 88, presetWidth, 20)
                .tooltip(Tooltip.create(Component.translatable(rule.translationKey()))).build();
            preset.active = host && action >= 0;
            addRenderableWidget(preset);
        }
        int y = 112;
        var visible = Button.builder(Component.translatable("settings.mchjong.toggle", Component.translatable("ui.mchjong.open_hands"),
            Component.translatable(view.openHands() ? "options.on" : "options.off")), ignored ->
                control(view, TableControlPayload.Operation.OPEN_HANDS, view.decision(), !view.openHands()))
            .bounds(left, y, span, 20).tooltip(Tooltip.create(Component.translatable("ui.mchjong.open_hands_hint"))).build();
        visible.active = host;
        addRenderableWidget(visible);
        y += 24;
        var clock = Button.builder(Component.translatable("ui.mchjong.clock_settings"), ignored ->
            minecraft.setScreen(new TableClockScreen(this, view.timeControl()))).bounds(left, y, half, 20).build();
        clock.active = host;
        addRenderableWidget(clock);
        var invite = Button.builder(Component.translatable("ui.mchjong.invite"), ignored ->
            minecraft.setScreen(new TableInviteScreen(this))).bounds(left + half + 4, y, half, 20).build();
        invite.active = view.viewerSeat() >= 0;
        addRenderableWidget(invite);
        y += 24;
        int column = 0;
        for (int i = 0; i < view.actions().size(); i++) {
            var action = view.actions().get(i);
            if (action.type() != Action.Type.READY && action.type() != Action.Type.PRACTICE) continue;
            int index = i;
            var button = new CalloutButton(left + column * (half + 4), y, half, 26,
                Component.translatable(action.type() == Action.Type.PRACTICE ? "ui.mchjong.practice_short" : action.translationKey()),
                action, i, () -> send(view, index));
            addRenderableWidget(button); callouts.add(button);
            column++;
        }
        actionTop = y;
    }

    private static int ruleAction(TableView view, RuleSet rule) {
        for (int i = 0; i < view.actions().size(); i++) {
            var action = view.actions().get(i);
            if (action.type() == Action.Type.CHANGE_RULE && action.tiles().getFirst() == rule.ordinal()) return i;
        }
        return -1;
    }

    private void send(TableView snapshot, int index) {
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
            return TableGeometry.orient(0, TableGeometry.FELT_Y + 0.08, 1.24, view.viewerSeat());
        return new Vec3(0, TableGeometry.FELT_Y + 0.045, 0);
    }

    private record Projected(double x, double y, double scale) {}

    private Projected project(Vec3 relative) {
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

    private TableScene.Piece pick(double mouseX, double mouseY) {
        TableView view = view();
        if (view == null || view.viewerSeat() < 0 || view.exitVote() != null || dealing() || TableResults.available(view)
            || overWidget(mouseX, mouseY) || overInformation(mouseX, mouseY)) return null;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        double yaw = Math.toRadians(camera.getYRot()), pitch = Math.toRadians(camera.getXRot());
        Vec3 forward = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        double fov = ((GameRendererAccessor) minecraft.gameRenderer).mchjong$getFov(camera, framePartial, true);
        double focal = height / (2 * Math.tan(Math.toRadians(fov) / 2));
        Vec3 ray = forward.add(right.scale((mouseX - width / 2.0) / focal))
            .add(right.cross(forward).scale((height / 2.0 - mouseY) / focal));
        Vec3 origin = camera.getPosition().subtract(TableGeometry.world(pos, Vec3.ZERO));
        TableScene.Piece best = null;
        double closest = Double.MAX_VALUE;
        for (TableAnimation.Frame frame : frames) {
            TableScene.Piece piece = frame.piece();
            if (piece.area() != TableScene.Area.HAND || piece.seat() != view.viewerSeat()) continue;
            if (choosingRiichi && tileAction(view, piece.tile(), Action.Type.RIICHI) < 0) continue;
            double distance = TilePicking.distanceSquared(frame, origin, ray, selected(pos, piece));
            if (distance < closest) {
                closest = distance;
                best = piece;
            }
        }
        return best;
    }

    private boolean overWidget(double x, double y) {
        return children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
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
        if (view.exitVote() != null) {
            renderExitVote(graphics, view);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        if (!TableResults.available(view)) information.render(font, graphics, view, width);
        TableScene.Piece hovered = pick(mouseX, mouseY);
        hoveredTile = hovered == null ? Tile.ABSENT : hovered.tile();
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
                Projected point = project(anchor(view, button.action));
                if (point != null) elbow(graphics, point, button, button.isHoveredOrFocused() ? 0xffffdc89 : 0xff8fbca9);
            }
            if (settings.highlightTiles && showsConsumed(button.action) && button.isHoveredOrFocused()) {
                for (TableScene.Piece piece : scene) if (piece.area() == TableScene.Area.HAND && piece.seat() == view.viewerSeat()
                    && button.action.tiles().contains(piece.tile())) {
                    Projected own = project(piece.position());
                    if (own != null) elbow(graphics, own, button, 0xffdfc98e);
                }
            }
        }
        if (selectedTile >= 0) for (TableScene.Piece piece : scene) if (piece.area() == TableScene.Area.HAND
            && piece.seat() == view.viewerSeat() && piece.tile() == selectedTile) {
            Projected point = project(piece.position());
            if (point != null) {
                int radius = Math.max(5, (int)(point.scale * 0.055));
                graphics.renderOutline((int)point.x-radius, (int)point.y-radius, radius*2, radius*2, 0xfff6d483);
            }
        }
        if (choosingRiichi) for (TableScene.Piece piece : scene) {
            if (piece.area() != TableScene.Area.HAND || piece.seat() != view.viewerSeat()
                || tileAction(view, piece.tile(), Action.Type.RIICHI) < 0) continue;
            Projected point = project(piece.position());
            if (point != null) {
                int radius = Math.max(3, (int) (point.scale * 0.045));
                graphics.hLine((int) point.x - radius, (int) point.x + radius, (int) (point.y + point.scale * 0.08), 0xffffd487);
            }
        }
        if (choosingRiichi) graphics.drawCenteredString(font, Component.translatable("ui.mchjong.choose_riichi"), width / 2, actionTop - 14, 0xffffd487);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!TableResults.available(view) && view.viewerSeat() >= 0 && view.viewerSeat() < view.clocks().size()) {
            var table = (MahjongTableBlockEntity) minecraft.level.getBlockEntity(pos);
            var clock = view.clocks().get(view.viewerSeat()).after(table.clientViewAgeMillis());
            if (clock.active()) {
                Component text = Component.translatable("ui.mchjong.clock", clock.moveSeconds(), clock.reserveSeconds());
                graphics.drawCenteredString(font, text, width / 2, actionTop - (choosingRiichi ? 28 : 14),
                    clock.moveTicks() + clock.reserveTicks() <= 100 ? 0xffffaaa0 : 0xffffd487);
            }
        }
        if (TableResults.available(view)) {
            long ready = view.seats().stream().filter(TableView.Seat::ready).count();
            graphics.drawString(font, Component.translatable("ui.mchjong.ready_count", ready, view.seats().size()),
                10, 14, 0xffd1e4d9);
        }
        if (dealing()) graphics.drawCenteredString(font, Component.translatable("ui.mchjong.dealing"), width / 2, height - 29, 0xffffd487);
        else if (TableSettings.get().animations && animation() != null && !TableResults.available(view)) {
            int cueY = 88;
            for (var cue : animation().cues(Util.getMillis())) {
                graphics.drawCenteredString(font, playerName(view, cue.seat()).copy().append(" · ").append(Component.translatable(cue.key())),
                    width / 2, cueY, 0xffffd487);
                cueY += 13;
            }
        }
        if (settings.show(TableSettings.Information.HELP)) {
            String helpKey = TableResults.available(view) ? "ui.mchjong.result_help" : view.viewerSeat() < 0 ? "ui.mchjong.spectator_help"
                : choosingRiichi ? "ui.mchjong.riichi_help" : "ui.mchjong.help_" + settings.discardMode.name().toLowerCase(java.util.Locale.ROOT);
            Component help = Component.translatable(helpKey);
            graphics.drawString(font, font.plainSubstrByWidth(help.getString(), width - 20), 10, height - 13, 0xffe0deca, true);
        }
        if (informationTooltip != null && !overWidget(mouseX, mouseY))
            graphics.renderTooltip(font, font.split(informationTooltip, Math.min(320, width - 24)), mouseX, mouseY);
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
        if (overWidget(mouseX, mouseY)) { super.mouseClicked(mouseX, mouseY, button); return true; }
        if (overInformation(mouseX, mouseY)) return true;
        if (button == 1) { dragging = true; dragDistance = 0; return true; }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            if (decision.pending() || dealing()) return true;
            updateScene();
            TableScene.Piece piece = pick(mouseX, mouseY);
            if (piece != null && !choosingRiichi && !hasShiftDown() && discardFromClick(piece.tile())) return true;
            selectedTile = piece == null ? Tile.ABSENT : piece.tile();
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
        if (button == 1 && dragging) {
            dragging = false;
            if (dragDistance < 4) cancelSelection();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging && minecraft.player != null) {
            dragDistance += Math.abs(dx) + Math.abs(dy);
            minecraft.player.setYRot(minecraft.player.getYRot() + (float) dx * 0.35f);
            minecraft.player.setXRot(Mth.clamp(minecraft.player.getXRot() + (float) dy * 0.35f, -25, 85));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        TableView view = view();
        if (results != null && results.isFocused() && results.keyPressed(key, scanCode, modifiers)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE && (choosingRiichi || selectedTile >= 0)) { cancelSelection(); return true; }
        if (key == GLFW.GLFW_KEY_HOME) { resetView(); return true; }
        if (key == GLFW.GLFW_KEY_R && view != null && view.actions().stream().anyMatch(action -> action.type() == Action.Type.RIICHI)) {
            toggleRiichi(); return true;
        }
        if (key == GLFW.GLFW_KEY_P && view != null) {
            for (int i = 0; i < view.actions().size(); i++) if (view.actions().get(i).type() == Action.Type.PASS) { send(view, i); return true; }
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && selectedTile >= 0 && view != null && getFocused() == null) {
            int action = tileAction(view, selectedTile, choosingRiichi ? Action.Type.RIICHI : Action.Type.DISCARD);
            if (action >= 0) send(view, action);
            return true;
        }
        if ((key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) && view != null && view.viewerSeat() >= 0) {
            if (TableResults.available(view) || dealing() || decision.pending()) return super.keyPressed(key, scanCode, modifiers);
            List<Integer> hand = view.seats().get(view.viewerSeat()).hand();
            if (choosingRiichi) hand = hand.stream().filter(tile -> tileAction(view, tile, Action.Type.RIICHI) >= 0).toList();
            if (!hand.isEmpty()) {
                int index = hand.indexOf(selectedTile);
                selectedTile = hand.get(index < 0 ? key == GLFW.GLFW_KEY_LEFT ? hand.size() - 1 : 0
                    : Math.floorMod(index + (key == GLFW.GLFW_KEY_LEFT ? -1 : 1), hand.size()));
                rebuild();
                setFocused(null);
            }
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void toggleRiichi() {
        if (decision.pending() || dealing()) return;
        choosingRiichi = !choosingRiichi;
        selectedTile = lastClickedTile = Tile.ABSENT;
        rebuild();
        setFocused(null);
    }

    private void cancelSelection() {
        choosingRiichi = false;
        selectedTile = lastClickedTile = Tile.ABSENT;
        rebuild();
        setFocused(null);
    }

    private final class CalloutButton extends Button {
        final Action action;
        final int actionIndex;
        CalloutButton(int x, int y, int w, int h, Component label, Action action, int actionIndex, Runnable click) {
            super(x, y, w, h, label, ignored -> click.run(), DEFAULT_NARRATION);
            this.action = action; this.actionIndex = actionIndex;
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int border = isHoveredOrFocused() ? 0xffe9c780 : active ? 0xff709b90 : 0xff405e5a;
            graphics.fill(getX(), getY(), getX()+width, getY()+height, 0xf01b2f32);
            graphics.renderOutline(getX(), getY(), width, height, border);
            graphics.fill(getX()+2, getY()+2, getX()+4, getY()+height-2, border);
            TableView view = view();
            int icons = view != null && TableSettings.get().actionTiles ? actionPreviewWidth(view, action) : 0;
            int y = getY() + 5;
            for (var line : font.split(getMessage(), Math.max(16, width - icons - 16)).stream().limit(2).toList()) {
                graphics.drawString(font, line, getX()+9, y, active ? 0xfff0e7d2 : 0xff9ba99e, false);
                y += 9;
            }
            if (icons > 0 && view != null) {
                ActionPreview preview = ActionPreview.of(view, action);
                int x = getX() + width - icons + 3;
                if (preview.meld() != null) TileGui.meld(graphics, preview.meld(), view.viewerSeat(), x, getY() + 4, 9);
                else for (int i = 0; i < preview.tiles().size(); i++)
                    TileGui.tile(graphics, preview.tiles().get(i), x + i * 13, getY() + 4, 9, false, false, false);
            }
        }
    }
}
