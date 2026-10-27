package dev.powderpass.volleyball.ball;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.powderpass.volleyball.config.Settings;
import dev.powderpass.volleyball.util.Displays;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Draws the ball as a single item display: a volleyball player head, or the 3D model from the optional
 * resource pack (which replaces heads tagged with the {@value #MODEL} custom model data). One rigid entity:
 * it cannot come apart while spinning and costs one update per tick.
 */
public final class BallRenderer {

    public static final String MODEL = "powderpass:volleyball";
    private static final UUID PROFILE_ID = UUID.fromString("7c1f0b52-3f4e-4c8e-9b1a-56d0a1b2c3d4");
    private static ItemStack template;
    private static String templateTexture;

    private ItemDisplay display;
    private final Quaternionf shown = new Quaternionf();
    private float size;
    private float scale = 1f;
    private int rotationInterpolation;

    /** The head item, built once per texture. */
    static ItemStack ballItem(String texture) {
        if (template == null || !texture.equals(templateTexture)) {
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (!texture.isBlank()) {
                try {
                    PlayerProfile profile = Bukkit.createProfile(PROFILE_ID, "Volleyball");
                    PlayerTextures textures = profile.getTextures();
                    textures.setSkin(URI.create(texture).toURL());
                    profile.setTextures(textures);
                    meta.setPlayerProfile(profile);
                } catch (Exception e) {
                    Bukkit.getLogger().warning("[PowderPass] Invalid ball.texture: " + e.getMessage());
                }
            }
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of(MODEL));
            meta.setCustomModelDataComponent(cmd);
            item.setItemMeta(meta);
            template = item;
            templateTexture = texture;
        }
        return template.clone();
    }

    public void spawn(World world, Location at, Settings.Ball cfg) {
        remove();
        // a head is half a block across in the FIXED context
        size = (float) cfg.visualSize() * 2f;
        rotationInterpolation = cfg.rotationInterpolation();
        display = world.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(ballItem(cfg.texture()));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(transformation(0.05f));
            d.setTeleportDuration(cfg.teleportInterpolation());
            d.setInterpolationDuration(rotationInterpolation);
            d.setBillboard(Display.Billboard.FIXED);
            d.setViewRange(4f);
            if (cfg.shadow()) {
                d.setShadowRadius((float) cfg.visualSize() * 0.9f);
                d.setShadowStrength(0.9f);
            }
            Displays.tag(d, "ball");
        });
    }

    private Transformation transformation(float factor) {
        float s = size * factor;
        return new Transformation(new Vector3f(), new Quaternionf(shown), new Vector3f(s, s, s), new Quaternionf());
    }

    public boolean valid() {
        return display != null && display.isValid();
    }

    public Location location() {
        return display == null ? null : display.getLocation();
    }

    public void moveTo(Location loc) {
        if (display != null) {
            display.teleport(loc);
        }
    }

    /** Spin: a pure rotation about the ball centre, so client interpolation keeps it rigid. */
    public void rotate(Quaternionf rotation) {
        if (display == null || shown.equals(rotation, 0.004f)) {
            return;
        }
        shown.set(rotation);
        display.setTransformation(transformation(scale));
        Displays.refreshInterpolation(display, rotationInterpolation);
    }

    /** Animates the size (spawn pop-in, despawn shrink). */
    public void resize(float factor, int ticks) {
        scale = factor;
        if (display != null && display.isValid()) {
            display.setTransformation(transformation(scale));
            Displays.refreshInterpolation(display, ticks);
        }
    }

    public void setTeleportDuration(int ticks) {
        if (display != null && display.getTeleportDuration() != ticks) {
            display.setTeleportDuration(ticks);
        }
    }

    public void remove() {
        if (display != null) {
            display.remove();
            display = null;
        }
    }
}
