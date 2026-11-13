package com.swapnil.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheStatsTest {

    @Test 
    void emptyStatsHaveZeroCountsAndRatio(){
        CacheStats stats = new CacheStats(0, 0, 0, 0, 0, 0);

        assertThat(stats.requestCount()).isZero();
        assertThat(stats.loadCount()).isZero();
        assertThat(stats.hitCount()).isZero();
        assertThat(stats.missRatio()).isZero();
        assertThat(stats.loadExceptionRate()).isZero();
        assertThat(stats.averageLoadPenaltyNanos()).isZero();
    }

    @Test
    void hitAndMissRatiosAreComputedCorrectly(){
        CacheStats stats = new CacheStats(75, 25, 0, 0, 0, 0);

        assertThat(stats.requestCount()).isEqualTo(100);
        assertThat(stats.hitRatio()).isEqualTo(0.75);
        assertThat(stats.missRatio()).isEqualTo(0.25);
    }

    @Test
    void loadExceptionRateIsComputedCorrectly(){
        CacheStats stats = new CacheStats(0, 10, 0, 8, 2, 0);

        assertThat(stats.loadCount()).isEqualTo(10);
        assertThat(stats.loadExceptionRate()).isEqualTo(0.2);
    }

    @Test
    void averageLoadPenaltyIsComputedCorrectly(){
        CacheStats stats = new CacheStats(0, 4, 0, 4, 0, 8_000_000L);

        assertThat(stats.averageLoadPenaltyNanos()).isEqualTo(2_000_000.0);
    }

    @Test 
    void negativeValuesAreRejected(){
        assertThatThrownBy(() -> new CacheStats(-1, 0, 0, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new CacheStats(0, -1, 0, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new CacheStats(0, 0, -1, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new CacheStats(0, 0, 0, 0, 0, -1))
        .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalsAndHashCodeAreConsistent(){
        CacheStats a = new CacheStats(1, 2, 3, 4, 5, 6);
        CacheStats b = new CacheStats(1, 2, 3, 4, 5, 6);
        CacheStats c = new CacheStats(1, 2, 3, 4, 5, 7);

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameClassAs(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test 
    void toStringContainsKeyCounters(){
        CacheStats stats = new CacheStats(10, 5, 2, 3, 1, 1_000_000L);

        assertThat(stats.toString())
        .contains("hits=10")
        .contains("misses=5")
        .contains("evictions=2")
        .contains("loadSuccess=3")
        .contains("loadFailure=1")
        .contains("totalLoadNanos=1000000");
    }
}