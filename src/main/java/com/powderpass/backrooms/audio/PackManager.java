package com.powderpass.backrooms.audio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Delivers the Java resource pack that lives inside the plugin JAR.
 * <ol>
 *   <li>On start the embedded {@code backrooms-pack.zip} is extracted to {@code plugins/Backrooms/resourcepack/}
 *   (again whenever the plugin version or the embedded pack changes), validated, and its SHA-1 computed.</li>
 *   <li>The download URL comes from config. The default is mc-packs.net, whose download URL is derived from the
 *   SHA-1 ({@code {sha1}} placeholder), so it stays correct across versions once the file is uploaded there.
 *   Optionally a small built-in HTTP server serves the file itself.</li>
 *   <li>The URL is checked asynchronously. The pack is only ever offered when the URL actually answers; otherwise the
 *   console explains exactly what to upload where. Nothing claims delivery works without a reachable URL.</li>
 *   <li>Java players get the pack on join. Each player's status is tracked; custom sounds are only sent to players
 *   whose client reported {@code SUCCESSFULLY_LOADED}. Clients cache packs by hash, so a re-join with the same SHA-1
 *   does not download again.</li>
 * </ol>
 */
public final class PackManager implements Listener {
    public enum UrlState { UNCHECKED, CHECKING, REACHABLE, UNREACHABLE, DISABLED }

    public static final String EMBEDDED = "backrooms-pack.zip";
    /** Bedrock manifest, icon and sound definitions; the audio itself is taken from the Java pack. */
    public static final String EMBEDDED_BEDROCK = "bedrock-skeleton.zip";

    private final BackroomsPlugin plugin;
    private final File dir;
    private final Map<UUID, PlayerResourcePackStatusEvent.Status> status = new ConcurrentHashMap<>();
    private volatile UrlState urlState = UrlState.UNCHECKED;
    private volatile String urlProblem = "";
    private String sha1 = "";
    private byte[] sha1Bytes = new byte[0];
    private UUID packId;
    private File packFile;
    private boolean valid;
    private String validation = "not loaded";
    private HttpServer server;
    private int soundEvents;

