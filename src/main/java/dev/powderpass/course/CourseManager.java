package dev.powderpass.course;

import dev.powderpass.PowderPassPlugin;
import dev.powderpass.config.Messages;
import dev.powderpass.config.PluginConfig;
import dev.powderpass.gear.GearType;
import dev.powderpass.physics.SkiState;
import dev.powderpass.profile.Badge;
import dev.powderpass.profile.PlayerData;
import dev.powderpass.ski.RigPose;
import dev.powderpass.ski.SkiSession;
import dev.powderpass.ski.SkierRig;
import dev.powderpass.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.joml.Vector3d;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/** Slalom courses: editing, visuals, timing, leaderboards and record ghosts. */
public final class CourseManager {

    /** Per-skier state of a run in progress. */
    private static final class RaceRun {
        final Course course;
        final RaceTracker tracker;
        final GhostRun recording;
        final BossBar bar;
        final GhostRun ghost;
        SkierRig ghostRig;
        double lastDelta = Double.NaN;

        RaceRun(Course course, RaceTracker tracker, GhostRun recording, BossBar bar, GhostRun ghost) {
            this.course = course;
            this.tracker = tracker;
            this.recording = recording;
            this.bar = bar;
            this.ghost = ghost;
        }
    }

    private static final double START_SEARCH_RANGE_SQ = 48 * 48;
    private static final double MAX_DY = 6.0;
    private static final double MAX_OUTSIDE = 12.0;

    private final PowderPassPlugin plugin;
    private final File file;
    private final File ghostFolder;
    private final Map<String, Course> courses = new LinkedHashMap<>();
    private final Map<String, CourseVisuals> visuals = new LinkedHashMap<>();
    private long tick;

