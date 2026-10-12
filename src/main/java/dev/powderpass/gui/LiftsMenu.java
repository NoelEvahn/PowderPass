package dev.powderpass.gui;

import dev.powderpass.PowderPassPlugin;
import dev.powderpass.lift.Lift;
import dev.powderpass.lift.LiftDefinition;
import dev.powderpass.util.ItemBuilder;
import dev.powderpass.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Overview of the resort's chairlifts. */
public final class LiftsMenu extends Menu {

    public LiftsMenu(PowderPassPlugin plugin, Player player) {
        super(4, Text.mm("<#4ade80><bold>🚡 Chairlifts"));
        List<LiftDefinition> defs = new ArrayList<>(plugin.lifts().definitions());
        for (int i = 0; i < defs.size() && i < 27; i++) {
            LiftDefinition d = defs.get(i);
            Lift lift = plugin.lifts().lift(d.name);
            double vertical = d.ty - d.by;
            double ride = lift == null ? 0 : lift.layout().rideLengthUp() / Math.max(0.01, d.speed) / 20.0;
            boolean pass = d.requirePass != null ? d.requirePass : plugin.config().liftRequirePass;
            set(i, ItemBuilder.of(Material.MINECART).name("<white><bold>" + Text.escape(d.name))
                    .lore("<gray>Style: <white>" + d.style.display(),
                            "<gray>Length: <white>" + Math.round(d.horizontalLength()) + " m",
                            "<gray>Vertical: <white>" + Math.round(vertical) + " m",
                            "<gray>Ride time: <white>" + Math.round(ride) + "s",
                            "<gray>Chairs: <white>" + (lift == null ? "-" : lift.chairCount()) + " <dark_gray>× " + d.seats + " seats",
                            "<gray>Pass required: <white>" + (pass ? "yes" : "no"),
                            "<gray>Status: " + (lift == null ? "<red>offline" : lift.active() ? "<#4ade80>running" : "<yellow>idle"),
                            "",
                            "<dark_gray>Base: " + Math.round(d.bx) + ", " + Math.round(d.by) + ", " + Math.round(d.bz))
                    .build());
        }
        if (defs.isEmpty()) {
            set(13, ItemBuilder.of(Material.BARRIER).name("<gray>No lifts yet")
                    .lore("<dark_gray>Admins: /pp lift create <name>").build());
        }
        set(31, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(), e -> new MainMenu(plugin, player).open(player));
        fill(Material.LIME_STAINED_GLASS_PANE);
    }
}
