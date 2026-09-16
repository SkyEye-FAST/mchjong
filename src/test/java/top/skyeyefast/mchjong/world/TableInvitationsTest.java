package top.skyeyefast.mchjong.world;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TableInvitationsTest {
    @Test void invitationIsBoundToItsRecipientAndExpiresAtTheExactDeadline() {
        UUID sender = UUID.randomUUID(), recipient = UUID.randomUUID();
        var invitation = new TableInvitations.Invitation(sender, recipient, UUID.randomUUID(),
            Level.OVERWORLD, BlockPos.ZERO, 1200);
        assertTrue(invitation.validFor(recipient, 1199));
        assertFalse(invitation.validFor(recipient, 1200));
        assertFalse(invitation.validFor(recipient, 1201));
        assertFalse(invitation.validFor(sender, 0));
        assertFalse(invitation.validFor(UUID.randomUUID(), 0));
        assertFalse(invitation.validFor(null, 0));
    }
}