    public CourseManager(PowderPassPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "courses.yml");
        this.ghostFolder = new File(plugin.getDataFolder(), "ghosts");
    }

    private PluginConfig config() {
        return plugin.config();
    }

    private Messages messages() {
        return plugin.messages();
    }

    // ------------------------------------------------------------------------------------------------ persistence

    public void load() {
        courses.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("courses");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            Course c = Course.load(key, s);
            if (c == null) {
                plugin.getLogger().warning("Skipping course " + key + ": no gates");
                continue;
            }
            File ghostFile = new File(ghostFolder, key.toLowerCase(Locale.ROOT) + ".ghost");
            if (ghostFile.exists() && c.ghostName() != null) {
                try (InputStream in = new FileInputStream(ghostFile)) {
                    c.ghost(GhostRun.read(in), c.ghostName(), c.ghostTicks());
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not read ghost for " + key, e);
                }
            }
            courses.put(key.toLowerCase(Locale.ROOT), c);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        ConfigurationSection root = y.createSection("courses");
        for (Course c : courses.values()) {
            c.save(root.createSection(c.name()));
        }
        String data = y.saveToString();
        plugin.writeAsync(file, data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void saveGhost(Course course) {
        GhostRun ghost = course.ghost();
        File target = new File(ghostFolder, course.name().toLowerCase(Locale.ROOT) + ".ghost");
        if (ghost == null) {
            plugin.deleteAsync(target);
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ghost.write(bytes);
            plugin.writeAsync(target, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not encode ghost", e);
        }
    }

    // ------------------------------------------------------------------------------------------------ editing

    public Collection<Course> courses() {
        return List.copyOf(courses.values());
    }

    public Course get(String name) {
        return name == null ? null : courses.get(name.toLowerCase(Locale.ROOT));
    }

    public List<String> names() {
        return courses.values().stream().map(Course::name).toList();
    }

    private Course.GateDef gateAt(Player p, double width) {
        Location l = p.getLocation();
        float yaw = Math.round(l.getYaw() / 5f) * 5f;
        return new Course.GateDef(l.getX(), l.getY(), l.getZ(), yaw, width);
    }

    public boolean create(Player p, String name) {
        if (!name.matches("[A-Za-z0-9_-]{1,32}") || get(name) != null) {
            return false;
        }
        Course c = new Course(name, p.getWorld().getName(), gateAt(p, config().gateWidth * 1.2));
        courses.put(name.toLowerCase(Locale.ROOT), c);
        rebuild(c);
        save();
        return true;
    }

    public int addGate(Player p, Course c) {
        c.addGate(gateAt(p, config().gateWidth));
        afterEdit(c);
        return c.slalomGates().size();
    }

    public void setFinish(Player p, Course c) {
        c.finish(gateAt(p, config().finishWidth));
        afterEdit(c);
    }

    public boolean undo(Course c) {
        boolean removed = c.removeLastGate();
        if (removed) {
            afterEdit(c);
        }
        return removed;
    }

    /** Editing the layout invalidates times set on the old layout. */
    private void afterEdit(Course c) {
        abortRacesOn(c);
        if (c.entries() > 0 || c.ghost() != null) {
            c.resetResults();
            saveGhost(c);
        }
        rebuild(c);
        save();
    }

    public void remove(Course c) {
        abortRacesOn(c);
        courses.remove(c.name().toLowerCase(Locale.ROOT));
        CourseVisuals v = visuals.remove(c.name().toLowerCase(Locale.ROOT));
        if (v != null) {
            v.despawn();
        }
        c.resetResults();
        saveGhost(c);
        save();
    }

    public void reset(Course c) {
        abortRacesOn(c);
        c.resetResults();
        saveGhost(c);
        refreshTexts(c);
        save();
    }

    // ------------------------------------------------------------------------------------------------ visuals

    public void rebuildAll() {
        for (CourseVisuals v : visuals.values()) {
            v.despawn();
        }
        visuals.clear();
        for (Course c : courses.values()) {
            rebuild(c);
        }
    }

    private void rebuild(Course c) {
        String key = c.name().toLowerCase(Locale.ROOT);
        CourseVisuals old = visuals.remove(key);
        if (old != null) {
            old.despawn();
        }
        World world = Bukkit.getWorld(c.world());
        if (world == null) {
            return;
        }
        visuals.put(key, new CourseVisuals(world, c, () -> leaderboardText(c), () -> finishText(c)));
    }

    private void refreshTexts(Course c) {
        CourseVisuals v = visuals.get(c.name().toLowerCase(Locale.ROOT));
        if (v != null) {
            v.updateTexts(leaderboardText(c), finishText(c));
        }
    }

    private Component leaderboardText(Course c) {
        StringBuilder sb = new StringBuilder("<gradient:#7dd3fc:#e0f2fe><bold>⛷ ")
                .append(Text.escape(c.name())).append("</bold></gradient>\n<dark_gray>───────────────\n");
        List<Course.Entry> top = c.top(config().leaderboardSize);
        if (top.isEmpty()) {
            sb.append("<gray><italic>No times yet.\n<gray>Cross the start gate!");
        }
        for (int i = 0; i < top.size(); i++) {
            Course.Entry e = top.get(i);
            String medal = switch (i) {
                case 0 -> "<#facc15>";
                case 1 -> "<#e5e7eb>";
                case 2 -> "<#d97706>";
                default -> "<gray>";
            };
            sb.append(medal).append(i + 1).append(". <white>").append(Text.escape(e.name()))
                    .append(" <dark_gray>— ").append(medal).append(Text.raceTime(e.ticks()));
            if (i < top.size() - 1) {
                sb.append('\n');
            }
        }
        return Text.mm(sb.toString());
    }

    private Component finishText(Course c) {
        Course.Entry record = c.record();
        String rec = record == null ? "<gray>No record yet"
                : "<#facc15>★ " + Text.escape(record.name()) + " " + Text.raceTime(record.ticks());
        return Text.mm("<gradient:#4ade80:#22d3ee><bold>FINISH</bold></gradient>\n" + rec);
    }

    public void tick() {
        tick++;
        if (tick % 20 == 0) {
            for (CourseVisuals v : visuals.values()) {
                v.sync(v.structure().playerNear(128));
            }
        }
        if (tick % 30 == 0) {
            for (CourseVisuals v : visuals.values()) {
                v.flutter();
            }
        }
    }

    public void onWorldUnload(World world) {
        for (CourseVisuals v : visuals.values()) {
            if (v.structure().world().equals(world)) {
                v.despawn();
            }
        }
    }

    public void onWorldLoad(World world) {
        for (Course c : courses.values()) {
            if (c.world().equals(world.getName())) {
                rebuild(c);
            }
        }
    }

    public void shutdown() {
        for (CourseVisuals v : visuals.values()) {
            v.despawn();
        }
        visuals.clear();
    }

    // ------------------------------------------------------------------------------------------------ racing

    /** Called by every ski session each tick after it moved from {@code p0} to {@code p1}. */
    public void onSkierMove(SkiSession session, Vector3d p0, Vector3d p1, long now) {
        if (session.raceState() instanceof RaceRun run) {
            advance(session, run, p0, p1, now);
            return;
        }
        if (!session.player().hasPermission("powderpass.race")) {
            return;
        }
        String world = session.world().getName();
        for (Course c : courses.values()) {
            if (!c.complete() || !c.world().equals(world)) {
                continue;
            }
            Course.GateDef s = c.start();
            double dx = s.x() - p1.x;
            double dz = s.z() - p1.z;
            if (dx * dx + dz * dz > START_SEARCH_RANGE_SQ) {
                continue;
            }
            double startTick = RaceTracker.startCrossing(s.line(), p0.x, p0.z, p1.x, p1.z, (p0.y + p1.y) / 2, now, MAX_DY);
            if (!Double.isNaN(startTick)) {
                begin(session, c, startTick);
                return;
            }
        }
    }

    private void begin(SkiSession session, Course course, double startTick) {
        Player p = session.player();
        PluginConfig cfg = config();
        RaceTracker tracker = new RaceTracker(course.lines(), startTick, cfg.missPolicy, cfg.missPenaltyTicks, MAX_OUTSIDE, MAX_DY);
        SkiState s = session.state();
        GhostRun recording = new GhostRun(s.pos.x, s.pos.y, s.pos.z, cfg.maxRunTicks + 40);
        BossBar bar = BossBar.bossBar(Component.text(course.name()), 0f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10);
        p.showBossBar(bar);
        GhostRun ghost = cfg.ghosts ? course.ghost() : null;
        RaceRun run = new RaceRun(course, tracker, recording, bar, ghost);
        session.raceState(run);
        if (ghost != null && ghost.size() > 0) {
            GhostRun.Frame f = ghost.sample(0);
            RigPose pose = new RigPose(f.yaw(), 0, 0, 0, 1, 0, f.lean(), f.crouch(), f.flip(), f.airborne(), false, -1, f.board());
            SkierRig.Look look = new SkierRig.Look(session.gear().tier(), session.data().suit, f.board(), null, true, true);
            run.ghostRig = new SkierRig(plugin, new Location(session.world(), f.x(), f.y(), f.z()), look, pose,
                    (float) cfg.riderOffset, 1, 1, 3.0f, 0f, p);
            run.ghostRig.nametag(Text.mm("<#7dd3fc>👻 " + Text.escape(course.ghostName() == null ? "Record" : course.ghostName())
                    + " <gray>" + Text.raceTime(course.ghostTicks())));
        }
        messages().actionBar(p, "course.started", Messages.p("course", course.name()));
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, org.bukkit.SoundCategory.PLAYERS, 1f, 1.6f);
    }

    private void advance(SkiSession session, RaceRun run, Vector3d p0, Vector3d p1, long now) {
        Player p = session.player();
        RigPose pose = session.currentPose();
        run.recording.add(p1.x, p1.y, p1.z, pose.visualYaw(), pose.flip(), pose.crouch(), pose.lean(), pose.airborne(),
                session.gear().type() == GearType.SNOWBOARD);
        double elapsed = run.tracker.elapsed(now);
        if (run.ghostRig != null) {
            if (elapsed > run.ghost.size() + 40) {
                run.ghostRig.remove();
                run.ghostRig = null;
            } else {
                GhostRun.Frame f = run.ghost.sample(elapsed);
                run.ghostRig.moveTo(f.x(), f.y(), f.z());
                run.ghostRig.pose(new RigPose(f.yaw(), 0, 0, 0, 1, 0, f.lean(), f.crouch(), f.flip(), f.airborne(),
                        false, -1, f.board()));
            }
        }

        for (RaceEvent e : run.tracker.update(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, now)) {
            switch (e.type()) {
                case GATE_PASSED -> onGate(session, run, e);
                case GATE_MISSED -> {
                    plugin.skis().effects().missed(p);
                    messages().actionBar(p, run.tracker.penaltyTicks() > 0 ? "course.missed-penalty" : "course.missed",
                            Messages.p("gate", e.gate()), Messages.p("penalty", Text.oneDecimal(config().missPenaltyTicks / 20.0)));
                }
                case FINISHED -> {
                    finish(session, run, e);
                    return;
                }
                case DISQUALIFIED -> {
                    messages().send(p, "course.disqualified", Messages.p("gate", e.gate()));
                    plugin.skis().effects().missed(p);
                    stop(session, run);
                    return;
                }
            }
        }
        if (elapsed > config().maxRunTicks) {
            messages().send(p, "course.timeout");
            stop(session, run);
            return;
        }
        if (now % 2 == 0) {
            updateBar(run, elapsed);
        }
    }

    private void onGate(SkiSession session, RaceRun run, RaceEvent e) {
        double[] rec = run.course.recordSplits();
        if (e.gate() < rec.length && rec[e.gate()] > 0) {
            run.lastDelta = e.totalTicks() - rec[e.gate()];
        }
        Course.GateDef g = run.course.slalomGates().get(e.gate() - 1);
        Location at = new Location(session.world(), g.x(), g.y() + 1.2, g.z());
        plugin.skis().effects().gate(session.player(), at, (e.gate() - 1) % 2 == 0, e.gate());
    }

    private void updateBar(RaceRun run, double elapsed) {
        int total = run.tracker.gateCount() - 1;
        int passed = Math.min(total, run.tracker.nextGate() - 1);
        String delta = "";
        if (!Double.isNaN(run.lastDelta)) {
            delta = (run.lastDelta <= 0 ? "  <#4ade80>" : "  <#f87171>") + Text.delta(run.lastDelta);
        }
        String penalty = run.tracker.missed() > 0 ? "  <#f87171>+" + run.tracker.missed() + " missed" : "";
        run.bar.name(Text.mm("<#7dd3fc>⛳ <white>" + Text.escape(run.course.name()) + "  <dark_gray>│  <white><bold>"
                + Text.raceTime(elapsed + run.tracker.penaltyTicks()) + "</bold>  <dark_gray>│  <gray>Gate "
                + passed + "/" + total + delta + penalty));
        run.bar.progress((float) Math.max(0, Math.min(1, passed / (double) Math.max(1, total))));
        if (!Double.isNaN(run.lastDelta)) {
            run.bar.color(run.lastDelta <= 0 ? BossBar.Color.GREEN : BossBar.Color.RED);
        }
    }

    private void finish(SkiSession session, RaceRun run, RaceEvent e) {
        Player p = session.player();
        Course c = run.course;
        double total = e.totalTicks();
        Course.Entry previousRecord = c.record();
        Course.Entry previousBest = c.best(p.getUniqueId());
        boolean pb = c.submit(p.getUniqueId(), p.getName(), total, System.currentTimeMillis());
        boolean record = previousRecord == null || total < previousRecord.ticks();
        int rank = c.rank(p.getUniqueId());
        PlayerData data = session.data();
        data.racesFinished++;
        data.markDirty();

        if (record) {
            double[] splits = new double[run.tracker.gateCount()];
            for (int i = 1; i < splits.length; i++) {
                splits[i] = run.tracker.split(i) + 0.0;
            }
            c.recordSplits(splits);
            c.ghost(run.recording, p.getName(), total);
            saveGhost(c);
        }
        save();
        refreshTexts(c);

        String sub;
        if (record) {
            sub = messages().raw("course.finish-record");
        } else if (pb) {
            sub = messages().raw("course.finish-pb").replace("<delta>",
                    previousBest == null ? "" : Text.delta(total - previousBest.ticks()));
        } else {
            sub = messages().raw("course.finish-slower").replace("<delta>",
                    Text.delta(total - (previousBest == null ? total : previousBest.ticks())));
        }
        p.showTitle(Title.title(Text.mm("<white><bold>" + Text.raceTime(total)), Text.mm(sub),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2500), Duration.ofMillis(600))));
        messages().send(p, "course.finished", Messages.p("course", c.name()), Messages.p("time", Text.raceTime(total)),
                Messages.p("rank", rank), Messages.p("missed", run.tracker.missed()));
        plugin.skis().effects().finish(p, p.getLocation(), record);

        if (record && config().announceRecords) {
            Component msg = messages().prefixed("course.record-broadcast", Messages.p("player", p.getName()),
                    Messages.p("course", c.name()), Messages.p("time", Text.raceTime(total)));
            for (Player other : session.world().getPlayers()) {
                if (other != p) {
                    other.sendMessage(msg);
                }
            }
        }
        if (run.tracker.missed() == 0) {
            plugin.badges().award(p, data, Badge.GATEKEEPER);
        }
        if (rank > 0 && rank <= 3) {
            plugin.badges().award(p, data, Badge.PODIUM);
        }
        if (record) {
            plugin.badges().award(p, data, Badge.COURSE_RECORD);
        }
        stop(session, run);
    }

    private void stop(SkiSession session, RaceRun run) {
        session.raceState(null);
        session.player().hideBossBar(run.bar);
        if (run.ghostRig != null) {
            run.ghostRig.remove();
            run.ghostRig = null;
        }
    }

    /** Ski session ended (unclip, crash, quit...): abandon the run. */
    public void onSkierEnd(SkiSession session, SkiSession.EndReason reason) {
        if (session.raceState() instanceof RaceRun run) {
            if (session.player().isOnline() && reason != SkiSession.EndReason.QUIT) {
                messages().send(session.player(), "course.dnf", Messages.p("course", run.course.name()));
            }
            stop(session, run);
        }
    }

    private void abortRacesOn(Course c) {
        for (SkiSession s : plugin.skis().sessions()) {
            if (s.raceState() instanceof RaceRun run && run.course == c) {
                messages().send(s.player(), "course.aborted");
                stop(s, run);
            }
        }
    }

    /** Copies a file atomically into place; used by the async writer. */
    public static void atomicWrite(File target, byte[] data) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        File tmp = new File(target.getPath() + ".tmp");
        Files.write(tmp.toPath(), data);
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
