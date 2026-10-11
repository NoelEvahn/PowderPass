package dev.powderpass.core;

import dev.powderpass.core.anomaly.Anomalies;
import dev.powderpass.core.climate.Climate;
import dev.powderpass.core.geo.BaseTerrain;
import dev.powderpass.core.geo.Geology;
import dev.powderpass.core.hydro.DomainSim;
import dev.powderpass.core.hydro.Domains;
import dev.powderpass.core.util.BoundedCache;
import dev.powderpass.core.volcano.Volcanoes;

/**
 * Entry point of the generation core: one instance per (world seed, generation settings). Thread-safe; all state is
 * immutable or held in bounded caches whose contents are pure functions of their keys.
 */
public final class PowderWorld {
    public final long seed;
    public final GenSettings settings;
    public final Geology geology;
    public final Climate climate;
    public final Volcanoes volcanoes;
    public final Anomalies anomalies;
    public final BaseTerrain base;
    public final Domains domains;
    private final BoundedCache<DomainSim> sims;
    private final boolean erosion;
    private volatile Object samplerHolder;

    public PowderWorld(long seed, GenSettings settings) {
        this(seed, settings, 256L << 20, true);
    }

    /**
     * @param simCacheBytes memory budget for cached domain simulations (each is roughly 1-3 MB)
     * @param erosion       false only for diagnostics (before/after comparisons)
     */
    public PowderWorld(long seed, GenSettings settings, long simCacheBytes, boolean erosion) {
        this.seed = seed;
        this.settings = settings.validated();
        this.erosion = erosion;
        this.geology = new Geology(seed, this.settings);
        this.climate = new Climate(seed, this.settings, geology);
        this.volcanoes = new Volcanoes(seed, this.settings, geology);
        this.anomalies = new Anomalies(seed, this.settings, geology, climate, volcanoes);
        this.base = new BaseTerrain(this.settings, geology, climate, volcanoes, anomalies);
        this.domains = new Domains(seed, this.settings, base, 16384);
        this.sims = new BoundedCache<>(simCacheBytes, DomainSim::memoryBytes);
    }

    /** Simulated domain (computed once, shared between threads; concurrent requesters wait for the first). */
    public DomainSim sim(long domainId) {
        return sims.getOrComputeShared(domainId, id -> DomainSim.build(domains, id, erosion));
    }

    public DomainSim simIfPresent(long domainId) {
        return sims.getIfPresent(domainId);
    }

    public BoundedCache<DomainSim> simCache() {
        return sims;
    }

    public boolean erosionEnabled() {
        return erosion;
    }

    @SuppressWarnings("unchecked")
    public <T> T attachment(java.util.function.Function<PowderWorld, T> factory) {
        Object h = samplerHolder;
        if (h == null) {
            synchronized (this) {
                h = samplerHolder;
                if (h == null) {
                    h = factory.apply(this);
                    samplerHolder = h;
                }
            }
        }
        return (T) h;
    }
}
