package dev.powderpass.mod;

import dev.powderpass.core.Diagnostics;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import dev.powderpass.core.util.Hash;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PowderPassMod implements ModInitializer {
    public static final String MOD_ID = "powderpass";
    public static final Logger LOGGER = LoggerFactory.getLogger("PowderPass");
    private static volatile PowderConfig config;

    public static PowderConfig config() {
        PowderConfig c = config;
        if (c == null) {
            synchronized (PowderPassMod.class) {
                if (config == null) config = PowderConfig.load();
                c = config;
            }
        }
        return c;
    }

    static void reloadConfig() {
        config = PowderConfig.load();
    }

    @Override
    public void onInitialize() {
        config();
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.fromNamespaceAndPath(MOD_ID, "powderpass"), PowderChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, Identifier.fromNamespaceAndPath(MOD_ID, "powderpass"), PowderBiomeSource.CODEC);

        // Bind the world context to the biome source as soon as a level exists (before spawn selection).
        ServerLevelEvents.LOAD.register((server, level) -> {
            if (level.getChunkSource().getGenerator() instanceof PowderChunkGenerator g) {
                g.context(level.getChunkSource().randomState());
                LOGGER.info("PowderPass generator active in {} with settings {}", level.dimension(), g.settings().toMap());
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(PowderPassMod::applySpawnOnce);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> PowderCommands.register(dispatcher));

        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            try {
                VolcanoRuntime.tick(level);
            } catch (RuntimeException e) {
                LOGGER.error("PowderPass volcano tick failed; volcano activity paused until restart", e);
                config().runtime.volcanoActivity = false;
            }
        });

        // Conservative player-modification tracking for volcano containment.
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> touch(world, pos));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            touch(world, hit.getBlockPos());
            touch(world, hit.getBlockPos().relative(hit.getDirection()));
            return InteractionResult.PASS;
        });
    }

    private static void touch(Level world, BlockPos pos) {
        if (world instanceof ServerLevel sl && sl.getChunkSource().getGenerator() instanceof PowderChunkGenerator) {
            VolcanoRuntime.data(sl).markTouched(Hash.pack(pos.getX() >> 4, pos.getZ() >> 4));
        }
    }

    /**
     * New worlds get a deterministic, survivable spawn: dry gentle land away from volcanoes and anomalies, with wood
     * and water nearby. Applied once per world through the vanilla /setworldspawn command.
     */
    private static void applySpawnOnce(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (!(level.getChunkSource().getGenerator() instanceof PowderChunkGenerator g)) return;
        VolcanoData data = VolcanoRuntime.data(level);
        if (data.spawnApplied) return;
        WorldContext ctx = g.context(level.getChunkSource().randomState());
        Diagnostics.Pos p = ctx.diagnostics.findSpawn();
        String cmd = "setworldspawn " + p.x() + " " + p.y() + " " + p.z();
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
            data.spawnApplied = true;
            data.setDirty();
            LOGGER.info("PowderPass spawn set to {} {} {} ({})", p.x(), p.y(), p.z(), p.note());
        } catch (RuntimeException e) {
            LOGGER.error("PowderPass could not set the world spawn", e);
        }
    }
}
