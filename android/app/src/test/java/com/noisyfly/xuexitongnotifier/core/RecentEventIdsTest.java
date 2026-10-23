package com.noisyfly.xuexitongnotifier.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RecentEventIdsTest {
    @Test
    public void rejectsDuplicatesAcrossReloads() {
        RecentEventIds ids = new RecentEventIds(3, "");

        assertTrue(ids.accept("event-1"));
        assertFalse(ids.accept("event-1"));

        RecentEventIds reloaded = new RecentEventIds(3, ids.serialize());
        assertFalse(reloaded.accept("event-1"));
        assertTrue(reloaded.accept("event-2"));
    }

    @Test
    public void forgetsOldestIdAtCapacity() {
        RecentEventIds ids = new RecentEventIds(2, "");
        ids.accept("event-1");
        ids.accept("event-2");
        ids.accept("event-3");

        assertTrue(ids.accept("event-1"));
    }

    @Test
    public void ignoresMalformedPersistedEntries() {
        RecentEventIds ids = new RecentEventIds(3, "event-1\n\n event-2 \n");

        assertFalse(ids.accept("event-1"));
        assertFalse(ids.accept("event-2"));
        assertFalse(ids.accept(""));
    }
}
