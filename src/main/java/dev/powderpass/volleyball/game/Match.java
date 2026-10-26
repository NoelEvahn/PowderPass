package dev.powderpass.volleyball.game;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.ball.BallContext;
import dev.powderpass.volleyball.ball.Volleyball;
import dev.powderpass.volleyball.config.Settings;
import dev.powderpass.volleyball.court.Court;
import dev.powderpass.volleyball.court.NetRenderer;
import dev.powderpass.volleyball.hit.HitResult;
import dev.powderpass.volleyball.hit.HitType;
import dev.powderpass.volleyball.items.ItemType;
import dev.powderpass.volleyball.math.CourtFrame;
import dev.powderpass.volleyball.math.Vec3;
import dev.powderpass.volleyball.physics.Aabb;
import dev.powderpass.volleyball.physics.NetContact;
import dev.powderpass.volleyball.util.Msg;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A running match on a court: state machine, rule enforcement and scoring.
 */
public final class Match implements BallContext {

    private final PowderPassPlugin plugin;
    private final Court court;
    private final MatchSettings settings;
    private final Team[] teams = new Team[2];
    private final ScoreKeeper score;
    private final UUID host;
    private UUID referee;
    private final Deque<ScoreKeeper.Snapshot> undo = new ArrayDeque<>();

    private MatchState state = MatchState.COUNTDOWN;
    private int stateTicks;
    private int stateDuration;
    private Volleyball ball;

    // rally tracking
    private TeamId lastTouchTeam;
    private UUID lastTouchPlayer;
    private boolean lastTouchBlock;
    private int teamContacts;
    private boolean serveInFlight;
    private boolean serveCrossed;
    private boolean serveAuthorized;
    private boolean served;
    private UUID server;
    private int rallyTouches;
    private String lastPointText = "";
    private TeamId lastPointTeam;
    private boolean ended;

    public Match(PowderPassPlugin plugin, Court court, MatchSettings settings, Lineup teamA, Lineup teamB,
                 UUID host) {
        this.plugin = plugin;
        this.court = court;
        this.settings = settings;
        this.host = host;
        Settings s = plugin.settings();
        teams[0] = new Team(TeamId.A, s.teamA, -1);
        teams[1] = new Team(TeamId.B, s.teamB, 1);
        teams[0].applyLineup(teamA);
        teams[1].applyLineup(teamB);
        TeamId first = ThreadLocalRandom.current().nextBoolean() ? TeamId.A : TeamId.B;
        this.score = new ScoreKeeper(settings, first);
    }

    // ------------------------------------------------------------------------------------------- accessors

    public Court court() {
        return court;
    }

    public MatchSettings settings() {
        return settings;
    }

    public ScoreKeeper score() {
        return score;
    }

    public Team team(TeamId id) {
        return teams[id.index()];
    }

    public Team[] teams() {
        return teams;
    }

    public MatchState state() {
        return state;
    }

    public UUID host() {
        return host;
    }

    public UUID referee() {
        return referee;
    }

    public void setReferee(UUID referee) {
        this.referee = referee;
    }

    public UUID server() {
        return server;
    }

    public int teamContacts() {
        return teamContacts;
    }

    public TeamId lastTouchTeam() {
        return lastTouchTeam;
    }

    public int rallyTouches() {
        return rallyTouches;
    }

    public String lastPointText() {
        return lastPointText;
    }

    public TeamId lastPointTeam() {
        return lastPointTeam;
    }

    public boolean ended() {
        return ended;
    }

    /** Seconds left on the current clock (serve clock, timeout, countdown). */
    public double secondsLeft() {
        return Math.max(0, (stateDuration - stateTicks) / 20.0);
    }

    public double clockProgress() {
        return stateDuration <= 0 ? 0 : Math.max(0, Math.min(1, 1 - (double) stateTicks / stateDuration));
    }

    public Team teamOf(UUID player) {
        for (Team t : teams) {
            if (t.contains(player)) {
                return t;
            }
        }
        return null;
    }

    public boolean contains(UUID player) {
        return teamOf(player) != null;
    }

    public List<Player> players() {
        List<Player> list = new ArrayList<>(teams[0].online());
        list.addAll(teams[1].online());
        return list;
    }

    /** Players plus spectators near the court. */
    public Audience audience() {
        List<Audience> list = new ArrayList<>(players());
        if (court.world() != null) {
            for (Player p : court.world().getPlayers()) {
                if (!contains(p.getUniqueId()) && court.inArea(p.getLocation(), 12)) {
                    list.add(p);
                }
            }
        }
        return Audience.audience(list);
    }

    public int teamSize() {
        return Math.max(teams[0].size(), teams[1].size());
    }

