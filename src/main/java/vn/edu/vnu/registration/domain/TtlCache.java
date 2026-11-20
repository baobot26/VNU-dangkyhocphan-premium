package vn.edu.vnu.registration.domain;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

// In-memory read cache, values live ttlMs, ttlMs <= 0 turns it off
public final class TtlCache<K, V> {
    // Clear the whole map when a new key would pass this size
    static final int MAX_KEYS = 1000;

    private record Entry<V>(V value, long loadedAt) {}

    private final long ttlNanos;
    private final LongSupplier nanoTime;
    private final ConcurrentHashMap<K, Entry<V>> entries = new ConcurrentHashMap<>();

    public TtlCache(long ttlMs) {
        this(ttlMs, System::nanoTime);
    }

    // Tests pass a fake clock
    TtlCache(long ttlMs, LongSupplier nanoTime) {
        this.ttlNanos = ttlMs <= 0 ? 0 : TimeUnit.MILLISECONDS.toNanos(ttlMs);
        this.nanoTime = nanoTime;
    }

    public V get(K key, Supplier<V> loader) {
        if (ttlNanos == 0) return loader.get();
        long now = nanoTime.getAsLong();
        Entry<V> entry = entries.get(key);
        if (entry != null && now - entry.loadedAt() < ttlNanos) return entry.value();

        // Load outside the map so a slow query does not block other keys
        V value = loader.get();
        if (!entries.containsKey(key) && entries.size() >= MAX_KEYS) entries.clear();
        // Keep the time before the load so a value is never older than ttlMs
        entries.put(key, new Entry<>(value, now));
        return value;
    }
}
