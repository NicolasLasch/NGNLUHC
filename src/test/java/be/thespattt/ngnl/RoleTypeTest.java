package be.thespattt.ngnl;

import be.thespattt.ngnl.player.faction.FactionType;
import be.thespattt.ngnl.role.RoleType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The role catalogue must match the design document: 29 roles, 8 duos, 13 solos, 7 factions.
 */
class RoleTypeTest {

    @Test
    void hasTwentyNineRoles() {
        assertEquals(29, RoleType.values().length);
    }

    @Test
    void duoPartnersAreSymmetricAndInTheRightFactionPairs() {
        long duoRoles = Arrays.stream(RoleType.values()).filter(RoleType::isDuo).count();
        assertEquals(16, duoRoles, "8 duos = 16 roles");
        for (RoleType role : RoleType.values()) {
            if (role.isDuo()) {
                RoleType partner = role.getPartnerRoleType();
                assertNotNull(partner, role + " has no partner");
                assertTrue(partner.isDuo(), role + " partner is not a duo role");
                assertSame(role, partner.getPartnerRoleType(), role + " and " + partner + " must point to each other");
            } else {
                assertNull(role.getPartnerRoleType(), role + " is solo but has a partner");
            }
        }
    }

    @Test
    void thirteenSolos() {
        assertEquals(13, Arrays.stream(RoleType.values()).filter(r -> !r.isDuo()).count());
    }

    @Test
    void factionsMatchTheDocument() {
        Map<FactionType, Integer> counts = new EnumMap<>(FactionType.class);
        for (RoleType role : RoleType.values()) {
            counts.merge(role.getFaction(), 1, Integer::sum);
        }
        assertEquals(7, counts.get(FactionType.IMANITY), "Sora, Shiro, Stephanie, Makoto, Riku, Corone, Einzig");
        assertEquals(2, counts.get(FactionType.FLUGEL));
        assertEquals(3, counts.get(FactionType.WEREBEASTS));
        assertEquals(3, counts.get(FactionType.EX_MACHINA));
        assertEquals(5, counts.get(FactionType.ELVES));
        assertEquals(5, counts.get(FactionType.OLD_DEUS), "Artosh, Okein, Kainas, Teto, Holou");
        assertEquals(4, counts.get(FactionType.OTHER));
    }

    @Test
    void godsAreTheFiveOldDeus() {
        for (RoleType god : new RoleType[]{RoleType.ARTOSH, RoleType.OKEIN, RoleType.KAINAS, RoleType.TETO, RoleType.HOLOU}) {
            assertEquals(FactionType.OLD_DEUS, god.getFaction());
        }
    }

    @Test
    void getByNameIsCaseInsensitive() {
        assertSame(RoleType.SORA, RoleType.getByName("sora"));
        assertNull(RoleType.getByName("nobody"));
    }
}
