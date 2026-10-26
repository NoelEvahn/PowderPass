package dev.powderpass.volleyball.court;

import dev.powderpass.volleyball.game.MatchSettings;
import dev.powderpass.volleyball.math.CourtFrame;
import dev.powderpass.volleyball.math.Vec3;
import dev.powderpass.volleyball.physics.CourtNet;
import dev.powderpass.volleyball.physics.NetGeometry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * A volleyball court placed in a world. Geometry is described in a court-local frame
 * (see {@link CourtFrame}); side A is u &lt; 0, side B is u &gt; 0.
 */
public final class Court {

    private final String name;
    private final String worldName;
    private final double cx;
    private final double cy;
    private final double cz;
    private final int dirX;
    private final int dirZ;
    private double length;
    private double width;
    private double netHeight;
    private double attackLine;
    private double freeZone;
    private double netBand;
    private double antennaAbove;
    private double postOffset;
    private boolean beach;
    private boolean floorPlaced;
    private MatchSettings settings;

    private CourtFrame frame;
    private CourtNet net;

    public Court(String name, String worldName, double cx, double cy, double cz, int dirX, int dirZ) {
        this.name = name;
        this.worldName = worldName;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.dirX = dirX;
        this.dirZ = dirZ;
    }

    public void applyDimensions(double length, double width, double netHeight, double attackLine, double freeZone,
                                double netBand, double antennaAbove, double postOffset, boolean beach) {
        this.length = length;
        this.width = width;
        this.netHeight = netHeight;
        this.attackLine = attackLine;
        this.freeZone = freeZone;
        this.netBand = Math.min(netBand, netHeight - 0.2);
        this.antennaAbove = antennaAbove;
        this.postOffset = postOffset;
        this.beach = beach;
        rebuildGeometry();
    }

    private void rebuildGeometry() {
        frame = new CourtFrame(cx, cy, cz, dirX, dirZ);
        double halfW = width / 2;
        NetGeometry g = new NetGeometry(netHeight, netBand, halfW + postOffset - 0.05, halfW, antennaAbove, 0.02,
                0.035, halfW + postOffset, 0.06, 0.012);
        net = new CourtNet(frame, g);
    }

    public String name() {
        return name;
    }

    public String worldName() {
        return worldName;
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public CourtFrame frame() {
        return frame;
    }

    public CourtNet net() {
        return net;
    }

    public double length() {
        return length;
    }

    public double width() {
        return width;
    }

    public double halfLength() {
        return length / 2;
    }

    public double halfWidth() {
        return width / 2;
    }

    public double netHeight() {
        return netHeight;
    }

    public double attackLine() {
        return attackLine;
    }

    public double freeZone() {
        return freeZone;
    }

    public double netBand() {
        return netBand;
    }

    public double antennaAbove() {
        return antennaAbove;
    }

    public double postOffset() {
        return postOffset;
    }

    public boolean beach() {
        return beach;
    }

    public boolean floorPlaced() {
        return floorPlaced;
    }

    public void setFloorPlaced(boolean v) {
        floorPlaced = v;
    }

    public MatchSettings settings() {
        return settings;
    }

    public void setSettings(MatchSettings s) {
        this.settings = s;
    }

    public void setNetHeight(double h) {
        this.netHeight = h;
        this.netBand = Math.min(netBand, h - 0.2);
        rebuildGeometry();
    }

    public int dirX() {
        return dirX;
    }

    public int dirZ() {
        return dirZ;
    }

    public double centerX() {
        return cx;
    }

    public double centerY() {
        return cy;
    }

    public double centerZ() {
        return cz;
    }

    /** True when a ball touching the floor at (u, w) is in. Lines are part of the court; any ball overlap counts. */
    public boolean isIn(double u, double w, double ballRadius) {
        return Math.abs(u) <= halfLength() + ballRadius * 0.5 && Math.abs(w) <= halfWidth() + ballRadius * 0.5;
    }

    /** True when the point lies within the court plus free zone (+ margin). */
    public boolean inArea(double u, double w, double margin) {
        return Math.abs(u) <= halfLength() + freeZone + margin && Math.abs(w) <= halfWidth() + freeZone + margin;
    }

    public boolean inArea(Location loc, double margin) {
        if (!loc.getWorld().getName().equals(worldName)) {
            return false;
        }
        if (Math.abs(loc.getY() - cy) > 12) {
            return false;
        }
        return inArea(frame.toU(loc.getX(), loc.getZ()), frame.toW(loc.getX(), loc.getZ()), margin);
    }

    /** Converts court-local coordinates to a world location. */
    public Location location(double u, double y, double w) {
        Vec3 v = frame.toWorld(u, y, w, new Vec3());
        return new Location(world(), v.x, v.y, v.z);
    }

    /** Location facing the net from the given side. */
    public Location facingNet(double u, double w) {
        Location l = location(u, 0, w);
        l.setYaw(frame.yawOf(-Math.signum(u == 0 ? 1 : u), 0));
        l.setPitch(0);
        return l;
    }

    public Location center() {
        return location(0, 0, 0);
    }

    public void save(ConfigurationSection s) {
        s.set("world", worldName);
        s.set("center.x", cx);
        s.set("center.y", cy);
        s.set("center.z", cz);
        s.set("direction.x", dirX);
        s.set("direction.z", dirZ);
        s.set("length", length);
        s.set("width", width);
        s.set("net-height", netHeight);
        s.set("attack-line", attackLine);
        s.set("free-zone", freeZone);
        s.set("net-band", netBand);
        s.set("antenna-above", antennaAbove);
        s.set("post-offset", postOffset);
        s.set("beach", beach);
        s.set("floor-placed", floorPlaced);
        if (settings != null) {
            settings.save(s.createSection("settings"));
        }
    }

    public static Court load(String name, ConfigurationSection s, MatchSettings defaults) {
        Court c = new Court(name, s.getString("world", "world"),
                s.getDouble("center.x"), s.getDouble("center.y"), s.getDouble("center.z"),
                s.getInt("direction.x", 1), s.getInt("direction.z", 0));
        c.applyDimensions(s.getDouble("length", 18), s.getDouble("width", 9), s.getDouble("net-height", 2.43),
                s.getDouble("attack-line", 3), s.getDouble("free-zone", 3), s.getDouble("net-band", 1),
                s.getDouble("antenna-above", 0.8), s.getDouble("post-offset", 0.75), s.getBoolean("beach", false));
        c.floorPlaced = s.getBoolean("floor-placed", false);
        c.settings = MatchSettings.load(s.getConfigurationSection("settings"), defaults);
        return c;
    }
}
