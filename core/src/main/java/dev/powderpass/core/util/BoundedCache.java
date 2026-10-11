package dev.powderpass.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

/**
 * Thread-safe, size-bounded cache keyed by long with approximate LRU eviction.
 *
 * <p>Two modes:
 * <ul>
 *   <li>{@link #getOrCompute} computes outside any lock; concurrent callers may compute duplicates, which is
 *       harmless because every value is a pure function of its key. Safe for recursive use.</li>
 *   <li>{@link #getOrComputeShared} de-duplicates in-flight computation (one thread computes, others wait).
 *       Must never be called recursively for the same cache.</li>
 * </ul>
 * Eviction never changes results: an evicted value is recomputed identically.
 */
public final class BoundedCache<V> {
    private static final class Entry<V> {
        final CompletableFuture<V> future;
        volatile long lastAccess;
        Entry(CompletableFuture<V> f, long t) { future = f; lastAccess = t; }
    }

    private final ConcurrentHashMap<Long, Entry<V>> map = new ConcurrentHashMap<>();
    private final AtomicLong clock = new AtomicLong();
    private final ToLongFunction<V> weigher;
    private final AtomicLong weight = new AtomicLong();
    private volatile long maxWeight;
    private final Object evictLock = new Object();
    private final AtomicLong hits = new AtomicLong(), misses = new AtomicLong(), evictions = new AtomicLong();

    public BoundedCache(long maxWeight, ToLongFunction<V> weigher) {
        this.maxWeight = Math.max(1, maxWeight);
        this.weigher = weigher;
    }

    public static <V> BoundedCache<V> ofCount(long maxEntries) {
        return new BoundedCache<>(maxEntries, v -> 1L);
    }

    public void setMaxWeight(long w) {
        this.maxWeight = Math.max(1, w);
        maybeEvict();
    }

    public V getIfPresent(long key) {
        Entry<V> e = map.get(Hash.mix(key));
        if (e == null || !e.future.isDone()) return null;
        e.lastAccess = clock.incrementAndGet();
        return e.future.getNow(null);
    }

    public V getOrCompute(long rawKey, LongFunction<V> fn) {
        long key = Hash.mix(rawKey); // packed (x,z) keys collide under Long.hashCode; SplitMix64 is a bijection
        Entry<V> e = map.get(key);
        if (e != null && e.future.isDone() && !e.future.isCompletedExceptionally()) {
            e.lastAccess = clock.incrementAndGet();
            hits.incrementAndGet();
            return e.future.join();
        }
        misses.incrementAndGet();
        V v = fn.apply(rawKey);
        Entry<V> fresh = new Entry<>(CompletableFuture.completedFuture(v), clock.incrementAndGet());
        Entry<V> prev = map.putIfAbsent(key, fresh);
        if (prev == null) {
            weight.addAndGet(weigher.applyAsLong(v));
            maybeEvict();
            return v;
        }
        if (prev.future.isDone() && !prev.future.isCompletedExceptionally()) {
            prev.lastAccess = clock.incrementAndGet();
            return prev.future.join();
        }
        return v;
    }

    public V getOrComputeShared(long rawKey, LongFunction<V> fn) {
        long key = Hash.mix(rawKey);
        while (true) {
            Entry<V> e = map.get(key);
            if (e != null) {
                e.lastAccess = clock.incrementAndGet();
                try {
                    V v = e.future.join();
                    hits.incrementAndGet();
                    return v;
                } catch (RuntimeException ex) {
                    map.remove(key, e);
                    throw ex;
                }
            }
            CompletableFuture<V> f = new CompletableFuture<>();
            Entry<V> mine = new Entry<>(f, clock.incrementAndGet());
            if (map.putIfAbsent(key, mine) != null) continue;
            misses.incrementAndGet();
            try {
                V v = fn.apply(rawKey);
                f.complete(v);
                weight.addAndGet(weigher.applyAsLong(v));
                maybeEvict();
                return v;
            } catch (Throwable t) {
                f.completeExceptionally(t);
                map.remove(key, mine);
                throw t;
            }
        }
    }

    private void maybeEvict() {
        if (weight.get() <= maxWeight) return;
        synchronized (evictLock) {
            if (weight.get() <= maxWeight) return;
            List<Map.Entry<Long, Entry<V>>> done = new ArrayList<>();
            for (Map.Entry<Long, Entry<V>> me : map.entrySet()) {
                if (me.getValue().future.isDone()) done.add(me);
            }
            done.sort((a, b) -> Long.compare(a.getValue().lastAccess, b.getValue().lastAccess));
            long target = (long) (maxWeight * 0.8);
            for (Map.Entry<Long, Entry<V>> me : done) {
                if (weight.get() <= target) break;
                if (map.remove(me.getKey(), me.getValue())) {
                    V v = me.getValue().future.getNow(null);
                    if (v != null) weight.addAndGet(-weigher.applyAsLong(v));
                    evictions.incrementAndGet();
                }
            }
        }
    }

    public void clear() {
        synchronized (evictLock) {
            map.clear();
            weight.set(0);
        }
    }

    public int size() { return map.size(); }
    public long weight() { return weight.get(); }
    public long maxWeight() { return maxWeight; }
    public long hits() { return hits.get(); }
    public long misses() { return misses.get(); }
    public long evictions() { return evictions.get(); }
}
