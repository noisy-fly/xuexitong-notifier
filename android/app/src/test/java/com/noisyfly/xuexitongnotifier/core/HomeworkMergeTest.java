package com.noisyfly.xuexitongnotifier.core;
import org.json.JSONArray;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;
public class HomeworkMergeTest {
    @Test public void dropsUnselectedCoursesFromCachedReminders() throws Exception {
        JSONArray old=new JSONArray("[{\"id\":\"a\",\"courseKey\":\"one\"},{\"id\":\"b\",\"courseKey\":\"two\"}]");
        JSONArray filtered=HomeworkMerge.keepSelected(old,Collections.singleton("one"));
        assertEquals(1,filtered.length()); assertEquals("a",filtered.getJSONObject(0).getString("id"));
    }
    @Test public void retainsOnlyFailedExamSourceWhenHomeworkSucceeds() throws Exception {
        JSONArray old=new JSONArray("[{\"id\":\"h\",\"courseKey\":\"one\",\"kind\":\"homework\"},{\"id\":\"e\",\"courseKey\":\"one\",\"kind\":\"exam\"}]");
        JSONArray filtered=HomeworkMerge.merge(new JSONArray(),old,Collections.singleton("one|exam"));
        assertEquals(1,filtered.length()); assertEquals("e",filtered.getJSONObject(0).getString("id"));
        assertTrue(filtered.getJSONObject(0).getBoolean("stale"));
    }
    @Test public void keepsFailedCourseAndOldDeadlineWhenDetailsFail() throws Exception {
        JSONArray old=new JSONArray("[{\"id\":\"a\",\"courseKey\":\"ok\",\"deadline\":123},{\"id\":\"b\",\"courseKey\":\"bad\",\"deadline\":456},{\"id\":\"c\",\"courseKey\":\"ok\",\"deadline\":789}]");
        JSONArray fresh=new JSONArray("[{\"id\":\"a\",\"courseKey\":\"ok\",\"deadline\":0,\"detailFailed\":true}]");
        JSONArray merged=HomeworkMerge.merge(fresh,old,Collections.singleton("bad"));
        assertEquals(2,merged.length()); assertEquals(123,merged.getJSONObject(0).getLong("deadline"));
        assertTrue(merged.getJSONObject(0).getBoolean("stale"));
        assertEquals("b",merged.getJSONObject(1).getString("id")); assertTrue(merged.getJSONObject(1).getBoolean("stale"));
    }
}
