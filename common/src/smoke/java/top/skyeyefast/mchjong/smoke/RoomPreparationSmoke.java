package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.BotDifficulty;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.PlayerPresence;
import top.skyeyefast.mchjong.engine.RoomSeating;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Drives the actual client controls and physical remount after the room lottery. */
final class RoomPreparationSmoke {
    private CompletableFuture<Integer> serverWork;
    private int ticks;
    private int windSlot = -1;
    private boolean requestedWind;
    private boolean capturedDrawing;
    private boolean capturedPositioning;
    private int botSeat = -1;
    private int botCycle;
    private Boolean originalAutoSeat;

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output, String prefix) {
        var view = table.clientView();
        if (originalAutoSeat == null) {
            originalAutoSeat = TableSettings.get().autoSeat;
            TableSettings.get().autoSeat = table.automatic();
        }
        if (view != null && view.phase() != Game.Phase.LOBBY) {
            TableSettings.get().autoSeat = originalAutoSeat;
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
        if (view == null || room == null) return false;
        if (TableScreen.active(client.screen) == null || ticks % 5 != 0) return false;
        if (room.seating() == RoomSeating.Stage.GATHERING) {
            boolean full = view.actions().stream().anyMatch(action -> action.type() == Action.Type.BEGIN_SEATING);
            if (full && botCycle < 4) {
                if (botSeat < 0) for (int seat = 0; seat < view.seats().size(); seat++)
                    if (view.seats().get(seat).bot()) { botSeat = seat; break; }
                if (botSeat >= 0) {
                    var occupant = view.seats().get(botSeat);
                    var difficulty = room.seats().get(botSeat).difficulty();
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
                if (!view.seats().get(botSeat).occupied()) {
                    click(client, "room.mchjong.add_bot");
                    botCycle = 3;
                }
                return false;
            }
            if (botCycle == 3 && !full) return false;
            click(client, full
                ? table.automatic() ? "room.mchjong.start_auto" : "room.mchjong.start_manual" : "room.mchjong.start_bots");
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
                // Choosing east keeps subsequent manual-dealing and drawer fixtures on seat zero.
                serverWork = client.getSingleplayerServer().submit(() -> {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                    var serverTable = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos);
                    var game = serverTable.participantGame(player);
                    var bag = TableNetworking.JSON.toJsonTree(game).getAsJsonObject().getAsJsonObject("seating").getAsJsonArray("concealed");
                    for (int slot = 0; slot < bag.size(); slot++) if (bag.get(slot).getAsInt() == 0) return slot;
                    throw new IllegalStateException("Wind bag has no east");
                });
            } else if (windSlot >= 0) click(client, "room.mchjong.wind_tile", windSlot + 1);
        } else if (view.viewerSeat() >= 0) {
            var state = room.seats().get(view.viewerSeat());
            if (!capturedPositioning) {
                AutomationControlsSmoke.checkBounds(client);
                capture(client, output, prefix + "-assigned-seats.png");
                capturedPositioning = true;
            }
            if (state.presence() != top.skyeyefast.mchjong.engine.PlayerPresence.SEATED) {
                if (view.actions().stream().anyMatch(action -> action.type() == Action.Type.READY))
                    throw new IllegalStateException("Unseated player can ready up");
                if (table.automatic()) return false;
                var id = client.player.getUUID();
                var pos = table.getBlockPos();
                serverWork = client.getSingleplayerServer().submit(() -> {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                    var serverTable = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos);
                    var game = serverTable.participantGame(player);
                    int seat = game.seatOf(id);
                    player.stopRiding();
                    serverTable.sit(player, seat);
                    if (serverTable.participantGame(player) == null || game.roomView().seats().get(seat).presence()
                        != top.skyeyefast.mchjong.engine.PlayerPresence.SEATED)
                        throw new IllegalStateException("Could not occupy the assigned stool");
                    return -1;
                });
            } else if (!view.seats().get(view.viewerSeat()).ready()) {
                AutomationControlsSmoke.checkBounds(client);
                capture(client, output, prefix + "-ready.png");
                click(client, "action.mchjong.ready");
            }
        }
        return false;
    }

    private static void click(Minecraft client, String key, Object... arguments) {
        String label = Component.translatable(key, arguments).getString();
        for (var child : client.screen.children()) if (child instanceof AbstractWidget button && button.active
            && (button.getMessage().getString().equals(label) || button.getMessage().getString().equals(label + " ›"))) {
            client.screen.mouseClicked(button.getX() + 3, button.getY() + 3, 0);
            return;
        }
    }

    private static void capture(Minecraft client, Path output, String name) {
        net.minecraft.client.Screenshot.grab(output.toFile(), name, client.getMainRenderTarget(), ignored -> {});
    }
}
