package com.powderpass.backrooms.item;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/** Limited, level-flavoured supplies for generated containers (filled once, on first open). */
public final class LootService {
    private final BackroomsPlugin plugin;

    public LootService(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    public void fill(Inventory inv, int tier, Level level) {
        if (tier <= 0) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Items it = plugin.items();
        int rolls = tier >= 2 ? 3 + r.nextInt(3) : r.nextInt(3);
        for (int i = 0; i < rolls; i++) {
            double x = r.nextDouble();
            ItemStack s;
            if (x < 0.30) s = it.almondWater();
            else if (x < 0.50) {
                s = it.ration();
                s.setAmount(1 + r.nextInt(tier >= 2 ? 3 : 2));
            } else if (x < 0.62) s = it.battery();
            else if (x < 0.72) s = it.note(level);
            else if (x < 0.78 && tier >= 2) s = it.flashlight(plugin.cfg().flashlightSeconds);
            else if (x < 0.86) s = new ItemStack(Material.STRING, 1 + r.nextInt(3));
            else if (x < 0.92) s = new ItemStack(Material.PAPER, 1 + r.nextInt(4));
            else s = new ItemStack(level == Level.LEVEL_5 ? Material.CANDLE : Material.TORCH, 1 + r.nextInt(2));
            inv.setItem(r.nextInt(inv.getSize()), s);
        }
    }
}
