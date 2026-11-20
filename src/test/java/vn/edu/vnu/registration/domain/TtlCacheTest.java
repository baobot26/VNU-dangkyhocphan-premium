package vn.edu.vnu.registration.domain;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

// Unit tests for TtlCache with a fake clock
class TtlCacheTest {
    @Test void loadsOnceInsideTtlAndAgainAfterIt() {
        var now = new AtomicLong();
        var cache = new TtlCache<String, Integer>(1000, now::get);
        var loads = new AtomicInteger();

        assertThat(cache.get("k", loads::incrementAndGet)).isEqualTo(1);
        now.set(TimeUnit.MILLISECONDS.toNanos(999));
        // Inside the TTL, no new load
        assertThat(cache.get("k", loads::incrementAndGet)).isEqualTo(1);
        now.set(TimeUnit.MILLISECONDS.toNanos(1000));
        // TTL passed, load again
        assertThat(cache.get("k", loads::incrementAndGet)).isEqualTo(2);
        assertThat(loads).hasValue(2);
    }

    @Test void zeroTtlLoadsEveryTime() {
        var cache = new TtlCache<String, Integer>(0);
        var loads = new AtomicInteger();

        cache.get("k", loads::incrementAndGet);
        cache.get("k", loads::incrementAndGet);
        cache.get("k", loads::incrementAndGet);
        assertThat(loads).hasValue(3);
    }

    @Test void keysAreCachedSeparately() {
        var cache = new TtlCache<String, String>(1000, () -> 0);

        assertThat(cache.get("a", () -> "first")).isEqualTo("first");
        assertThat(cache.get("b", () -> "second")).isEqualTo("second");
        assertThat(cache.get("a", () -> "changed")).isEqualTo("first");
    }

    @Test void failedLoadIsNotCached() {
        var cache = new TtlCache<String, Integer>(1000, () -> 0);

        assertThatThrownBy(() -> cache.get("k", () -> { throw new IllegalStateException("db down"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(cache.get("k", () -> 7)).isEqualTo(7);
    }

    @Test void clearsWhenNewKeyGoesPastLimit() {
        var cache = new TtlCache<Integer, Integer>(1000, () -> 0);
        var loads = new AtomicInteger();

        for (int i = 0; i < TtlCache.MAX_KEYS; i++) cache.get(i, loads::incrementAndGet);
        // Full map still serves old keys
        cache.get(0, loads::incrementAndGet);
        assertThat(loads).hasValue(TtlCache.MAX_KEYS);

        // One more key clears the map, key 0 loads again
        cache.get(TtlCache.MAX_KEYS, loads::incrementAndGet);
        cache.get(0, loads::incrementAndGet);
        assertThat(loads).hasValue(TtlCache.MAX_KEYS + 2);
    }
}
