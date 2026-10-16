package com.powderpass.backrooms.creature;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/** The awareness model: sight and sound out to 32 blocks, suspicion, alert, memory, search, forgetting. */
class AwarenessTest {
    private ServerMock server;
    private World w;
    private PlayerMock p;
    private int light = 12;
    private boolean los = true;
    private final Awareness.Senses senses = new Awareness.Senses() {
        public boolean lineOfSight(org.bukkit.entity.Player pl) {
            return los;
        }

        public int lightAt(org.bukkit.entity.Player pl) {
            return light;
        }
    };

    @BeforeEach
    void up() {
        server = MockBukkit.mock();
        w = server.addSimpleWorld("aw");
        p = server.addPlayer();
        p.teleport(new Location(w, 0.5, 65, 25.5));
    }

    @AfterEach
    void down() {
        MockBukkit.unmock();
    }

    private Location eye() {
        return new Location(w, 0.5, 66.7, 0.5);
    }

    /** Run the model for some ticks, the player wandering a little each step (or not). */
    private Awareness.Mode run(Awareness a, int ticks, boolean moving, double yaw) {
        for (int t = 0; t < ticks; t += 2) {
            if (moving) p.teleport(p.getLocation().add((t / 2) % 2 == 0 ? 0.4 : -0.4, 0, 0));
            a.update(w, eye(), yaw, 2, senses);
        }
        return a.mode();
    }

    @Test
    void aSprinterInTheLightIsSpottedAt25Blocks() {
        Awareness a = new Awareness(75, 1.0, 400);
        p.setSprinting(true);
        assertEquals(Awareness.Mode.ALERT, run(a, 200, true, 0), "in view, lit, running: it is sure");
        assertEquals(p.getUniqueId(), a.target());
    }

    @Test
    void sneakingInTheDarkOrBehindItGoesUnnoticed() {
        Awareness a = new Awareness(75, 1.0, 400);
        light = 1;
        p.setSneaking(true);
        assertEquals(Awareness.Mode.CALM, run(a, 200, true, 0), "sneaking in the dark at 25 blocks");
        Awareness b = new Awareness(75, 1.0, 400);
        light = 15;
        p.setSneaking(false);
        assertEquals(Awareness.Mode.CALM, run(b, 200, true, 180), "walking behind its back at 25 blocks");
        Awareness c = new Awareness(75, 1.0, 400);
        p.teleport(new Location(w, 0.5, 65, 45.5));
        p.setSprinting(true);
        assertEquals(Awareness.Mode.CALM, run(c, 200, true, 0), "past 32 blocks nothing is seen");
    }

    @Test
    void aNoiseMakesItSuspiciousThenItForgets() {
        Awareness a = new Awareness(75, 1.0, 100);
        los = false;
        Location bang = new Location(w, 8.5, 65, 14.5);
        a.hear(eye(), bang, 24, null);
        a.update(w, eye(), 0, 2, senses);
        assertEquals(Awareness.Mode.SUSPICIOUS, a.mode(), "it goes to look");
        assertEquals(bang, a.lastKnown());
        Location look = a.searchPoint();
        assertTrue(look.distance(bang) < 6, "searching near the noise");
        run(a, 140, false, 0);
        assertEquals(Awareness.Mode.CALM, a.mode(), "nothing more: it forgets");
        assertNull(a.lastKnown());
    }

    @Test
    void losingTheTargetLeavesAMemoryToSearch() {
        Awareness a = new Awareness(75, 1.0, 600);
        p.setSprinting(true);
        run(a, 200, true, 0);
        assertEquals(Awareness.Mode.ALERT, a.mode());
        Location seen = p.getLocation().clone();
        // gone behind a wall and still
        los = false;
        p.setSprinting(false);
        assertEquals(Awareness.Mode.SEARCHING, run(a, 120, false, 0), "it searches where they were");
        assertTrue(a.lastKnown().distance(seen) < 1.5, "remembering the last place it saw them");
    }

    @Test
    void aBlindHunterFeelsMovementButNotStillness() {
        Awareness a = new Awareness(180, 0.0, 400);
        p.teleport(new Location(w, 0.5, 65, 5.5));
        assertEquals(Awareness.Mode.CALM, run(a, 200, false, 0), "standing still right in front of it");
        assertEquals(Awareness.Mode.ALERT, run(a, 120, true, 0), "walking near it");
    }
}