    public Role roleOf(UUID player) {
        Team t = teamOf(player);
        return t == null ? Role.ANY : t.role(player);
    }

    public boolean onCourt(UUID player) {
        Team t = teamOf(player);
        return t != null && t.onCourt(player);
    }

    public int positionOf(UUID player) {
        Team t = teamOf(player);
        return t == null ? -1 : t.positionOf(player, score.rotation(t.id()));
    }

    // ------------------------------------------------------------------------------------------- lifecycle

    public void start() {
        for (Player p : players()) {
            prepare(p);
        }
        positionPlayers();
        enter(MatchState.COUNTDOWN, 5 * 20);
        Msg.send(audience(), "<white>Match on <yellow><court></yellow> · <gray><format></gray>",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("court", court.name()),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("format", settings.describe()));
        Msg.send(audience(), team(score.serving()).displayName()
                .append(Component.text(" won the coin toss and serves first.", NamedTextColor.GRAY)));
    }

    private void prepare(Player p) {
        plugin.snapshots().save(p);
        PlayerInventory inv = p.getInventory();
        inv.clear();
        p.setGameMode(GameMode.ADVENTURE);
        var maxHealth = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        p.setHealth(maxHealth == null ? 20 : maxHealth.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setFireTicks(0);
        p.setAllowFlight(false);
        p.setFlying(false);
        giveKit(p, false);
        p.sendMessage(Msg.mm("<gray>Controls: <yellow>left-click</yellow> bump/spike · <yellow>right-click</yellow> set/block · "
                + "<yellow>sneak</yellow> variants · look where you aim. <white>/vb guide</white> for details."));
    }

    /** Fills the hotbar so every slot works: Minecraft sends no right-click packet for an empty hand. */
    private void giveKit(Player p, boolean serving) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, plugin.items().create(ItemType.HANDS));
        }
        if (serving) {
            inv.setItem(0, plugin.items().create(ItemType.VOLLEYBALL));
        }
        if (p.getUniqueId().equals(referee) || p.getUniqueId().equals(host)) {
            inv.setItem(8, plugin.items().create(ItemType.WHISTLE));
        }
        Team t = teamOf(p.getUniqueId());
        if (t != null) {
            boolean libero = t.isLibero(p.getUniqueId());
            inv.setArmorContents(plugin.items().jersey(libero ? plugin.settings().liberoColor : t.color()));
        }
        inv.setHeldItemSlot(0);
    }

    /** Ends the match and restores players. */
    public void end(String reason) {
        if (ended) {
            return;
        }
        ended = true;
        removeBall();
        for (Player p : players()) {
            release(p);
        }
        if (reason != null) {
            Msg.send(audience(), "<gray>" + Msg.escape(reason));
        }
    }

    private void release(Player p) {
        plugin.hud().detach(p);
        p.getInventory().clear();
        if (!plugin.snapshots().restore(p, false)) {
            p.setGameMode(Bukkit.getDefaultGameMode());
        }
    }

    /** A player left the server or used /vb leave. */
    public void removePlayer(Player p, boolean quit) {
        Team t = teamOf(p.getUniqueId());
        if (t == null) {
            return;
        }
        if (t.isLibero(p.getUniqueId())) {
            t.clearLibero();
        } else {
            t.order().remove(p.getUniqueId());
            if (p.getUniqueId().equals(t.benched())) {
                t.setBenched(null);
            }
        }
        if (!quit) {
            release(p);
        } else {
            plugin.hud().detach(p);
        }
        Msg.send(audience(), "<yellow>" + Msg.escape(p.getName()) + "</yellow> <gray>left the match.");
        if (t.order().isEmpty()) {
            Team other = team(t.id().other());
            announceWinner(other, "by forfeit");
            finish();
            return;
        }
        if (p.getUniqueId().equals(server) && (state == MatchState.SERVE || state == MatchState.PRE_SERVE)) {
            enter(MatchState.PRE_SERVE, 30);
            setupServe();
        }
    }

    // ------------------------------------------------------------------------------------------- state machine

    private void enter(MatchState s, int duration) {
        state = s;
        stateTicks = 0;
        stateDuration = duration;
    }

    public void tick() {
        if (ended) {
            return;
        }
        stateTicks++;
        switch (state) {
            case COUNTDOWN -> {
                int left = stateDuration - stateTicks;
                if (left > 0 && left % 20 == 0) {
                    int sec = left / 20;
                    for (Player p : players()) {
                        plugin.fx().sound(p, Settings.SoundId.COUNTDOWN);
                    }
                    audience().showTitle(Title.title(Component.text(sec, NamedTextColor.YELLOW, TextDecoration.BOLD),
                            Component.text(court.name() + " · " + teams[0].name() + " vs " + teams[1].name(), NamedTextColor.GRAY),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                }
                if (left <= 0) {
                    for (Player p : players()) {
                        plugin.fx().sound(p, Settings.SoundId.GO);
                    }
                    beginRallySetup();
                }
            }
            case PRE_SERVE -> {
                if (stateTicks >= stateDuration && (!settings.flag(MatchSettings.Option.REFEREE_WHISTLE) || serveAuthorized)) {
                    whistle();
                    enter(MatchState.SERVE, settings.get(MatchSettings.Option.SERVE_TIME) * 20);
                }
            }
            case SERVE -> {
                Player sp = server == null ? null : Bukkit.getPlayer(server);
                if (sp == null) {
                    enter(MatchState.PRE_SERVE, 20);
                    setupServe();
                    return;
                }
                if (!served && stateTicks >= stateDuration && ball == null) {
                    if (settings.rule(Rule.SERVE_DELAY)) {
                        fault(team(score.serving()).id(), Fault.SERVE_DELAY, sp);
                    } else {
                        stateTicks = 0;
                    }
                }
            }
            case RALLY -> {
                if (ball == null || ball.removed()) {
                    // the ball left the world (fell into the void, chunk unloaded...)
                    ball = null;
                    faultAgainstLastTouch(Fault.OBJECT);
                } else if (stateTicks > 90 * 20) {
                    Msg.send(audience(), "<yellow>Rally took too long, replaying it.");
                    beginRallySetup();
                } else {
                    checkPlayers();
                }
            }
            case POINT_END -> {
                if (stateTicks >= stateDuration) {
                    beginRallySetup();
                }
            }
            case TIMEOUT -> {
                if (stateTicks >= stateDuration) {
                    Msg.send(audience(), "<gray>Timeout over.");
                    beginRallySetup();
                }
            }
            case PAUSED, FINISHED -> {
                if (state == MatchState.FINISHED && stateTicks >= stateDuration) {
                    end(null);
                }
            }
        }
    }

    private void beginRallySetup() {
        removeBall();
        resetRally();
        positionPlayers();
        setupServe();
        enter(MatchState.PRE_SERVE, 30);
    }

    private void resetRally() {
        lastTouchTeam = null;
        lastTouchPlayer = null;
        lastTouchBlock = false;
        teamContacts = 0;
        serveInFlight = false;
        serveCrossed = false;
        serveAuthorized = false;
        served = false;
        rallyTouches = 0;
    }

    private void setupServe() {
        Team st = team(score.serving());
        server = st.atPosition(1, score.rotation(st.id()));
        // only the old and the new server need a different hotbar
        for (Player p : players()) {
            boolean isServer = p.getUniqueId().equals(server);
            boolean holdsBall = dev.powderpass.volleyball.items.CustomItems.is(p.getInventory().getItem(0), ItemType.VOLLEYBALL);
            if (isServer != holdsBall) {
                giveKit(p, isServer);
            }
        }
        Player sp = server == null ? null : Bukkit.getPlayer(server);
        if (sp != null) {
            sp.showTitle(Title.title(Component.empty(), Component.text("Your serve!", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
            plugin.fx().sound(sp, Settings.SoundId.POINT);
        }
    }

    /** Decides who the libero replaces this rally: a back-row middle blocker if possible (never the server). */
    private void assignLibero(Team t) {
        t.setBenched(null);
        if (t.libero() == null || Bukkit.getPlayer(t.libero()) == null) {
            return;
        }
        int rot = score.rotation(t.id());
        int n = t.size();
        UUID best = null;
        int bestScore = -1;
        for (int pos = 1; pos <= n; pos++) {
            if (!Positions.isBackRow(n, pos) || (pos == 1 && t.id() == score.serving())) {
                continue;
            }
            UUID id = t.atPosition(pos, rot);
            if (id == null || Bukkit.getPlayer(id) == null) {
                continue;
            }
            int sc = (t.role(id) == Role.MIDDLE ? 100 : 0) + pos;
            if (sc > bestScore) {
                bestScore = sc;
                best = id;
            }
        }
        // only swap in for a middle; teams without one use the libero for the deepest back-row spot
        boolean hasMiddle = t.order().stream().anyMatch(id -> t.role(id) == Role.MIDDLE);
        if (best != null && (!hasMiddle || t.role(best) == Role.MIDDLE)) {
            t.setBenched(best);
        }
    }

    /** Bench spot along the side line, away from the referee stand. */
    private Location benchSpot(Team t, int index) {
        double u = t.side() * (Math.min(court.halfLength() - 1, 2.0 + index * 1.0));
        double w = -(court.halfWidth() + Math.min(court.freeZone(), 3.0) * 0.7);
        Location l = court.location(u, 0.01, w);
        l.setYaw(court.frame().yawOf(0, 1));
        return l;
    }

    private void positionPlayers() {
        for (Team t : teams) {
            assignLibero(t);
        }
        boolean auto = settings.flag(MatchSettings.Option.AUTO_POSITION);
        for (Team t : teams) {
            int rot = score.rotation(t.id());
            int n = t.size();
            int benchIndex = 0;
            for (Player p : t.online()) {
                if (!t.onCourt(p.getUniqueId())) {
                    p.teleport(benchSpot(t, benchIndex++));
                }
            }
            if (!auto) {
                continue;
            }
            for (int pos = 1; pos <= n; pos++) {
                UUID id = t.atPosition(pos, rot);
                if (id == null) {
                    continue;
                }
                if (id.equals(t.benched())) {
                    id = t.libero();
                }
                Player p = Bukkit.getPlayer(id);
                if (p == null) {
                    continue;
                }
                Location target;
                if (pos == 1 && t.id() == score.serving()) {
                    target = court.facingNet(t.side() * (court.halfLength() + 1.2), -t.side() * court.halfWidth() * 0.6);
                } else {
                    double[] spot = Positions.spot(n, pos);
                    target = court.facingNet(t.side() * spot[0] * court.halfLength(),
                            -t.side() * spot[1] * (court.halfWidth() - 0.6));
                }
                target.add(0, 0.01, 0);
                p.teleport(target);
                p.setVelocity(new org.bukkit.util.Vector());
            }
        }
    }

    /**
     * 6v6 positional fault at the moment of the serve: front-row players must be nearer the net than their
     * back-row counterparts, and each row must keep its left-to-right order. The server is exempt.
     *
     * @return the offending positions, or null when everyone is in order
     */
    private String rotationFault(Team t) {
        if (t.size() != 6) {
            return null;
        }
        int rot = score.rotation(t.id());
        double[] depth = new double[7];
        double[] lateral = new double[7];
        boolean[] known = new boolean[7];
        CourtFrame f = court.frame();
        for (int pos = 1; pos <= 6; pos++) {
            UUID id = t.atPosition(pos, rot);
            if (id != null && id.equals(t.benched())) {
                id = t.libero();
            }
            Player p = id == null ? null : Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            Location l = p.getLocation();
            depth[pos] = Math.abs(f.toU(l.getX(), l.getZ()));
            lateral[pos] = -t.side() * f.toW(l.getX(), l.getZ());
            known[pos] = true;
        }
        boolean serving = t.id() == score.serving();
        int[][] frontBack = {{4, 5}, {3, 6}, {2, 1}};
        for (int[] fb : frontBack) {
            if (fb[1] == 1 && serving) {
                continue;
            }
            if (known[fb[0]] && known[fb[1]] && depth[fb[0]] >= depth[fb[1]]) {
                return "P" + fb[0] + "/P" + fb[1];
            }
        }
        int[][] rows = {{4, 3, 2}, {5, 6, 1}};
        for (int[] row : rows) {
            for (int i = 0; i + 1 < row.length; i++) {
                int left = row[i];
                int right = row[i + 1];
                if (right == 1 && serving) {
                    continue;
                }
                if (known[left] && known[right] && lateral[left] >= lateral[right]) {
                    return "P" + left + "/P" + right;
                }
            }
        }
        return null;
    }

    private Location refereeSpot() {
        return court.location(0, court.netHeight() + 1, court.halfWidth() + 1);
    }

    private void whistle() {
        plugin.fx().whistle(refereeSpot(), false);
    }

    private void longWhistle() {
        plugin.fx().whistle(refereeSpot(), true);
    }

    /** Crowd reaction around the court. */
    private void crowd(Settings.SoundId id, float volume) {
        plugin.fx().sound(court.location(0, 2, 0), id, volume);
    }

    // ------------------------------------------------------------------------------------------- serving

    /** Right click with the ball item. */
    public void requestToss(Player p) {
        if (state != MatchState.SERVE || !p.getUniqueId().equals(server) || ball != null) {
            if (state == MatchState.PRE_SERVE && p.getUniqueId().equals(server)) {
                p.sendActionBar(Component.text("Wait for the whistle", NamedTextColor.YELLOW));
            }
            return;
        }
        Vec3[] st = plugin.hits().tossState(p);
        ball = plugin.balls().spawn(p.getWorld(), st[0], this);
        plugin.hits().prepareToss(p, ball, st[1]);
        takeBallItem(p);
    }

    /** Left click with the ball item and no toss: underhand serve. */
    public void requestUnderhand(Player p) {
        if (state != MatchState.SERVE || !p.getUniqueId().equals(server) || ball != null) {
            return;
        }
        ball = plugin.balls().spawn(p.getWorld(), plugin.hits().underhandPosition(p), this);
        ball.markToss(p.getUniqueId());
        takeBallItem(p);
        plugin.hits().underhand(p, ball);
    }

    private void takeBallItem(Player p) {
        giveKit(p, false);
    }

    /** Referee whistle. */
    public void refereeWhistle(Player p) {
        if (state == MatchState.PRE_SERVE) {
            serveAuthorized = true;
            stateTicks = Math.max(stateTicks, stateDuration);
        }
    }

    // ------------------------------------------------------------------------------------------- BallContext

    @Override
    public Court court(Volleyball b) {
        return court;
    }

    @Override
    public boolean canTouch(Player player, Volleyball b) {
        if (b != ball || ended) {
            return false;
        }
        Team t = teamOf(player.getUniqueId());
        if (t == null || !t.onCourt(player.getUniqueId())) {
            return false;
        }
        UUID tossedBy = b.tossedBy();
        if (tossedBy != null) {
            return tossedBy.equals(player.getUniqueId()) && state == MatchState.SERVE;
        }
        return state == MatchState.RALLY;
    }

    @Override
    public void onHit(Player player, Volleyball b, HitResult r) {
        if (b != ball) {
            return;
        }
        Team t = teamOf(player.getUniqueId());
        if (t == null) {
            return;
        }
        rallyTouches++;
        CourtFrame f = court.frame();
        if (r.type().serve()) {
            onServe(player, t, r);
            return;
        }
        if (state != MatchState.RALLY) {
            return;
        }
        double contactU = f.toU(r.contact().x, r.contact().z);
        double contactY = f.toLocalY(r.contact().y);
        boolean receiving = t.id() != score.serving();

        if (serveInFlight) {
            if (!receiving && !serveCrossed) {
                if (settings.rule(Rule.SERVING_TEAM_TOUCH)) {
                    fault(t.id(), Fault.SERVING_TEAM_TOUCH, player);
                    return;
                }
            } else if (receiving) {
                boolean aboveNet = contactY > court.netHeight() && Math.abs(contactU) < 2.0;
                if ((r.type() == HitType.BLOCK || (r.type().attack() && aboveNet)) && settings.rule(Rule.SERVE_BLOCK)) {
                    fault(t.id(), Fault.SERVE_BLOCK, player);
                    return;
                }
                serveInFlight = false;
            }
        }

        int position = t.positionOf(player.getUniqueId(), score.rotation(t.id()));
        boolean backRow = !Positions.isFrontRow(t.size(), position);
        if (t.isLibero(player.getUniqueId()) && settings.rule(Rule.LIBERO)) {
            if (r.type() == HitType.BLOCK) {
                fault(t.id(), Fault.LIBERO_BLOCK, player);
                return;
            }
            if (r.type().attack() && contactY > court.netHeight()) {
                fault(t.id(), Fault.LIBERO_ATTACK, player);
                return;
            }
        }
        if (r.type() == HitType.BLOCK) {
            if (backRow && settings.rule(Rule.BACK_ROW_ATTACK)) {
                fault(t.id(), Fault.BACK_ROW_BLOCK, player);
                return;
            }
            lastTouchTeam = t.id();
            lastTouchPlayer = player.getUniqueId();
            lastTouchBlock = true;
            teamContacts = 0;
            return;
        }

        if (lastTouchTeam == t.id()) {
            // a one-player team has nobody to pass to: consecutive touches are allowed
            if (player.getUniqueId().equals(lastTouchPlayer) && !lastTouchBlock && settings.rule(Rule.DOUBLE_CONTACT)
                    && t.size() > 1) {
                fault(t.id(), Fault.DOUBLE_CONTACT, player);
                return;
            }
            teamContacts++;
        } else {
            teamContacts = 1;
        }
        lastTouchTeam = t.id();
        lastTouchPlayer = player.getUniqueId();
        lastTouchBlock = false;

        if (teamContacts > 3 && settings.rule(Rule.FOUR_HITS)) {
            fault(t.id(), Fault.FOUR_HITS, player);
            return;
        }
        if (contactU * t.side() < -0.25 && settings.rule(Rule.REACH_OVER)) {
            fault(t.id(), Fault.REACH_OVER, player);
            return;
        }
        if (r.type().attack() && backRow && settings.rule(Rule.BACK_ROW_ATTACK) && contactY > court.netHeight()) {
            double takeoffU = f.toU(r.feet().x, r.feet().z);
            if (Math.abs(takeoffU) < court.attackLine()) {
                fault(t.id(), Fault.BACK_ROW_ATTACK, player);
            }
        }
    }

    private void onServe(Player player, Team t, HitResult r) {
        if (state != MatchState.SERVE || !player.getUniqueId().equals(server)) {
            return;
        }
        served = true;
        enter(MatchState.RALLY, 0);
        serveInFlight = true;
        serveCrossed = false;
        lastTouchTeam = t.id();
        lastTouchPlayer = player.getUniqueId();
        lastTouchBlock = false;
        teamContacts = 1;
        if (settings.rule(Rule.FOOT_FAULT)) {
            CourtFrame f = court.frame();
            double u = f.toU(r.feet().x, r.feet().z);
            double w = f.toW(r.feet().x, r.feet().z);
            double foot = 0.15;
            boolean onLine = Math.abs(u) - foot < court.halfLength();
            boolean wrongSide = u * t.side() < 0;
            boolean outsideZone = Math.abs(w) > court.halfWidth() + 0.05;
            if (onLine || wrongSide || outsideZone) {
                fault(t.id(), Fault.FOOT_FAULT, player);
                return;
            }
        }
        if (settings.rule(Rule.ROTATION_ORDER)) {
            for (Team team : teams) {
                String bad = rotationFault(team);
                if (bad != null) {
                    lastPointText = "";
                    Msg.send(audience(), team.displayName().append(Component.text(" out of rotation order (" + bad + ")",
                            NamedTextColor.GRAY)));
                    fault(team.id(), Fault.POSITIONAL, null);
                    return;
                }
            }
        }
    }

    @Override
    public void onBlockContact(Volleyball b, Vec3 position, Vec3 normal, double impact, Aabb box) {
        if (b != ball || ended) {
            return;
        }
        if (state == MatchState.SERVE) {
            if (normal.y > 0.7 && impact > 0.3 && !served) {
                Player sp = server == null ? null : Bukkit.getPlayer(server);
                if (settings.rule(Rule.TOSS_DROP)) {
                    fault(score.serving(), Fault.TOSS_DROP, sp);
                } else {
                    removeBall();
                    if (sp != null) {
                        giveKit(sp, true);
                    }
                }
            }
            return;
        }
        if (state != MatchState.RALLY) {
            return;
        }
        CourtFrame f = court.frame();
        double localY = f.toLocalY(position.y);
        boolean floorLevel = normal.y > 0.7 && Math.abs(localY) < 0.6;
        if (floorLevel) {
            double u = f.toU(position.x, position.z);
            double w = f.toW(position.x, position.z);
            double r = plugin.settings().physics.radius();
            if (court.isIn(u, w, r)) {
                TeamId sideTeam = u < 0 ? teamOnSide(-1) : teamOnSide(1);
                pointMarker(position, true);
                awardPoint(sideTeam.other(), Fault.BALL_IN, null);
            } else {
                pointMarker(position, false);
                faultAgainstLastTouch(Fault.BALL_OUT);
            }
        } else if (settings.rule(Rule.CEILING_OUT) && impact > 0.05) {
            faultAgainstLastTouch(Fault.OBJECT);
        }
    }

    private void pointMarker(Vec3 p, boolean in) {
        plugin.fx().ring(court.world(), p.x, p.y, p.z, 0.32, in ? Color.fromRGB(80, 255, 80) : Color.fromRGB(255, 60, 60), 16);
    }

    @Override
    public void onNetContact(Volleyball b, NetContact contact, Vec3 localPoint, double impact) {
        if (b != ball || state != MatchState.RALLY) {
            return;
        }
        if (contact == NetContact.TAPE && impact > 1.5) {
            crowd(Settings.SoundId.OOH, 0.9f);
        }
        if (contact == NetContact.ANTENNA && settings.rule(Rule.ANTENNA)) {
            faultAgainstLastTouch(Fault.ANTENNA);
        } else if (contact == NetContact.POST && settings.rule(Rule.CEILING_OUT)) {
            faultAgainstLastTouch(Fault.OBJECT);
        }
    }

    @Override
    public void onNetCross(Volleyball b, int fromSide, double localW, double localY) {
        if (b != ball || state != MatchState.RALLY) {
            return;
        }
        double r = plugin.settings().physics.radius();
        if (localY < court.netHeight() - court.netBand()) {
            faultAgainstLastTouch(Fault.UNDER_NET);
            return;
        }
        if (Math.abs(localW) > court.halfWidth() + r && settings.rule(Rule.ANTENNA)) {
            faultAgainstLastTouch(Fault.OUTSIDE_ANTENNA);
            return;
        }
        if (serveInFlight) {
            serveCrossed = true;
        }
    }

    @Override
    public void onTick(Volleyball b) {
        if (b == ball && state == MatchState.RALLY && b.body().resting) {
            // a ball that came to rest without a floor call (e.g. stuck on an object)
            faultAgainstLastTouch(Fault.OBJECT);
        }
    }

    // ------------------------------------------------------------------------------------------- player checks

    private void checkPlayers() {
        CourtFrame f = court.frame();
        NetRenderer net = plugin.courts().netRenderer(court);
        for (Team t : teams) {
            for (Player p : t.onCourt()) {
                Location l = p.getLocation();
                double u = f.toU(l.getX(), l.getZ());
                double w = f.toW(l.getX(), l.getZ());
                double y = l.getY() - court.centerY();
                double top = y + p.getHeight();
                boolean betweenAntennas = Math.abs(w) <= court.halfWidth();
                boolean touchesNet = Math.abs(u) < 0.32 && top > court.netHeight() - court.netBand() - 0.1
                        && y < court.netHeight() && Math.abs(w) <= court.halfWidth() + court.postOffset();
                if (touchesNet) {
                    if (net != null) {
                        net.brush(w, Math.min(court.netHeight() - 0.1, Math.max(y + 1.2, court.netHeight() - court.netBand())),
                                -Math.signum(u) * 1.5);
                    }
                    if (betweenAntennas && settings.rule(Rule.NET_TOUCH)) {
                        fault(t.id(), Fault.NET_TOUCH, p);
                        return;
                    }
                }
                if (settings.rule(Rule.CENTER_LINE) && p.isOnGround() && u * t.side() < -0.35 && betweenAntennas
                        && Math.abs(u) < court.halfLength()) {
                    fault(t.id(), Fault.CENTER_LINE, p);
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------- scoring

    private TeamId teamOnSide(int side) {
        return teams[0].side() == side ? TeamId.A : TeamId.B;
    }

    private void faultAgainstLastTouch(Fault f) {
        TeamId against = lastTouchTeam != null ? lastTouchTeam : score.serving();
        Player who = lastTouchPlayer == null ? null : Bukkit.getPlayer(lastTouchPlayer);
        fault(against, f, who);
    }

    /** {@code against} committed a fault: the other team scores. */
    public void fault(TeamId against, Fault f, Player culprit) {
        awardPoint(against.other(), f, culprit);
    }

    public void awardPoint(TeamId scorer, Fault reason, Player culprit) {
        if (state == MatchState.POINT_END || state == MatchState.FINISHED || ended) {
            return;
        }
        undo.push(score.snapshot());
        while (undo.size() > 20) {
            undo.removeLast();
        }
        ScoreKeeper.Outcome o = score.awardPoint(scorer);
        if (o.setWon()) {
            longWhistle();
        } else {
            whistle();
        }
        // the crowd cheers a won rally, louder for long ones
        crowd(Settings.SoundId.CHEER, rallyTouches >= 4 ? 1.2f : 0.8f);
        Team winner = team(scorer);
        String detail = reason.text() + (culprit != null && reason != Fault.BALL_IN ? " · " + culprit.getName() : "");
        lastPointText = detail;
        lastPointTeam = scorer;

        Component title = Component.text("POINT ", NamedTextColor.WHITE, TextDecoration.BOLD)
                .append(Component.text(winner.name().toUpperCase(), winner.color(), TextDecoration.BOLD));
        Component sub = Component.text(score.points(TeamId.A) + " - " + score.points(TeamId.B), NamedTextColor.WHITE)
                .append(Component.text("  " + detail, NamedTextColor.GRAY));
        audience().showTitle(Title.title(title, sub, Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1600),
                Duration.ofMillis(400))));
        audience().sendMessage(Msg.mm("<dark_gray>»</dark_gray> ").append(winner.displayName())
                .append(Component.text(" +1 ", NamedTextColor.WHITE))
                .append(Component.text("(" + detail + ")", NamedTextColor.GRAY)));
        for (Player p : winner.online()) {
            plugin.fx().sound(p, Settings.SoundId.POINT);
        }

        if (o.matchWon()) {
            announceWinner(winner, score.sets(TeamId.A) + " - " + score.sets(TeamId.B));
            finish();
            return;
        }
        if (o.setWon()) {
            crowd(Settings.SoundId.APPLAUSE, 1.2f);
            int[] last = score.history().get(score.history().size() - 1);
            Msg.send(audience(), winner.displayName().append(Component.text(" wins set " + score.history().size()
                    + " (" + last[0] + "-" + last[1] + ")", NamedTextColor.WHITE)));
            for (Team t : teams) {
                t.resetTimeouts();
            }
        } else {
            if (score.isMatchPoint(scorer)) {
                Msg.send(audience(), "<gold><b>Match point</b>");
            } else if (score.isSetPoint(scorer)) {
                Msg.send(audience(), "<yellow><b>Set point</b>");
            }
        }
        if (o.switchSides()) {
            teams[0].switchSide();
            teams[1].switchSide();
            Msg.send(audience(), "<aqua>Teams switch sides.");
        }
        if (o.sideOut()) {
            Player newServer = Bukkit.getPlayer(team(scorer).atPosition(1, score.rotation(scorer)));
            if (newServer != null && team(scorer).size() > 1) {
                newServer.sendActionBar(Component.text("Side-out: your team rotates, you serve", NamedTextColor.GOLD));
            }
        }
        int delay = settings.get(MatchSettings.Option.RALLY_END_DELAY) * 20 + (o.setWon() ? 60 : 0);
        enter(MatchState.POINT_END, delay);
    }

    private void announceWinner(Team winner, String detail) {
        Settings.SoundId win = Settings.SoundId.WIN;
        audience().showTitle(Title.title(winner.displayName().append(Component.text(" WINS!", NamedTextColor.WHITE,
                TextDecoration.BOLD)), Component.text(detail, NamedTextColor.GRAY)));
        for (Player p : players()) {
            plugin.fx().sound(p, win);
        }
        longWhistle();
        crowd(Settings.SoundId.WIN, 1.5f);
        StringBuilder sets = new StringBuilder();
        for (int[] h : score.history()) {
            if (!sets.isEmpty()) {
                sets.append(", ");
            }
            sets.append(h[0]).append('-').append(h[1]);
        }
        Msg.send(audience(), winner.displayName().append(Component.text(" won the match " + detail
                + (sets.isEmpty() ? "" : " (" + sets + ")"), NamedTextColor.WHITE)));
        for (Player p : winner.online()) {
            launchFirework(p.getLocation(), winner.color());
        }
    }

    private void launchFirework(Location at, net.kyori.adventure.text.format.TextColor color) {
        try {
            Firework fw = at.getWorld().spawn(at.clone().add(0, 1, 0), Firework.class, f -> {
                FireworkMeta meta = f.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                        .withColor(Color.fromRGB(color.value())).withFade(Color.WHITE).trail(true).build());
                meta.setPower(1);
                f.setFireworkMeta(meta);
            });
            fw.setSilent(false);
        } catch (Exception ignored) {
            // cosmetic only
        }
    }

    private void finish() {
        removeBall();
        enter(MatchState.FINISHED, 8 * 20);
    }

    private void removeBall() {
        if (ball != null) {
            plugin.balls().remove(ball);
            ball = null;
        }
    }

    // ------------------------------------------------------------------------------------------- referee / team actions

    public boolean undoLastPoint() {
        if (undo.isEmpty() || state == MatchState.FINISHED) {
            return false;
        }
        score.restore(undo.pop());
        Msg.send(audience(), "<yellow>Referee: last point cancelled.");
        beginRallySetup();
        return true;
    }

    public void replayRally() {
        if (state == MatchState.FINISHED) {
            return;
        }
        Msg.send(audience(), "<yellow>Referee: replay the rally.");
        whistle();
        beginRallySetup();
    }

    public void togglePause() {
        if (state == MatchState.FINISHED) {
            return;
        }
        if (state == MatchState.PAUSED) {
            Msg.send(audience(), "<green>Match resumed.");
            beginRallySetup();
        } else {
            removeBall();
            enter(MatchState.PAUSED, 0);
            Msg.send(audience(), "<yellow>Match paused by the referee.");
        }
    }

    public boolean callTimeout(Player p) {
        Team t = teamOf(p.getUniqueId());
        if (t == null || state == MatchState.RALLY || state == MatchState.SERVE && served
                || state == MatchState.FINISHED || state == MatchState.TIMEOUT || state == MatchState.PAUSED) {
            return false;
        }
        if (t.timeoutsUsed() >= settings.get(MatchSettings.Option.TIMEOUTS_PER_SET)) {
            return false;
        }
        t.useTimeout();
        removeBall();
        longWhistle();
        enter(MatchState.TIMEOUT, settings.get(MatchSettings.Option.TIMEOUT_SECONDS) * 20);
        Msg.send(audience(), t.displayName().append(Component.text(" called a timeout (" + t.timeoutsUsed() + "/"
                + settings.get(MatchSettings.Option.TIMEOUTS_PER_SET) + ")", NamedTextColor.GRAY)));
        return true;
    }

    public boolean isOfficial(Player p) {
        return p.getUniqueId().equals(host) || p.getUniqueId().equals(referee) || p.hasPermission("powderpass.referee");
    }
}
