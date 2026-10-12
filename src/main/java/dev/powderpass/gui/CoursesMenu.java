package dev.powderpass.gui;

import dev.powderpass.PowderPassPlugin;
import dev.powderpass.course.Course;
import dev.powderpass.util.ItemBuilder;
import dev.powderpass.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Race courses with records, leaderboards and the viewer's personal best. */
public final class CoursesMenu extends Menu {

    public CoursesMenu(PowderPassPlugin plugin, Player player) {
        super(4, Text.mm("<#f87171><bold>⛳ Race Courses"));
        List<Course> courses = new ArrayList<>(plugin.courses().courses());
        for (int i = 0; i < courses.size() && i < 27; i++) {
            Course c = courses.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>World: <white>" + Text.escape(c.world()));
            lore.add("<gray>Gates: <white>" + c.slalomGates().size() + (c.complete() ? "" : " <red>(no finish yet)"));
            lore.add("");
            Course.Entry record = c.record();
            lore.add(record == null ? "<gray>No record yet" : "<#facc15>★ Record: <white>" + Text.escape(record.name())
                    + " <#facc15>" + Text.raceTime(record.ticks()));
            Course.Entry mine = c.best(player.getUniqueId());
            lore.add(mine == null ? "<gray>You: <dark_gray>no time" : "<gray>You: <white>" + Text.raceTime(mine.ticks())
                    + " <dark_gray>(#" + c.rank(player.getUniqueId()) + ")");
            lore.add("");
            List<Course.Entry> top = c.top(5);
            for (int k = 0; k < top.size(); k++) {
                lore.add("<dark_gray>" + (k + 1) + ". <gray>" + Text.escape(top.get(k).name()) + " <white>" + Text.raceTime(top.get(k).ticks()));
            }
            lore.add("");
            lore.add("<dark_gray>Start: " + Math.round(c.start().x()) + ", " + Math.round(c.start().y()) + ", " + Math.round(c.start().z()));
            set(i, ItemBuilder.of(i % 2 == 0 ? Material.RED_BANNER : Material.BLUE_BANNER)
                    .name("<white><bold>" + Text.escape(c.name())).lore(lore.toArray(String[]::new)).hideAll().build());
        }
        if (courses.isEmpty()) {
            set(13, ItemBuilder.of(Material.BARRIER).name("<gray>No courses yet")
                    .lore("<dark_gray>Admins: /pp course create <name>").build());
        }
        set(31, ItemBuilder.of(Material.ARROW).name("<gray>Back").build(), e -> new MainMenu(plugin, player).open(player));
        fill(Material.WHITE_STAINED_GLASS_PANE);
    }
}
