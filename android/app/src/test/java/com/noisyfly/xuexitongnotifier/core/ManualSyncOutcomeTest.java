package com.noisyfly.xuexitongnotifier.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class ManualSyncOutcomeTest {
    @Test public void classifiesResultTruthfully() {
        assertEquals("success",ManualSyncOutcome.classify(true,false,0));
        assertEquals("partial",ManualSyncOutcome.classify(true,false,2));
        assertEquals("verification",ManualSyncOutcome.classify(true,true,2));
        assertEquals("failure",ManualSyncOutcome.classify(false,false,0));
    }
}
