package com.swapnil.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CacheLoadExceptionTest {

    @Test 
    void storesKeyMessageAndCause() {
        Object key = new Object();
        Throwable cause = new RuntimeException("boom");

        CacheLoadException ex = new CacheLoadException(
            key, "load failed", cause);
        
        assertThat(ex.getKey()).isSameAs(key);
        assertThat(ex.getMessage()).isEqualTo("load failed");
        assertThat(ex.getCause()).isSameAs(cause);   
    }

    @Test 
    void extendsRuntimeException(){
        CacheLoadException ex = new CacheLoadException(
            "k", "m", null);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void nullCauseIsAccepted(){
        CacheLoadException ex = new CacheLoadException(
            "k", "m", null);
        assertThat(ex.getCause()).isNull();
    }
}