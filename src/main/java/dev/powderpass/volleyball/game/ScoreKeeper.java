package dev.powderpass.volleyball.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure rally-point scoring: points, sets, serve possession and rotations.
 */
public final class ScoreKeeper {

    /** What happened when a point was awarded. */
    public record Outcome(TeamId scorer, boolean sideOut, boolean setWon, boolean matchWon, boolean switchSides) {
    }

    /** Immutable copy of the state, used for referee undo. */
    public record Snapshot(int[] points, int[] sets, int setIndex, TeamId serving, int[] rotation,
                           TeamId firstServerOfSet, boolean decidingSwitchDone, List<int[]> history, TeamId winner) {
    }

    private final MatchSettings settings;
    private final int[] points = new int[2];
    private final int[] sets = new int[2];
    private final int[] rotation = new int[2];
    private final List<int[]> history = new ArrayList<>();
    private int setIndex;
    private TeamId serving;
    private TeamId firstServerOfSet;
    private boolean decidingSwitchDone;
    private TeamId winner;

    public ScoreKeeper(MatchSettings settings, TeamId firstServer) {
        this.settings = settings;
        this.serving = firstServer;
        this.firstServerOfSet = firstServer;
    }

    public Outcome awardPoint(TeamId team) {
        if (winner != null) {
            throw new IllegalStateException("match already finished");
        }
        boolean sideOut = team != serving;
        if (sideOut) {
            serving = team;
            rotation[team.index()]++;
        }
        points[team.index()]++;

        boolean switchSides = false;
        if (isDecidingSet() && !decidingSwitchDone && settings.flag(MatchSettings.Option.SWITCH_SIDES)
                && points[team.index()] >= (targetPoints() + 1) / 2) {
            decidingSwitchDone = true;
            switchSides = true;
        }

        boolean setWon = isSetWonBy(team);
        boolean matchWon = false;
        if (setWon) {
            history.add(new int[]{points[0], points[1]});
            sets[team.index()]++;
            if (sets[team.index()] >= settings.setsToWin()) {
                matchWon = true;
                winner = team;
            } else {
                setIndex++;
                points[0] = 0;
                points[1] = 0;
                firstServerOfSet = firstServerOfSet.other();
                serving = firstServerOfSet;
                switchSides = settings.flag(MatchSettings.Option.SWITCH_SIDES);
            }
        }
        return new Outcome(team, sideOut, setWon, matchWon, switchSides);
    }

    private boolean isSetWonBy(TeamId team) {
        int mine = points[team.index()];
        int theirs = points[team.other().index()];
        int cap = settings.pointCap();
        if (cap > 0 && mine >= cap) {
            return true;
        }
        return mine >= targetPoints() && mine - theirs >= settings.winBy();
    }

    public int targetPoints() {
        return isDecidingSet() ? settings.decidingSetPoints() : settings.pointsPerSet();
    }

    /** The last possible set (e.g. the 5th of a best of 5). Single-set matches use the normal set target. */
    public boolean isDecidingSet() {
        return settings.setsToWin() > 1 && setIndex == settings.setsToWin() * 2 - 2;
    }

    /** True if winning the next rally wins the set for {@code team}. */
    public boolean isSetPoint(TeamId team) {
        int mine = points[team.index()] + 1;
        int theirs = points[team.other().index()];
        int cap = settings.pointCap();
        return (cap > 0 && mine >= cap) || (mine >= targetPoints() && mine - theirs >= settings.winBy());
    }

    public boolean isMatchPoint(TeamId team) {
        return isSetPoint(team) && sets[team.index()] + 1 >= settings.setsToWin();
    }

    public int points(TeamId t) {
        return points[t.index()];
    }

    public int sets(TeamId t) {
        return sets[t.index()];
    }

    public int rotation(TeamId t) {
        return rotation[t.index()];
    }

    /** 1-based set number. */
    public int setNumber() {
        return setIndex + 1;
    }

    public TeamId serving() {
        return serving;
    }

    public TeamId winner() {
        return winner;
    }

    public List<int[]> history() {
        return history;
    }

    public Snapshot snapshot() {
        List<int[]> h = new ArrayList<>();
        for (int[] e : history) {
            h.add(e.clone());
        }
        return new Snapshot(points.clone(), sets.clone(), setIndex, serving, rotation.clone(), firstServerOfSet,
                decidingSwitchDone, h, winner);
    }

    public void restore(Snapshot s) {
        System.arraycopy(s.points(), 0, points, 0, 2);
        System.arraycopy(s.sets(), 0, sets, 0, 2);
        System.arraycopy(s.rotation(), 0, rotation, 0, 2);
        setIndex = s.setIndex();
        serving = s.serving();
        firstServerOfSet = s.firstServerOfSet();
        decidingSwitchDone = s.decidingSwitchDone();
        history.clear();
        for (int[] e : s.history()) {
            history.add(e.clone());
        }
        winner = s.winner();
    }
}
