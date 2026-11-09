package com.noisyfly.xuexitongnotifier.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DeadlineTest {
    @Test public void formatsRemainingTimeInWholeMinutes() {
        assertEquals("距截止:1d2h3m", Deadline.formatRemaining(0, (26 * 60L + 3) * 60_000));
        assertEquals("距截止:0d0h1m", Deadline.formatRemaining(0, 1));
        assertEquals("已截止", Deadline.formatRemaining(1, 1));
    }
    @Test public void hidesOnlyKnownExpiredOrEndedItems() {
        assertTrue(Deadline.visible(101,"未完成",100));
        assertTrue(Deadline.visible(0,"截止时间未知",100));
        assertTrue(!Deadline.visible(100,"未完成",100));
        assertTrue(!Deadline.visible(0,"已截止",100));
        assertTrue(!Deadline.visible(0,"已结束，完成状态未知",100));
    }
    @Test public void ordersUpcomingBeforeOverdueAndUnknown() {
        assertTrue(Deadline.compare(200,50,100)<0);
        assertTrue(Deadline.compare(50,0,100)<0);
        assertTrue(Deadline.compare(120,200,100)<0);
    }
}
