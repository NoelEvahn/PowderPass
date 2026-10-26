package dev.powderpass.volleyball.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LineupTest {

    private static List<UUID> ids(int n) {
        List<UUID> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(new UUID(0, i));
        }
        return l;
    }

    @Test
    void fiveOneSystemKeepsPairsOpposite() {
        List<UUID> p = ids(7);
        Map<UUID, Role> want = new HashMap<>();
        want.put(p.get(0), Role.MIDDLE);
        want.put(p.get(1), Role.OUTSIDE);
        want.put(p.get(2), Role.SETTER);
        want.put(p.get(3), Role.LIBERO);
        want.put(p.get(4), Role.OPPOSITE);
        Lineup l = Lineup.build(p, want);
        assertEquals(p.get(3), l.libero());
        assertEquals(6, l.order().size());
        assertFalse(l.order().contains(p.get(3)));
        assertEquals(p.get(2), l.order().get(0), "setter starts in position 1");
        assertEquals(p.get(4), l.order().get(3), "opposite is three places from the setter");
        assertEquals(Role.MIDDLE, l.roles().get(l.order().get(2)));
        assertEquals(Role.MIDDLE, l.roles().get(l.order().get(5)));
        assertEquals(Role.LIBERO, l.roles().get(p.get(3)));
    }

    @Test
    void duplicateRolesFallBackToFreeSlots() {
        List<UUID> p = ids(4);
        Map<UUID, Role> want = new HashMap<>();
        for (UUID id : p) {
            want.put(id, Role.SETTER);
        }
        Lineup l = Lineup.build(p, want);
        assertEquals(4, l.order().size());
        assertEquals(4, l.order().stream().distinct().count());
        assertEquals(Role.SETTER, l.roles().get(l.order().get(0)));
    }

    @Test
    void noLiberoInSmallTeams() {
        List<UUID> p = ids(3);
        Map<UUID, Role> want = Map.of(p.get(0), Role.LIBERO);
        Lineup l = Lineup.build(p, want);
        assertNull(l.libero());
        assertEquals(3, l.order().size());
    }
}
