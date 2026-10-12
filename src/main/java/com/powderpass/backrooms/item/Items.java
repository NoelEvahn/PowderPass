package com.powderpass.backrooms.item;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.display.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Backrooms survival items, identified by a PDC tag rather than by name. */
public final class Items {
    public static final String ALMOND = "almond_water", FLASHLIGHT = "flashlight", BATTERY = "battery", RATION = "ration", NOTE = "note";
    private final BackroomsPlugin plugin;

    public Items(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    private static Component name(String mini) {
        return BackroomsConfig.mm(mini).decoration(TextDecoration.ITALIC, false);
    }

    private static List<Component> lore(String... lines) {
        List<Component> out = new ArrayList<>();
        for (String l : lines) out.add(BackroomsConfig.mm("<gray>" + l).decoration(TextDecoration.ITALIC, true));
        return out;
    }

    private static void tag(ItemMeta m, String id) {
        m.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, id);
    }

    public String id(ItemStack s) {
        if (s == null || !s.hasItemMeta()) return null;
        return s.getItemMeta().getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING);
    }

    public ItemStack almondWater() {
        ItemStack s = new ItemStack(Material.POTION);
        PotionMeta m = (PotionMeta) s.getItemMeta();
        m.setBasePotionType(PotionType.WATER);
        m.setColor(Color.fromRGB(0xF1E6C8));
        m.itemName(name("<#f3e9c6>Almond Water"));
        m.lore(lore("Faintly sweet. Steadies the mind.", "Restores sanity and a little hunger."));
        tag(m, ALMOND);
        s.setItemMeta(m);
        return s;
    }

    public ItemStack flashlight(int seconds) {
        ItemStack s = new ItemStack(Material.STICK);
        ItemMeta m = s.getItemMeta();
        m.setItemModel(NamespacedKey.minecraft("spyglass"));
        m.itemName(name("<#ffe9a8>Flashlight"));
        m.lore(lore("Hold it to light the way.", "Right-click a Battery to recharge."));
        m.setMaxStackSize(1);
        tag(m, FLASHLIGHT);
        m.getPersistentDataContainer().set(Keys.BATTERY, PersistentDataType.INTEGER, seconds);
        s.setItemMeta(m);
        return s;
    }

    public ItemStack battery() {
        ItemStack s = new ItemStack(Material.PAPER);
        ItemMeta m = s.getItemMeta();
        m.setItemModel(NamespacedKey.minecraft("copper_ingot"));
        m.itemName(name("<#d9a066>Battery"));
        m.lore(lore("Right-click to recharge a flashlight."));
        tag(m, BATTERY);
        s.setItemMeta(m);
        return s;
    }

    public ItemStack ration() {
        ItemStack s = new ItemStack(Material.BREAD);
        ItemMeta m = s.getItemMeta();
        m.itemName(name("<#c8b48a>Stale Ration"));
        m.lore(lore("Best before: yesterday, always."));
        tag(m, RATION);
        s.setItemMeta(m);
        return s;
    }

    private static final Map<Level, String[]> NOTES = Map.of(
            Level.LEVEL_0, new String[]{"The walls aren't all the same. One of them is cold and smells like chlorine. Crouch into it.",
                    "If you see a green EXIT sign on a door that stands alone in a room, go through. Don't think about it.",
                    "Stairs down lead somewhere wetter and greyer. Level 1, they call it. Bring water."},
            Level.POOLROOMS, new String[]{"There's a drain at the bottom of the deepest pool. It goes back to the yellow rooms.",
                    "Sunlight. I SAW sunlight through an arch. Real light.", "Grey door, STAFF ONLY. It's dry on the other side."},
            Level.LEVEL_1, new String[]{"The dogs hear you run. Walk. If one growls, it's about to jump.",
                    "MAINTENANCE stairs go down into the pipes. Hot down there.", "A freight elevator here goes UP. Offices, I think.",
                    "Rooms with wooden floors and warm light are safe. They don't come in."},
            Level.LEVEL_2, new String[]{"Dead ends hiss before they burn. Count to two and step back.",
                    "There's a hatch with a ladder. It hums at the bottom. Electricity.", "Stairs up, toward the light, lead back to the concrete halls."},
            Level.LEVEL_3, new String[]{"Don't touch the bright knots of light that wander the halls.",
                    "The lift upstairs says OFFICES. The ladders connect the floors.", "During a surge, get out of the water."},
            Level.LEVEL_4, new String[]{"The windows lie. Ignore them.", "The elevator goes to a hotel. Nobody should go to that hotel.",
                    "FIRE EXIT. Copper door. It opens to somewhere with weather."},
            Level.LEVEL_5, new String[]{"The concierge only moves when you aren't looking. Ring a bell and it goes away.",
                    "The revolving door in the lobby turns out. OUT.", "Room 000 smells like wet carpet. It goes back to the start."});

    public ItemStack note(Level level) {
        String[] pool = NOTES.getOrDefault(level, NOTES.get(Level.LEVEL_0));
        String text = pool[ThreadLocalRandom.current().nextInt(pool.length)];
        ItemStack s = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta m = (BookMeta) s.getItemMeta();
        m.setTitle("Damp Note");
        m.setAuthor("Unknown wanderer");
        m.addPages(BackroomsConfig.mm("<dark_gray>" + text));
        m.itemName(name("<#c9c0a8>Damp Note"));
        tag(m, NOTE);
        s.setItemMeta(m);
        return s;
    }

    public int charge(ItemStack s) {
        if (!FLASHLIGHT.equals(id(s))) return 0;
        return s.getItemMeta().getPersistentDataContainer().getOrDefault(Keys.BATTERY, PersistentDataType.INTEGER, 0);
    }

    public void setCharge(ItemStack s, int seconds) {
        ItemMeta m = s.getItemMeta();
        m.getPersistentDataContainer().set(Keys.BATTERY, PersistentDataType.INTEGER, Math.max(0, seconds));
        s.setItemMeta(m);
    }

    public BackroomsPlugin plugin() {
        return plugin;
    }
}
