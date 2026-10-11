package dev.powderpass.mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.powderpass.core.Diagnostics;
import dev.powderpass.core.anomaly.Anomaly;
import dev.powderpass.core.export.MapExport;
import dev.powderpass.core.volcano.VolcanoSite;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /powderpass ...} diagnostics (operator only). Locate commands evaluate deterministic metadata or bounded
 * column samples; they never generate chunks.
 */
final class PowderCommands {
    private PowderCommands() {}

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(literal("powderpass").requires(Perms::admin)
                .then(literal("inspect").executes(PowderCommands::inspect))
                .then(literal("settings").executes(PowderCommands::settings))
                .then(literal("cache").executes(PowderCommands::cache))
                .then(literal("reload").executes(c -> {
                    PowderPassMod.reloadConfig();
                    c.getSource().sendSuccess(() -> Component.literal("PowderPass runtime config reloaded (generation settings of existing worlds are unchanged)."), true);
                    return 1;
                }))
                .then(literal("locate")
                        .then(literal("volcano").executes(c -> locateVolcano(c, false))
                                .then(literal("active").executes(c -> locateVolcano(c, true))))
                        .then(literal("anomaly").then(argument("type", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (Anomaly.Type t : Anomaly.Type.values()) b.suggest(t.key);
                                    return b.buildFuture();
                                })
                                .executes(PowderCommands::locateAnomaly)))
                        .then(literal("landform").then(argument("type", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (Diagnostics.Landform l : Diagnostics.Landform.values()) b.suggest(l.name().toLowerCase(Locale.ROOT));
                                    return b.buildFuture();
                                })
                                .executes(PowderCommands::locateLandform))))
                .then(literal("volcano")
                        .then(literal("status").executes(c -> {
                            Vec3 p = c.getSource().getPosition();
                            for (String s : VolcanoRuntime.status(c.getSource().getLevel(), p.x, p.z)) send(c, s);
                            return 1;
                        }))
                        .then(literal("erupt").executes(c -> {
                            Vec3 p = c.getSource().getPosition();
                            send(c, VolcanoRuntime.trigger(c.getSource().getLevel(), p.x, p.z));
                            return 1;
                        }))
                        .then(literal("calm").executes(c -> {
                            Vec3 p = c.getSource().getPosition();
                            send(c, VolcanoRuntime.calm(c.getSource().getLevel(), p.x, p.z));
                            return 1;
                        })))
                .then(literal("export").then(argument("size", IntegerArgumentType.integer(256, 8192)).executes(PowderCommands::export))));
    }

    private static void send(CommandContext<CommandSourceStack> c, String s) {
        c.getSource().sendSuccess(() -> Component.literal(s), false);
    }

    private static WorldContext ctx(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        if (level.getChunkSource().getGenerator() instanceof PowderChunkGenerator g) return g.context(level.getChunkSource().randomState());
        send(c, "This dimension does not use the PowderPass generator.");
        return null;
    }

    private static int inspect(CommandContext<CommandSourceStack> c) {
        WorldContext ctx = ctx(c);
        if (ctx == null) return 0;
        Vec3 p = c.getSource().getPosition();
        for (String s : ctx.diagnostics.inspect(Math.floor(p.x), Math.floor(p.z))) send(c, s);
        return 1;
    }

    private static int settings(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        if (!(level.getChunkSource().getGenerator() instanceof PowderChunkGenerator g)) {
            send(c, "Not a PowderPass world.");
            return 0;
        }
        send(c, "Persisted generation settings (fixed for this world): " + g.settings().toMap());
        return 1;
    }

    private static int cache(CommandContext<CommandSourceStack> c) {
        WorldContext ctx = ctx(c);
        if (ctx == null) return 0;
        var sims = ctx.world.simCache();
        send(c, String.format(Locale.ROOT, "domain simulations: %d cached, %.1f/%.0f MB, hits %d misses %d evictions %d; %s",
                sims.size(), sims.weight() / 1048576.0, sims.maxWeight() / 1048576.0, sims.hits(), sims.misses(), sims.evictions(),
                ctx.world.domains.cacheStats()));
        return 1;
    }

    private static int locateVolcano(CommandContext<CommandSourceStack> c, boolean active) {
        WorldContext ctx = ctx(c);
        if (ctx == null) return 0;
        Vec3 p = c.getSource().getPosition();
        VolcanoSite v = ctx.diagnostics.nearestVolcano(p.x, p.z, active, 32000);
        if (v == null) send(c, "No volcano within 32000 blocks.");
        else send(c, String.format(Locale.ROOT, "%s %s volcano at %d %d (radius %d, %s). /tp @s %d ~ %d", v.origin(), v.type(),
                (int) v.x(), (int) v.z(), (int) v.radius(), v.canErupt() ? "eruption-capable" : "extinct", (int) v.x(), (int) v.z()));
        return 1;
    }

    private static int locateAnomaly(CommandContext<CommandSourceStack> c) {
        WorldContext ctx = ctx(c);
        if (ctx == null) return 0;
        Anomaly.Type t = Anomaly.Type.byKey(StringArgumentType.getString(c, "type"));
        if (t == null) {
            send(c, "Unknown anomaly type.");
            return 0;
        }
        Vec3 p = c.getSource().getPosition();
        Anomaly a = ctx.diagnostics.nearestAnomaly(p.x, p.z, t, 64000);
        if (a == null) send(c, "None within 64000 blocks.");
        else send(c, String.format(Locale.ROOT, "%s at %d %d (radius %d). /tp @s %d ~ %d", t.key, (int) a.x(), (int) a.z(), (int) a.radius(), (int) a.x(), (int) a.z()));
        return 1;
    }

    private static int locateLandform(CommandContext<CommandSourceStack> c) {
        WorldContext ctx = ctx(c);
        if (ctx == null) return 0;
        Diagnostics.Landform lf;
        try {
            lf = Diagnostics.Landform.valueOf(StringArgumentType.getString(c, "type").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            send(c, "Unknown landform.");
            return 0;
        }
        Vec3 p = c.getSource().getPosition();
        Diagnostics.Pos r = ctx.diagnostics.locate(lf, p.x, p.z, 12000);
        if (r == null) send(c, "Not found within the bounded search.");
        else send(c, String.format(Locale.ROOT, "%s at %d %d %d. /tp @s %d %d %d", r.note(), r.x(), r.y(), r.z(), r.x(), r.y(), r.z()));
        return 1;
    }

    /** Developer map export (runs off-thread; writes PNG layers and a report to config/powderpass-maps). */
    private static int export(CommandContext<CommandSourceStack> c) {
        WorldContext ctx = ctx(c);
        if (ctx == null) return 0;
        int size = IntegerArgumentType.getInteger(c, "size");
        Vec3 p = c.getSource().getPosition();
        File out = FabricLoader.getInstance().getConfigDir().resolve("powderpass-maps").toFile();
        Thread t = new Thread(() -> {
            try {
                MapExport.export(ctx.seed, ctx.world.settings, (int) p.x, (int) p.z, size, Math.max(1, size / 1024), out, true);
                PowderPassMod.LOGGER.info("PowderPass map export written to {}", out);
            } catch (Exception e) {
                PowderPassMod.LOGGER.error("PowderPass map export failed", e);
            }
        }, "powderpass-map-export");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
        send(c, "Exporting " + size + "x" + size + " diagnostic maps to " + out + " in the background.");
        return 1;
    }
}
