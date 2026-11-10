package com.noisyfly.xuexitongnotifier.core;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class TaskStateTest {
    @Test public void unknownCanBeOverriddenWithoutChangingKnownPlatformState() throws Exception {
        JSONObject unknown=new JSONObject().put("status","完成状态未知");
        JSONObject done=new JSONObject().put("status","待批阅").put("completed",true);
        JSONObject pending=new JSONObject().put("status","未完成");
        assertEquals(TaskState.UNKNOWN,TaskState.resolve(unknown,""));
        assertEquals(TaskState.DONE,TaskState.resolve(unknown,"done"));
        assertEquals(TaskState.PENDING,TaskState.resolve(unknown,"pending"));
        assertEquals(TaskState.DONE,TaskState.resolve(done,"pending"));
        assertEquals(TaskState.PENDING,TaskState.resolve(pending,"done"));
        assertEquals("done",TaskState.next(""));
        assertEquals("pending",TaskState.next("done"));
        assertEquals("done",TaskState.next("pending"));
    }
    @Test public void staleExamStatusIsUnknownUnlessManuallyMarked() throws Exception {
        JSONObject stale=new JSONObject().put("kind","exam").put("stale",true).put("status","未完成");
        assertEquals(TaskState.UNKNOWN,TaskState.resolve(stale,""));
        assertEquals(TaskState.DONE,TaskState.resolve(stale,"done"));
    }
    @Test public void unfinishedItemsSortAheadOfUnknownAndCompleted() {
        assertTrue(TaskState.rank(TaskState.PENDING)<TaskState.rank(TaskState.UNKNOWN));
        assertTrue(TaskState.rank(TaskState.UNKNOWN)<TaskState.rank(TaskState.DONE));
    }
}
