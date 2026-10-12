package dev.powderpass.profile;

import dev.powderpass.course.Course;
import dev.powderpass.physics.SteeringMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {

    @Test
    void playerDataRoundTrip() throws Exception {
        UUID id = UUID.randomUUID();
        PlayerData d = new PlayerData(id, "Steve");
        d.topSpeed = 1.75;
        d.distance = 12345.5;
        d.bestTrickName = "Backflip 720 Japan Grab";
        d.bestTrickScore = 1650;
        d.badges.add(Badge.COURSE_RECORD);
        d.badges.add(Badge.YARD_SALE);
        d.suit = SuitPalette.GOLD_MEDAL;
        d.steering = SteeringMode.KEYS;
        d.mph = true;
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(d.toYaml().saveToString());
        PlayerData back = PlayerData.fromYaml(id, y);
        assertEquals("Steve", back.name());
        assertEquals(1.75, back.topSpeed, 1e-9);
        assertEquals(12345.5, back.distance, 1e-9);
        assertEquals("Backflip 720 Japan Grab", back.bestTrickName);
        assertTrue(back.badges.contains(Badge.COURSE_RECORD));
        assertEquals(SuitPalette.GOLD_MEDAL, back.suit);
        assertEquals(SteeringMode.KEYS, back.steering);
        assertTrue(back.mph);
    }

    @Test
    void lockedSuitFallsBackToClassic() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("name: Alex\nprefs:\n  suit: GOLD_MEDAL\nbadges: [UNKNOWN_FUTURE_BADGE]\n");
        PlayerData d = PlayerData.fromYaml(UUID.randomUUID(), y);
        assertEquals(SuitPalette.CLASSIC, d.suit, "cannot wear a suit without its badge");
        assertTrue(d.badges.isEmpty());
    }

    @Test
    void courseRoundTripKeepsGatesAndResults() throws Exception {
        Course c = new Course("Glacier_Run", "alps", new Course.GateDef(10.5, 120, -4.25, 90f, 6));
        c.addGate(new Course.GateDef(14, 115, 0, 85f, 5));
        c.addGate(new Course.GateDef(20, 110, 3, 95f, 5));
        c.finish(new Course.GateDef(40, 95, 0, 90f, 9));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        c.submit(a, "Alice", 812.25, 1L);
        c.submit(b, "Bob", 799.5, 2L);
        assertFalse(c.submit(a, "Alice", 900, 3L), "slower time is not a PB");
        c.recordSplits(new double[]{0, 100.5, 200.25, 799.5});

        YamlConfiguration y = new YamlConfiguration();
        c.save(y.createSection("courses.Glacier_Run"));
        YamlConfiguration reread = new YamlConfiguration();
        reread.loadFromString(y.saveToString());
        Course back = Course.load("Glacier_Run", reread.getConfigurationSection("courses.Glacier_Run"));
        assertNotNull(back);
        assertEquals("alps", back.world());
        assertEquals(2, back.slalomGates().size());
        assertNotNull(back.finish());
        assertEquals(4, back.lines().size());
        assertEquals(90f, back.start().yaw(), 1e-6);
        assertEquals("Bob", back.record().name());
        assertEquals(2, back.rank(a));
        assertEquals(812.25, back.best(a).ticks(), 1e-9);
        assertArrayEquals(new double[]{0, 100.5, 200.25, 799.5}, back.recordSplits(), 1e-9);
    }
}