    public PackManager(BackroomsPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "resourcepack");
    }

    private BackroomsConfig cfg() {
        return plugin.cfg();
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        stopServer();
        prepare();
        if (!valid) {
            urlState = UrlState.DISABLED;
            return;
        }
        if (!cfg().packEnabled) {
            urlState = UrlState.DISABLED;
            urlProblem = "resource-pack.enabled is false";
            return;
        }
        if (cfg().packHostEnabled) startServer();
        checkUrl();
    }

    public void stop() {
        stopServer();
    }

    /** Extract (if needed), validate and hash the embedded pack. */
    void prepare() {
        valid = false;
        try {
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
            packFile = new File(dir, "Backrooms-pack.zip");
            File marker = new File(dir, "pack.version");
            String embeddedHash;
            try (InputStream in = plugin.getResource(EMBEDDED)) {
                if (in == null) {
                    validation = "no resource pack is embedded in this JAR";
                    plugin.getLogger().warning("[Audio] " + validation + "; custom audio is disabled.");
                    return;
                }
                embeddedHash = sha1(in.readAllBytes());
            }
            String want = plugin.getPluginMeta().getVersion() + " " + embeddedHash;
            String have = marker.exists() ? Files.readString(marker.toPath(), StandardCharsets.UTF_8).trim() : "";
            boolean intact = packFile.exists() && sha1(Files.readAllBytes(packFile.toPath())).equals(embeddedHash);
            if (!want.equals(have) || !intact) {
                try (InputStream in = plugin.getResource(EMBEDDED)) {
                    File tmp = new File(dir, "Backrooms-pack.zip.tmp");
                    Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Files.move(tmp.toPath(), packFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
                extractBedrock();
                Files.writeString(marker.toPath(), want, StandardCharsets.UTF_8);
                plugin.getLogger().info("[Audio] Extracted resource pack (" + (packFile.length() / 1024) + " KB) to " + packFile.getPath());
            }
            sha1 = embeddedHash;
            sha1Bytes = HexFormat.of().parseHex(sha1);
            packId = UUID.nameUUIDFromBytes(("backrooms-pack:" + sha1).getBytes(StandardCharsets.UTF_8));
            validation = validate(packFile);
            valid = validation.startsWith("ok");
            if (!valid) plugin.getLogger().warning("[Audio] Resource pack failed validation: " + validation);
        } catch (IOException ex) {
            validation = "error: " + ex.getMessage();
            plugin.getLogger().warning("[Audio] Could not prepare the resource pack: " + ex.getMessage());
        }
    }

    /**
     * Assembles the Bedrock companion pack: the embedded skeleton plus every Ogg file from the Java pack, moved from
     * {@code assets/backrooms/sounds/} to {@code sounds/backrooms/}. Done once per version, so the JAR carries the
     * audio only once.
     */
    private void extractBedrock() throws IOException {
        File out = new File(dir, "Backrooms-bedrock.mcpack");
        File tmp = new File(dir, "Backrooms-bedrock.mcpack.tmp");
        try (InputStream skel = plugin.getResource(EMBEDDED_BEDROCK)) {
            if (skel == null) return;
            try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(tmp.toPath()));
                 var skin = new java.util.zip.ZipInputStream(skel);
                 ZipFile java = new ZipFile(packFile)) {
                ZipEntry e;
                while ((e = skin.getNextEntry()) != null) {
                    zos.putNextEntry(new ZipEntry(e.getName()));
                    skin.transferTo(zos);
                    zos.closeEntry();
                }
                String prefix = "assets/backrooms/sounds/";
                var entries = java.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry je = entries.nextElement();
                    if (!je.getName().startsWith(prefix) || !je.getName().endsWith(".ogg")) continue;
                    zos.putNextEntry(new ZipEntry("sounds/backrooms/" + je.getName().substring(prefix.length())));
                    try (InputStream in = java.getInputStream(je)) {
                        in.transferTo(zos);
                    }
                    zos.closeEntry();
                }
            }
        }
        Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Structural validation: metadata present, every sounds.json reference exists as an .ogg in the zip. */
    String validate(File zip) {
        try (ZipFile z = new ZipFile(zip)) {
            if (z.getEntry("pack.mcmeta") == null) return "missing pack.mcmeta";
            ZipEntry sj = z.getEntry("assets/backrooms/sounds.json");
            if (sj == null) return "missing assets/backrooms/sounds.json";
            JsonObject sounds;
            try (InputStream in = z.getInputStream(sj)) {
                sounds = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
            Set<String> missing = new HashSet<>();
            int files = 0;
            for (Map.Entry<String, JsonElement> e : sounds.entrySet()) {
                for (JsonElement s : e.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                    String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
                    String ns = name.contains(":") ? name.substring(0, name.indexOf(':')) : "minecraft";
                    String path = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
                    if (!ns.equals("backrooms")) continue; // a reference to a vanilla sound is fine
                    if (z.getEntry("assets/backrooms/sounds/" + path + ".ogg") == null) missing.add(path);
                    files++;
                }
            }
            soundEvents = sounds.size();
            if (!missing.isEmpty()) return "missing " + missing.size() + " sound files, e.g. " + missing.iterator().next();
            return "ok: " + sounds.size() + " sound events, " + files + " file references";
        } catch (IOException | RuntimeException ex) {
            return "unreadable zip: " + ex.getMessage();
        }
    }

    // ------------------------------------------------------------------ URL

    /** The configured URL with {sha1} filled in, or the built-in server's URL. */
    public String url() {
        if (cfg().packHostEnabled) {
            return "http://" + cfg().packHostPublicAddress + ":" + cfg().packHostPort + "/Backrooms-" + sha1 + ".zip";
        }
        return cfg().packUrl.replace("{sha1}", sha1);
    }

    public void checkUrl() {
        if (!valid) return;
        String u = url();
        if (u.isBlank() || u.contains("{")) {
            urlState = UrlState.UNREACHABLE;
            urlProblem = "no URL configured";
            instructions();
            return;
        }
        urlState = UrlState.CHECKING;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String problem = probe(u);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (problem == null) {
                    urlState = UrlState.REACHABLE;
                    urlProblem = "";
                    plugin.getLogger().info("[Audio] Resource pack URL is reachable: " + u);
                    for (Player p : plugin.getServer().getOnlinePlayers()) offer(p);
                } else {
                    urlState = UrlState.UNREACHABLE;
                    urlProblem = problem;
                    instructions();
                }
            });
        });
    }

    /** HEAD (then a ranged GET) the URL; returns null when it serves a file, else the reason. */
    private String probe(String u) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<Void> head = client.send(HttpRequest.newBuilder(URI.create(u)).method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.discarding());
            if (head.statusCode() / 100 == 2) return null;
            HttpResponse<Void> get = client.send(HttpRequest.newBuilder(URI.create(u)).header("Range", "bytes=0-1")
                    .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.discarding());
            return get.statusCode() / 100 == 2 ? null : "HTTP " + get.statusCode();
        } catch (IOException | IllegalArgumentException ex) {
            return ex.getClass().getSimpleName() + ": " + ex.getMessage();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
    }

    private void instructions() {
        var log = plugin.getLogger();
        log.warning("[Audio] The resource pack is NOT being sent: " + url() + " -> " + urlProblem);
        log.warning("[Audio] Players still hear the vanilla fallback sounds. To enable custom audio:");
        log.warning("[Audio]   1. Upload " + packFile.getPath() + " to https://mc-packs.net (free, keeps files by SHA-1).");
        log.warning("[Audio]      Its download link will be https://download.mc-packs.net/pack/" + sha1 + ".zip");
        log.warning("[Audio]      which is exactly what resource-pack.url resolves to by default.");
        log.warning("[Audio]   2. Or host the file on any HTTPS server and set resource-pack.url (use {sha1} if you like).");
        log.warning("[Audio]   3. Or enable resource-pack.host if this machine has a public port for it.");
        log.warning("[Audio]   Then run /backrooms audio reload. SHA-1: " + sha1);
    }

    // ------------------------------------------------------------------ players

    public boolean deliverable() {
        return valid && urlState == UrlState.REACHABLE;
    }

    /** Offer the pack to a Java player (no-op for Bedrock players and when the URL is not known to work). */
    public void offer(Player p) {
        if (!deliverable() || Bedrock.is(p)) return;
        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(packId, URI.create(url()), sha1);
            p.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .required(cfg().packRequired)
                    .replace(false)
                    .prompt(BackroomsConfig.mm(cfg().packPrompt))
                    .build());
            status.put(p.getUniqueId(), PlayerResourcePackStatusEvent.Status.ACCEPTED);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[Audio] Could not send the resource pack to " + p.getName() + ": " + ex);
        }
    }

    /**
     * May this player enter the Backrooms? With custom blocks on and {@code required-for-entry}, Java players need
     * the pack loaded. Never blocks anyone while the pack cannot be delivered (no reachable URL): then everybody
     * would be locked out, so the console warns instead. Bedrock players cannot load the Java pack at all and are
     * let in when {@code bedrock.allow-entry} is on.
     */
    public boolean mayEnter(Player p) {
        if (!cfg().customBlocks || !cfg().packRequiredForEntry || !deliverable()) return true;
        if (Bedrock.is(p)) return cfg().bedrockEntry;
        return loaded(p);
    }

    /** Is the custom pack loaded on this player's client? */
    public boolean loaded(Player p) {
        if (Bedrock.is(p)) return cfg().bedrockCustomSounds;
        return status.get(p.getUniqueId()) == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED;
    }

    public PlayerResourcePackStatusEvent.Status status(Player p) {
        return status.get(p.getUniqueId());
    }

    /** For tests and the audio test command: mark a player's pack state. */
    public void markLoaded(Player p, boolean loaded) {
        if (loaded) status.put(p.getUniqueId(), PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
        else status.remove(p.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) offer(p);
        }, 20L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        if (packId != null && !packId.equals(e.getID())) return;
        status.put(e.getPlayer().getUniqueId(), e.getStatus());
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> plugin.debug("[Audio] " + e.getPlayer().getName() + " loaded the resource pack");
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED ->
                    plugin.getLogger().info("[Audio] " + e.getPlayer().getName() + ": resource pack " + e.getStatus()
                            + " (vanilla fallback sounds will be used)");
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        status.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ optional built-in host

    private void startServer() {
        if (packFile == null || !packFile.exists()) return;
        String path = "/Backrooms-" + sha1 + ".zip";
        try {
            server = HttpServer.create(new InetSocketAddress(cfg().packHostBind, cfg().packHostPort), 16);
            server.createContext("/", ex -> {
                try (ex) {
                    String method = ex.getRequestMethod();
                    if (!ex.getRequestURI().getPath().equals(path) || !(method.equals("GET") || method.equals("HEAD"))) {
                        ex.sendResponseHeaders(404, -1);
                        return;
                    }
                    ex.getResponseHeaders().add("Content-Type", "application/zip");
                    ex.getResponseHeaders().add("Cache-Control", "public, max-age=31536000, immutable");
                    long len = packFile.length();
                    if (method.equals("HEAD")) {
                        ex.getResponseHeaders().add("Content-Length", Long.toString(len));
                        ex.sendResponseHeaders(200, -1);
                        return;
                    }
                    ex.sendResponseHeaders(200, len);
                    try (OutputStream out = ex.getResponseBody()) {
                        Files.copy(packFile.toPath(), out);
                    }
                }
            });
            server.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "Backrooms-pack-http");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            plugin.getLogger().info("[Audio] Serving the resource pack on " + cfg().packHostBind + ":" + cfg().packHostPort + path
                    + " (only this one file). Make sure the port is reachable from the internet.");
        } catch (IOException | RuntimeException ex) {
            server = null;
            plugin.getLogger().warning("[Audio] Built-in pack host could not start on port " + cfg().packHostPort + ": " + ex.getMessage());
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    // ------------------------------------------------------------------ status

    public String describe() {
        return "pack " + (valid ? "valid" : "INVALID") + " (" + validation + "), sha1 " + (sha1.isEmpty() ? "-" : sha1)
                + ", url " + (valid ? url() : "-") + " [" + urlState + (urlProblem.isEmpty() ? "" : ": " + urlProblem) + "]"
                + (server != null ? ", built-in host running" : "");
    }

    public UrlState urlState() {
        return urlState;
    }

    public UUID packId() {
        return packId;
    }

    public String sha1() {
        return sha1;
    }

    public boolean valid() {
        return valid;
    }

    public int soundEvents() {
        return soundEvents;
    }

    public File packFile() {
        return packFile;
    }

    static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
