package com.noisyfly.xuexitongnotifier.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ReconnectBackoffTest {
    @Test
    public void doublesDelayAndCapsAtThirtySeconds() {
        ReconnectBackoff backoff = new ReconnectBackoff();

        assertEquals(2_000L, backoff.nextDelayMillis());
        assertEquals(4_000L, backoff.nextDelayMillis());
        assertEquals(8_000L, backoff.nextDelayMillis());
        assertEquals(16_000L, backoff.nextDelayMillis());
        assertEquals(30_000L, backoff.nextDelayMillis());
        assertEquals(30_000L, backoff.nextDelayMillis());
    }

    @Test
    public void resetsAfterSuccessfulConnection() {
        ReconnectBackoff backoff = new ReconnectBackoff();
        backoff.nextDelayMillis();
        backoff.nextDelayMillis();

        backoff.reset();

        assertEquals(2_000L, backoff.nextDelayMillis());
    }
}

