package dev.powderpass.config;

import dev.powderpass.util.Text;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** MiniMessage strings from messages.yml, with the bundled file as fallback for missing keys. */
public final class Messages {

    private final Map<String, String> strings = new HashMap<>();
    private String prefix = "";

    public void load(JavaPlugin plugin) {
        strings.clear();
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read bundled messages.yml: " + e.getMessage());
        }
        YamlConfiguration user = YamlConfiguration.loadConfiguration(file);
        for (String key : defaults.getKeys(true)) {
            if (defaults.isString(key)) {
                strings.put(key, defaults.getString(key));
            }
        }
        for (String key : user.getKeys(true)) {
            if (user.isString(key)) {
                strings.put(key, user.getString(key));
            }
        }
        prefix = strings.getOrDefault("prefix", "");
    }

    public String raw(String key) {
        return strings.getOrDefault(key, "<red>missing message: " + key);
    }

    public Component get(String key, TagResolver... resolvers) {
        return Text.mm(raw(key), resolvers);
    }

    public Component prefixed(String key, TagResolver... resolvers) {
        return Text.mm(prefix + raw(key), resolvers);
    }

    public void send(Audience audience, String key, TagResolver... resolvers) {
        audience.sendMessage(prefixed(key, resolvers));
    }

    public void actionBar(Audience audience, String key, TagResolver... resolvers) {
        audience.sendActionBar(get(key, resolvers));
    }

    /** Placeholder with untrusted (escaped) text. */
    public static TagResolver p(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    /** Placeholder with a component. */
    public static TagResolver c(String name, Component value) {
        return Placeholder.component(name, value);
    }
}
