package top.skyeyefast.mchjong.smoke;

import java.util.UUID;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.RoomSeating;

/** Fixed-seat fixtures for equipment, currency and replay checks; never included in a release jar. */
public final class SeatingFixtures {
    private SeatingFixtures() {}

    public static void startPositioned(RiichiGame game, UUID... humans) {
        var host = game.roomView(humans[0]);
        for (int i = 0; i < host.actions().size(); i++) if (host.actions().get(i).type() == RoomAction.Type.FILL_BOTS)
            if (!game.actRoom(humans[0], host.tableId(), host.incarnation(), host.decision(), i))
                throw new IllegalStateException("Cannot fill fixture");
        // Keep privileged fixture setup outside the engine's package: NeoForge uses distinct modules.
        try {
            var seating = new RoomSeating();
            var position = RoomSeating.class.getDeclaredMethod("positioned", int.class);
            position.setAccessible(true);
            position.invoke(seating, game.rules().players());
            var field = top.skyeyefast.mchjong.engine.TableSession.class.getDeclaredField("seating");
            field.setAccessible(true);
            field.set(game, seating);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot assign fixture seats", failure);
        }
        for (UUID human : humans) {
            var view = game.view(human);
            if (!game.join(human, view.seats().get(view.viewerSeat()).name(), view.viewerSeat()))
                throw new IllegalStateException("Cannot position fixture");
        }
        for (UUID human : humans) {
            var view = game.roomView(human);
            int ready = -1;
            for (int i = 0; i < view.actions().size(); i++) if (view.actions().get(i).type() == RoomAction.Type.READY) ready = i;
            if (!game.actRoom(human, view.tableId(), view.incarnation(), view.decision(), ready))
                throw new IllegalStateException("Cannot ready fixture");
        }
        game.validate();
    }
}
