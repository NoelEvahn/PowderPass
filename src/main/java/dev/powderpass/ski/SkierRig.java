package dev.powderpass.ski;

import dev.powderpass.display.Displays;
import dev.powderpass.display.Transforms;
import dev.powderpass.gear.GearTier;
import dev.powderpass.profile.SuitPalette;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The entity side of an animated skier: an invisible anchor that the rider (if any) and every body part ride
 * on, so the whole skier moves as one smoothly interpolated unit. Poses are pushed as transformation updates,
 * only when they actually change.
 */
public final class SkierRig {

    /** Appearance of a rig. */
    public record Look(GearTier tier, SuitPalette suit, boolean board, Player skin, boolean ghost, boolean nametag) {
    }

    private static final float EPSILON = 0.004f;

    private final Plugin plugin;
    private final Look look;
    private final float riderOffset;
    private final int poseSmoothing;
    private final float headYawOffset;
    private final ItemDisplay anchor;
    private final Map<RigPart, Display> parts = new EnumMap<>(RigPart.class);
    private final Map<RigPart, Matrix4f> sent = new EnumMap<>(RigPart.class);
    private final Map<RigPart, Matrix4f> scratch = new EnumMap<>(RigPart.class);
    private final Player viewer;
    private Player hiddenFrom;
    private boolean removed;

    /**
     * Spawns a rig at {@code feet}.
     *
     * @param viewer when non-null the rig is visible to this player only (ghosts)
     */
    public SkierRig(Plugin plugin, Location feet, Look look, RigPose initial, float riderOffset, int movementSmoothing,
                    int poseSmoothing, float viewRange, float headYawOffset, Player viewer) {
        this.plugin = plugin;
        this.look = look;
        this.riderOffset = riderOffset;
        this.poseSmoothing = poseSmoothing;
        this.headYawOffset = headYawOffset;
        this.viewer = viewer;
        Location at = feet.clone().add(0, riderOffset, 0);
        at.setYaw(0f);
        at.setPitch(0f);

        SkierModel.compute(initial, riderOffset, !look.ghost(), scratch);
        applyHeadOffset(scratch);
        this.anchor = Displays.anchor(at, movementSmoothing, d -> {
            if (viewer != null) {
                d.setVisibleByDefault(false);
            }
        });
        if (viewer != null) {
            viewer.showEntity(plugin, anchor);
        }
        for (RigPart part : RigPart.values()) {
            if (!uses(part)) {
                continue;
            }
            Matrix4f m = scratch.get(part);
            Display display = spawnPart(at, part, m, viewRange, viewer);
            if (display == null) {
                continue;
            }
            display.setInterpolationDuration(poseSmoothing);
            anchor.addPassenger(display);
            parts.put(part, display);
            sent.put(part, new Matrix4f(m));
        }
    }

    private boolean uses(RigPart part) {
        if (part == RigPart.NAMETAG) {
            return look.nametag();
        }
        if (part.boardOnly()) {
            return look.board();
        }
        if (part.skisOnly()) {
            return !look.board();
        }
        if (look.ghost() && (part == RigPart.GOGGLES || part == RigPart.POMPOM)) {
            return false;
        }
        return true;
    }

    private Display spawnPart(Location at, RigPart part, Matrix4f m, float viewRange, Player viewer) {
        Display display;
        if (part == RigPart.NAMETAG) {
            display = Displays.text(at, Component.empty(), m, viewRange, t -> {
                t.setSeeThrough(false);
                t.setBillboard(Display.Billboard.CENTER);
                t.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
                if (viewer != null) {
                    t.setVisibleByDefault(false);
                }
            });
        } else if (part == RigPart.HEAD) {
            ItemStack head;
            if (look.ghost() || look.skin() == null) {
                head = new ItemStack(look.ghost() ? Material.LIGHT_BLUE_STAINED_GLASS : Material.PLAYER_HEAD);
            } else {
                head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setPlayerProfile(look.skin().getPlayerProfile());
                head.setItemMeta(meta);
            }
            display = Displays.item(at, head, m, viewRange, d -> {
                if (viewer != null) {
                    d.setVisibleByDefault(false);
                }
            });
        } else {
            display = Displays.block(at, material(part).createBlockData(), m, viewRange, (BlockDisplay d) -> {
                if (viewer != null) {
                    d.setVisibleByDefault(false);
                }
            });
        }
        if (look.ghost()) {
            display.setGlowing(true);
            display.setGlowColorOverride(Color.fromRGB(0x7DD3FC));
        }
        if (viewer != null) {
            viewer.showEntity(plugin, display);
        }
        return display;
    }

