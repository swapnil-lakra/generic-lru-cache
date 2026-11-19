package com.swapnil.cache.policy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LRUEvictionPolicyTest {
    private LRUEvictionPolicy<String> policy;

    @BeforeEach 
    void setUp(){
        policy = new LRUEvictionPolicy<>();
    }

    // ------------------------------------------------------------------
    // Victim selection — basic ordering
    // ------------------------------------------------------------------

    @Test 
    void emptyPolicyHasNoVictim(){
        assertThat(policy.selectVictim()).isNull();
        assertThat(policy.size()).isZero();
    }

    @Test
    void singleInsertReturnsItAsVictim(){
        policy.onInsert("a");

        assertThat(policy.selectVictim()).isEqualTo("a");
        assertThat(policy.size()).isEqualTo(1);
    }
    
    @Test 
    void multipleInsertsReturnOldestAsVictim(){
        policy.onInsert("a");
        policy.onInsert("b");
        policy.onInsert("c");
        // Order: c (MRU) -> b -> a (LRU)

        assertThat(policy.selectVictim()).isEqualTo("a");
    }

    // ------------------------------------------------------------------
    // Access promotion
    // ------------------------------------------------------------------

    @Test 
    void accessPromotesToMostRecentlyUsed(){
        policy.onInsert("a");
        policy.onInsert("b");
        policy.onInsert("c");
        // Order: c -> b -> a

        policy.onAccess("a");
        // Order: a -> c -> b

        assertThat(policy.selectVictim()).isEqualTo("b");
    }

    // ------------------------------------------------------------------
    // Removal — head / middle / tail
    // ------------------------------------------------------------------

    @Test 
    void removeMiddlePreservesOrder(){
        policy.onInsert("a");
        policy.onInsert("b");
        policy.onInsert("c");
        // Order: c -> b -> a

        policy.onRemove("b");
        // Order: c -> a

        assertThat(policy.selectVictim()).isEqualTo("a");
        assertThat(policy.size()).isEqualTo(2);
    }

    @Test 
    void removeHeadUpdatesMru(){
        policy.onInsert("a");
        policy.onInsert("b");
        policy.onInsert("c");
        // Order: c (MRU) -> b -> a (LRU)

        policy.onRemove("c");
        // Order: b -> a

        assertThat(policy.selectVictim()).isEqualTo("a");
    }

    @Test 
    void removeTailUpdatesLru(){
        policy.onInsert("a");
        policy.onInsert("b");
        policy.onInsert("c");
        // Order: c -> b -> a

        policy.onRemove("a");
        // Order: c -> b

        assertThat(policy.selectVictim()).isEqualTo("b");
    }

    @Test 
    void removeOnlyEntryLeavesEmptyPolicy(){
        policy.onInsert("a");
        policy.onRemove("a");

        assertThat(policy.selectVictim()).isNull();
        assertThat(policy.size()).isZero();
    }

    // ------------------------------------------------------------------
    // Contract violations — fail fast
    // ------------------------------------------------------------------

    @Test
    void duplicateInsertThrows(){
        policy.onInsert("a");

        assertThatThrownBy(() -> policy.onInsert("a"))
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("duplicate");
    }

    @Test 
    void accessOnMissingKeyThrows(){
        assertThatThrownBy(() -> policy.onAccess("nope"))
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("not tracked");
    }

    @Test 
    void removeOnMissingKeyThrows(){
        assertThatThrownBy(() -> policy.onRemove("nope"))
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("not tracked");
    }

    @Test 
    void nullKeysAreRejected(){
        assertThatThrownBy(() -> policy.onInsert(null))
              .isInstanceOf(NullPointerException.class);
        
        assertThatThrownBy(() -> policy.onAccess(null))
              .isInstanceOf(NullPointerException.class);
        
        assertThatThrownBy(() -> policy.onRemove(null))
              .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------
    // Peek-only guarantee
    // ------------------------------------------------------------------

    @Test 
    void selectVictimDoesNotMutateState(){
        policy.onInsert("a");
        policy.onInsert("b");

        assertThat(policy.selectVictim()).isEqualTo("a");
        assertThat(policy.selectVictim()).isEqualTo("a");
        assertThat(policy.size()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // End-to-end simulation of cache capacity=3 with LRU
    // ------------------------------------------------------------------

    @Test 
    void fullEvictionCycleSimulation(){
        policy.onInsert("a");
        policy.onInsert("b");
        policy.onInsert("c");

        // Access b -> order: b -> c -> a
        policy.onAccess("b");

        // New insert would evict a
        assertThat(policy.selectVictim()).isEqualTo("a");
        policy.onRemove("a");
        policy.onInsert("d");
        // Order: d -> b -> c

        // Access c -> order: c -> d -> b
        policy.onAccess("c");
        assertThat(policy.selectVictim()).isEqualTo("b");
        policy.onRemove("b");
        policy.onInsert("e");
        // Order: e -> c -> d

        assertThat(policy.size()).isEqualTo(3);
        assertThat(policy.selectVictim()).isEqualTo("d");
    }

    @Test 
    void reinsertAfterRemoveWorks(){
        policy.onInsert("a");
        policy.onRemove("a");
        policy.onInsert("a");

        assertThat(policy.selectVictim()).isEqualTo("a");
    }

    @Test
    void toStringShowsOrder(){
        policy.onInsert("a");
        policy.onInsert("b");
        policy.onInsert("c");

        assertThat(policy.toString())
              .startsWith("LRUEvictionPolicy{MRU->c -> b -> a<-LRU")
              .contains("size=3");
    }


}