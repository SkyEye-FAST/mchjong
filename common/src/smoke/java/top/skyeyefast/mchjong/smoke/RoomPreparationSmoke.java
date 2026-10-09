package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.RiichiTableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.engine.BotDifficulty;
import top.skyeyefast.mchjong.engine.RoomSeating;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Drives the actual client controls and physical remount after the room lottery. */
final class RoomPreparationSmoke {
    private final int wind;
    private CompletableFuture<Integer> serverWork;
    private int ticks;
    private int windSlot = -1;
    private boolean requestedWind;
    private boolean capturedDrawing;
    private boolean capturedPositioning;
    private int botSeat = -1;
    private int botCycle;
    private Boolean originalAutoSeat;
    private int cueUntil = -1;

    RoomPreparationSmoke() { this(0); }
    RoomPreparationSmoke(int wind) { this.wind = wind; }

    void restoreSettings() { if (originalAutoSeat != null) TableSettings.get().autoSeat = originalAutoSeat; }

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output, String prefix) {
        var view = table.clientView();
        if (originalAutoSeat == null) {
            originalAutoSeat = TableSettings.get().autoSeat;
            TableSettings.get().autoSeat = table.automatic() && !prefix.startsWith("room");
        }
        if (view != null) {
            restoreSettings();
            return true;
        }
        if (++ticks > 400) throw new IllegalStateException("Seat preparation timed out: " + table.clientRoom());
        if (serverWork != null) {
            if (!serverWork.isDone()) return false;
            int value = serverWork.join();
            if (value >= 0) windSlot = value;
            serverWork = null;
        }
        var room = table.clientRoom();
        if (room == null) return false;
        if (cueUntil >= 0) {
            var stool = top.skyeyefast.mchjong.world.TableGeometry.stool(table.getBlockPos(), room.viewerSeat());
            var direction = net.minecraft.world.phys.Vec3.atCenterOf(stool).add(0, 0.6, 0).subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
            if (ticks < cueUntil) return false;
            capture(client, output, prefix + "-stool-cue.png");
            client.setScreen(new RiichiTableScreen(table.getBlockPos()));
            cueUntil = -1;
        }
        if (RiichiTableScreen.active(client.screen) == null || ticks % 5 != 0) return false;
        if (room.seating() == RoomSeating.Stage.GATHERING) {
            var roster = LobbySmoke.find(client, Component.translatable("room.mchjong.participants").getString());
            if (roster != null) roster.onPress();
            boolean full = room.actions().stream().anyMatch(action -> action.type() == RoomAction.Type.BEGIN_SEATING);
            if (full && botCycle < 4 && botCycle != 2) {
                if (botSeat < 0) for (int seat = 0; seat < room.seats().size(); seat++)
                    if (room.seats().get(seat).participant().bot()) { botSeat = seat; break; }
                if (botSeat >= 0) {
                    var occupant = room.seats().get(botSeat).participant();
                    var difficulty = room.seats().get(botSeat).participant().difficulty();
                    if (botCycle == 0) {
                        if (difficulty != BotDifficulty.EASY) throw new IllegalStateException("Fill must create Easy bots");
                        click(client, BotDifficulty.EASY.translationKey());
                        botCycle = 1;
                    } else if (botCycle == 1 && difficulty == BotDifficulty.HARD) {
                        capture(client, output, prefix + "-bot-hard.png");
                        click(client, BotDifficulty.HARD.translationKey());
                        botCycle = 2;
                    } else if (botCycle == 3 && occupant.bot() && difficulty == BotDifficulty.EASY) {
                        capture(client, output, prefix + "-bot-easy.png");
                        botCycle = 4;
                    }
                    return false;
                }
                botCycle = 4;
            }
            if (botCycle == 2) {
                if (room.seats().get(botSeat).participant().id() == null) {
                    click(client, "room.mchjong.add_bot");
                    botCycle = 3;
                }
                return false;
            }
            if (botCycle == 3 && !full) return false;
            click(client, full
                ? table.automatic() ? "room.mchjong.start_auto" : "room.mchjong.start_manual" : "action.mchjong.fill_bots");
        } else if (room.seating() == RoomSeating.Stage.DRAWING) {
            if (!capturedDrawing) {
                AutomationControlsSmoke.checkBounds(client);
                capture(client, output, prefix + "-wind-draw.png");
                capturedDrawing = true;
            }
            if (!requestedWind) {
                requestedWind = true;
                var id = client.player.getUUID();
                var pos = table.getBlockPos();
                // Only the server-side test fixture reads this; the normal snapshot hides the bag.
                // Normal dealing fixtures choose east; the reassignment check chooses west.
                serverWork = client.getSingleplayerServer().submit(() -> {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                    var serverTable = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos);
                    var game = serverTable.participantSession(player);
                    var bag = TableNetworking.JSON.toJsonTree(game).getAsJsonObject().getAsJsonObject("seating").getAsJsonArray("concealed");
                    for (int slot = 0; slot < bag.size(); slot++) if (bag.get(slot).getAsInt() == wind) return slot;
                    throw new IllegalStateException("Wind bag is missing " + wind);
                });
            } else if (windSlot >= 0) click(client, "room.mchjong.wind_tile", windSlot + 1);
        } else if (room.viewerSeat() >= 0) {
            var state = room.seats().get(room.viewerSeat());
            if (!capturedPositioning) {
                AutomationControlsSmoke.checkBounds(client);
                capture(client, output, prefix + "-assigned-seats.png");
                capturedPositioning = true;
                if (prefix.startsWith("room") && state.presence() != top.skyeyefast.mchjong.engine.PlayerPresence.SEATED) {
                    var stool = top.skyeyefast.mchjong.world.TableGeometry.stool(table.getBlockPos(), room.viewerSeat());
                    var direction = net.minecraft.world.phys.Vec3.atCenterOf(stool).subtract(client.player.getEyePosition());
                    client.player.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
                    client.player.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
                    client.setScreen(null);
                    cueUntil = ticks + 12;
                    return false;
                }
            }
            if (state.presence() != top.skyeyefast.mchjong.engine.PlayerPresence.SEATED) {
                if (room.actions().stream().anyMatch(action -> action.type() == RoomAction.Type.READY))
                    throw new IllegalStateException("Unseated player can ready up");
                if (TableSettings.get().autoSeat) return false;
                var id = client.player.getUUID();
                var pos = table.getBlockPos();
                serverWork = client.getSingleplayerServer().submit(() -> {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                    var serverTable = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos);
                    var game = serverTable.participantSession(player);
                    int seat = game.seatOf(id);
                    player.stopRiding();
                    serverTable.sit(player, seat);
                    if (serverTable.participantSession(player) == null || game.roomView(id).seats().get(seat).presence()
                        != top.skyeyefast.mchjong.engine.PlayerPresence.SEATED)
                        throw new IllegalStateException("Could not occupy the assigned stool");
                    return -1;
                });
            } else if (!state.participant().ready()) {
                AutomationControlsSmoke.checkBounds(client);
                capture(client, output, prefix + "-ready.png");
                click(client, "action.mchjong.ready");
            }
        }
        return false;
    }

    private static void click(Minecraft client, String key, Object... arguments) {
        String label = Component.translatable(key, arguments).getString();
        clickLabel(client, label);
    }

    private static void clickLabel(Minecraft client, String label) {
        for (var child : client.screen.children()) if (child instanceof AbstractWidget button && button.active
            && (button.getMessage().getString().equals(label) || button.getMessage().getString().equals(label + " ›"))) {
            client.screen.mouseClicked(button.getX() + 3, button.getY() + 3, 0);
            return;
        }
    }

    private static void capture(Minecraft client, Path output, String name) {
        SmokeScreenshots.grab(output.toFile(), name, client.getMainRenderTarget(), ignored -> {});
    }
}