    private Material material(RigPart part) {
        if (look.ghost()) {
            return switch (part) {
                case SKI_L, SKI_R, TIP_L, TIP_R, BOARD, NOSE, TAIL, POLE_L, POLE_R -> Material.WHITE_STAINED_GLASS;
                default -> Material.LIGHT_BLUE_STAINED_GLASS;
            };
        }
        return switch (part) {
            case SKI_L, SKI_R, BOARD -> look.tier().baseBlock();
            case TIP_L, TIP_R, NOSE, TAIL -> look.tier().tipBlock();
            case LEG_L, LEG_R -> look.suit().pants();
            case TORSO -> look.suit().jacket();
            case ARM_L, ARM_R -> look.suit().accent();
            case POLE_L, POLE_R -> Material.LIGHT_GRAY_CONCRETE;
            case GOGGLES -> Material.ORANGE_STAINED_GLASS;
            case POMPOM -> look.suit().pompom();
            default -> Material.WHITE_CONCRETE;
        };
    }

    private void applyHeadOffset(Map<RigPart, Matrix4f> matrices) {
        if (headYawOffset != 0f && !look.ghost()) {
            Matrix4f head = matrices.get(RigPart.HEAD);
            if (head != null) {
                head.rotateY((float) Math.toRadians(headYawOffset));
            }
        }
    }

    public ItemDisplay anchor() {
        return anchor;
    }

    public boolean valid() {
        return !removed && anchor.isValid();
    }

    /** Hides the parts that would block the rider's own camera. */
    public void hideFromRider(Player rider) {
        hiddenFrom = rider;
        for (Map.Entry<RigPart, Display> e : parts.entrySet()) {
            if (e.getKey().hiddenFromRider()) {
                rider.hideEntity(plugin, e.getValue());
            }
        }
    }

    /** Moves the whole rig so the skis rest at {@code x, y, z}. */
    public void moveTo(double x, double y, double z) {
        if (!valid()) {
            return;
        }
        Location loc = new Location(anchor.getWorld(), x, y + riderOffset, z, 0f, 0f);
        anchor.teleport(loc); // passengers are retained by default since 1.21.10
    }

    public void pose(RigPose pose) {
        if (!valid()) {
            return;
        }
        SkierModel.compute(pose, riderOffset, !look.ghost(), scratch);
        applyHeadOffset(scratch);
        for (Map.Entry<RigPart, Display> e : parts.entrySet()) {
            RigPart part = e.getKey();
            Matrix4f m = scratch.get(part);
            if (m == null || part == RigPart.NAMETAG) {
                continue;
            }
            Matrix4f last = sent.get(part);
            if (Transforms.close(last, m, EPSILON)) {
                continue;
            }
            Display d = e.getValue();
            if (!d.isValid()) {
                continue;
            }
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(poseSmoothing);
            d.setTransformationMatrix(m);
            last.set(m);
        }
    }

    public void nametag(Component text) {
        Display d = parts.get(RigPart.NAMETAG);
        if (d instanceof TextDisplay t && t.isValid()) {
            t.text(text);
        }
    }

    /**
     * Detaches the gear parts (skis/board) for a crash animation. The caller takes ownership and must remove them.
     */
    public List<Display> detachGear() {
        List<Display> out = new ArrayList<>();
        for (RigPart part : List.of(RigPart.SKI_L, RigPart.SKI_R, RigPart.TIP_L, RigPart.TIP_R,
                RigPart.BOARD, RigPart.NOSE, RigPart.TAIL, RigPart.POLE_L, RigPart.POLE_R)) {
            Display d = parts.remove(part);
            if (d != null && d.isValid()) {
                anchor.removePassenger(d);
                out.add(d);
            }
        }
        return out;
    }

    public List<Entity> entities() {
        List<Entity> out = new ArrayList<>(parts.values());
        out.add(anchor);
        return out;
    }

    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        // Drop the per-player visibility overrides so they don't pile up for removed entities.
        for (Map.Entry<RigPart, Display> e : parts.entrySet()) {
            Display d = e.getValue();
            if (hiddenFrom != null && hiddenFrom.isOnline() && e.getKey().hiddenFromRider()) {
                hiddenFrom.showEntity(plugin, d);
            }
            if (viewer != null && viewer.isOnline()) {
                viewer.hideEntity(plugin, d);
            }
            if (d.isValid()) {
                d.remove();
            }
        }
        if (viewer != null && viewer.isOnline()) {
            viewer.hideEntity(plugin, anchor);
        }
        parts.clear();
        if (anchor.isValid()) {
            anchor.eject();
            anchor.remove();
        }
    }
}
