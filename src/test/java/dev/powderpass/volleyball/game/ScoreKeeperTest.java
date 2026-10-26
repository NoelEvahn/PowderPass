package dev.powderpass.volleyball.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScoreKeeperTest {

    @Test
    void sideOutRotatesReceivingTeam() {
        ScoreKeeper k = new ScoreKeeper(MatchSettings.standard(), TeamId.A);
        ScoreKeeper.Outcome o = k.awardPoint(TeamId.A);
        assertFalse(o.sideOut());
        assertEquals(0, k.rotation(TeamId.A));
        o = k.awardPoint(TeamId.B);
        assertTrue(o.sideOut());
        assertEquals(TeamId.B, k.serving());
        assertEquals(1, k.rotation(TeamId.B));
        assertEquals(0, k.rotation(TeamId.A));
    }

    @Test
    void setRequiresTwoPointLead() {
        ScoreKeeper k = new ScoreKeeper(MatchSettings.standard(), TeamId.A);
        for (int i = 0; i < 24; i++) {
            k.awardPoint(TeamId.A);
            k.awardPoint(TeamId.B);
        }
        assertEquals(24, k.points(TeamId.A));
        assertFalse(k.awardPoint(TeamId.A).setWon(), "25-24 is not enough");
        assertTrue(k.isSetPoint(TeamId.A));
        assertFalse(k.awardPoint(TeamId.B).setWon());
        assertFalse(k.awardPoint(TeamId.B).setWon());
        ScoreKeeper.Outcome o = k.awardPoint(TeamId.B);
        assertTrue(o.setWon(), "27-25 wins");
        assertTrue(o.switchSides());
        assertEquals(1, k.sets(TeamId.B));
        assertEquals(0, k.points(TeamId.A));
        assertEquals(2, k.setNumber());
        assertEquals(TeamId.B, k.serving(), "the other team serves first in set 2");
    }

    @Test
    void decidingSetTo15WithSwitchAt8() {
        MatchSettings s = MatchSettings.standard();
        ScoreKeeper k = new ScoreKeeper(s, TeamId.A);
        winSet(k, TeamId.A);
        winSet(k, TeamId.B);
        assertTrue(k.isDecidingSet());
        assertEquals(15, k.targetPoints());
        boolean switched = false;
        for (int i = 0; i < 8; i++) {
            switched |= k.awardPoint(TeamId.A).switchSides();
        }
        assertTrue(switched, "switch when a team reaches 8");
        for (int i = 0; i < 6; i++) {
            assertFalse(k.awardPoint(TeamId.A).matchWon());
        }
        assertTrue(k.isMatchPoint(TeamId.A));
        ScoreKeeper.Outcome o = k.awardPoint(TeamId.A);
        assertTrue(o.matchWon());
        assertEquals(TeamId.A, k.winner());
        assertThrows(IllegalStateException.class, () -> k.awardPoint(TeamId.B));
    }

    @Test
    void pointCapEndsSet() {
        MatchSettings s = MatchSettings.standard();
        s.set(MatchSettings.Option.SETS_TO_WIN, 1);
        s.set(MatchSettings.Option.POINTS_PER_SET, 15);
        s.set(MatchSettings.Option.POINT_CAP, 17);
        ScoreKeeper k = new ScoreKeeper(s, TeamId.A);
        for (int i = 0; i < 16; i++) {
            k.awardPoint(TeamId.A);
            k.awardPoint(TeamId.B);
        }
        assertTrue(k.awardPoint(TeamId.B).matchWon(), "17-16 wins with a cap of 17");
    }

    @Test
    void snapshotRestore() {
        ScoreKeeper k = new ScoreKeeper(MatchSettings.standard(), TeamId.A);
        k.awardPoint(TeamId.A);
        ScoreKeeper.Snapshot snap = k.snapshot();
        k.awardPoint(TeamId.B);
        k.awardPoint(TeamId.B);
        k.restore(snap);
        assertEquals(1, k.points(TeamId.A));
        assertEquals(0, k.points(TeamId.B));
        assertEquals(TeamId.A, k.serving());
        assertEquals(0, k.rotation(TeamId.B));
    }

    private static void winSet(ScoreKeeper k, TeamId t) {
        int set = k.setNumber();
        while (k.setNumber() == set) {
            k.awardPoint(t);
        }
    }
}
