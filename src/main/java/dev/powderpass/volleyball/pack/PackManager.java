package dev.powderpass.volleyball.pack;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.config.Settings;
import dev.powderpass.volleyball.util.Msg;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Knows which players run the optional resource pack, so they get custom sounds while everyone else gets
 * vanilla ones. The client reports pack status for packs sent by the server (server.properties or this
 * plugin); players who installed the pack themselves can opt in with /vb pack on.
 */
public final class PackManager implements Listener {

    /** Stable id of the pack this plugin sends, so its status can be told apart from other packs. */
    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("powderpass:resource-pack".getBytes(StandardCharsets.UTF_8));

    private final PowderPassPlugin plugin;
    private final File file;
    private final Set<UUID> loaded = new HashSet<>();
    private final Set<UUID> forcedOn = new HashSet<>();
    private final Set<UUID> forcedOff = new HashSet<>();

    public PackManager(PowderPassPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pack-players.yml");
        load();
    }

    /** Whether {@code p} should receive resource-pack sounds. */
    public boolean has(Player p) {
        Settings.PackMode mode = plugin.settings().resourcePack.mode();
        if (mode == Settings.PackMode.EVERYONE) {
            return true;
        }
        if (mode == Settings.PackMode.NOBODY) {
            return false;
        }
        UUID id = p.getUniqueId();
        if (forcedOff.contains(id)) {
            return false;
        }
        return forcedOn.contains(id) || loaded.contains(id);
    }

    /** Detected through the client's pack status (not counting manual overrides). */
    public boolean detected(Player p) {
        return loaded.contains(p.getUniqueId());
    }

    public void setManual(Player p, Boolean on) {
        UUID id = p.getUniqueId();
        forcedOn.remove(id);
        forcedOff.remove(id);
        if (on != null) {
            (on ? forcedOn : forcedOff).add(id);
        }
        save();
    }

    public boolean canSend() {
        return !plugin.settings().resourcePack.url().isEmpty();
    }

    /** Offers the pack configured in resource-pack.url. */
    public boolean send(Player p) {
        Settings.ResourcePack cfg = plugin.settings().resourcePack;
        if (cfg.url().isEmpty()) {
            return false;
        }
        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(cfg.url()), cfg.sha1());
            ResourcePackRequest.Builder req = ResourcePackRequest.resourcePackRequest().packs(info).replace(false)
                    .required(cfg.required());
            if (!cfg.prompt().isBlank()) {
                req.prompt(Msg.mm(cfg.prompt()));
            }
            p.sendResourcePacks(req.build());
            return true;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid resource-pack.url / sha1: " + e.getMessage());
            return false;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (canSend()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (e.getPlayer().isOnline()) {
                    send(e.getPlayer());
                }
            }, 20L);
        }
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        // with our own url configured, only our pack counts; otherwise any server pack (server.properties)
        if (canSend() && !PACK_ID.equals(e.getID())) {
            return;
        }
        UUID id = e.getPlayer().getUniqueId();
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(id);
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> loaded.remove(id);
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        loaded.remove(e.getPlayer().getUniqueId());
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String s : y.getStringList("on")) {
            parse(s, forcedOn);
        }
        for (String s : y.getStringList("off")) {
            parse(s, forcedOff);
        }
    }

    private static void parse(String s, Set<UUID> into) {
        try {
            into.add(UUID.fromString(s));
        } catch (IllegalArgumentException ignored) {
            // skip malformed entries
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> on = new ArrayList<>();
        forcedOn.forEach(u -> on.add(u.toString()));
        List<String> off = new ArrayList<>();
        forcedOff.forEach(u -> off.add(u.toString()));
        y.set("on", on);
        y.set("off", off);
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save pack-players.yml", e);
        }
    }
}
