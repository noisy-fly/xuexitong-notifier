package com.noisyfly.xuexitongnotifier.core;

public final class ReconnectBackoff {
    private static final long INITIAL_DELAY_MILLIS = 2_000L;
    private static final long MAX_DELAY_MILLIS = 30_000L;

    private long nextDelayMillis = INITIAL_DELAY_MILLIS;

    public long nextDelayMillis() {
        long current = nextDelayMillis;
        nextDelayMillis = Math.min(current * 2L, MAX_DELAY_MILLIS);
        return current;
    }

    public void reset() {
        nextDelayMillis = INITIAL_DELAY_MILLIS;
    }
}
