package top.skyeyefast.mchjong.smoke;

import java.util.UUID;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RoomSeating;

/** Fixed-seat fixtures for equipment, currency and replay checks; never included in a release jar. */
public final class SeatingFixtures {
    private SeatingFixtures() {}

    public static void startPositioned(Game game, UUID... humans) {
        var host = game.view(humans[0]);
        for (int i = 0; i < host.actions().size(); i++) if (host.actions().get(i).type() == Action.Type.FILL_BOTS)
            if (!game.act(humans[0], host.decision(), i)) throw new IllegalStateException("Cannot fill fixture");
        // Keep privileged fixture setup outside the engine's package: NeoForge uses distinct modules.
        try {
            var seating = new RoomSeating();
            var position = RoomSeating.class.getDeclaredMethod("positioned", int.class);
            position.setAccessible(true);
            position.invoke(seating, game.rules().players());
            var field = Game.class.getDeclaredField("seating");
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
            var view = game.view(human);
            int ready = -1;
            for (int i = 0; i < view.actions().size(); i++) if (view.actions().get(i).type() == Action.Type.READY) ready = i;
            if (!game.act(human, view.decision(), ready)) throw new IllegalStateException("Cannot ready fixture");
        }
        game.validate();
    }
}
