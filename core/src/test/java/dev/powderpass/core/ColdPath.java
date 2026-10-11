package dev.powderpass.core;

public final class ColdPath {
    public static void main(String[] a) {
        PowderWorld w = new PowderWorld(12345, GenSettings.defaults());
        long id = w.domains.owner(8, 8);
        long t = System.nanoTime();
        w.domains.info(id);
        System.out.printf("info %.0f ms%n", (System.nanoTime() - t) / 1e6); t = System.nanoTime();
        w.domains.key(id);
        System.out.printf("key %.0f ms (%s)%n", (System.nanoTime() - t) / 1e6, w.domains.cacheStats()); t = System.nanoTime();
        w.domains.flow(id);
        System.out.printf("flow %.0f ms (%s)%n", (System.nanoTime() - t) / 1e6, w.domains.cacheStats()); t = System.nanoTime();
        w.domains.inflows(id);
        System.out.printf("inflows+discharge %.0f ms (%s)%n", (System.nanoTime() - t) / 1e6, w.domains.cacheStats()); t = System.nanoTime();
        w.sim(id);
        System.out.printf("sim %.0f ms (%s)%n", (System.nanoTime() - t) / 1e6, w.domains.cacheStats());
    }
}
