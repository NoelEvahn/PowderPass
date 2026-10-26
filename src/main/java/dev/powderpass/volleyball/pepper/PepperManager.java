package dev.powderpass.volleyball.pepper;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.util.Msg;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pepper drill sessions. */
public final class PepperManager {

    private final PowderPassPlugin plugin;
    private final Map<UUID, PepperSession> byPlayer = new HashMap<>();
    private final List<PepperSession> sessions = new ArrayList<>();

    public PepperManager(PowderPassPlugin plugin) {
        this.plugin = plugin;
    }

    public PepperSession of(Player p) {
        PepperSession s = byPlayer.get(p.getUniqueId());
        return s == null || s.closed() ? null : s;
    }

    /** Starts a drill for {@code host} and invites nearby players. */
    public PepperSession start(Player host) {
        PepperSession existing = of(host);
        if (existing != null) {
            existing.feed(host);
            return existing;
        }
        PepperSession s = new PepperSession(plugin, host);
        sessions.add(s);
        byPlayer.put(host.getUniqueId(), s);
        s.feed(host);
        for (Player o : host.getWorld().getNearbyPlayers(host.getLocation(), 10)) {
            if (o != host && of(o) == null && plugin.matches().byPlayer(o) == null) {
                invite(host, o);
            }
        }
        return s;
    }

    public void invite(Player host, Player target) {
        target.sendMessage(Msg.mm("<gold>" + Msg.escape(host.getName()) + "</gold> <gray>is peppering.</gray> "
                + "<green><click:run_command:'/vb pepper join " + host.getName() + "'>[Join]</click></green>"));
    }

    public boolean join(Player p, Player host) {
        PepperSession s = of(host);
        if (s == null || plugin.matches().byPlayer(p) != null) {
            return false;
        }
        leave(p);
        s.add(p);
        byPlayer.put(p.getUniqueId(), s);
        for (Player o : s.online()) {
            Msg.send(o, "<gray>" + Msg.escape(p.getName()) + " joined the pepper (" + s.members().size() + ").");
        }
        return true;
    }

    public void leave(Player p) {
        if (p == null) {
            return;
        }
        PepperSession s = byPlayer.remove(p.getUniqueId());
        if (s != null) {
            s.remove(p);
        }
    }

    public void tick() {
        if (sessions.isEmpty()) {
            return;
        }
        for (PepperSession s : new ArrayList<>(sessions)) {
            if (!s.closed()) {
                s.tick();
            }
            if (s.closed()) {
                sessions.remove(s);
                byPlayer.values().removeIf(x -> x == s);
            }
        }
    }

    public void shutdown() {
        for (PepperSession s : sessions) {
            s.close();
        }
        sessions.clear();
        byPlayer.clear();
    }
}
