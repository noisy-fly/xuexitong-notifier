package com.noisyfly.xuexitongnotifier.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class ReminderPolicyTest {
    @Test public void checksLatestRecordBeforeDelivering() throws Exception {
        JSONArray items=new JSONArray("[{\"id\":\"a\",\"courseKey\":\"course\",\"deadline\":1000,\"status\":\"未提交\"}]");
        assertTrue(ReminderPolicy.isCurrent(items,"a",1000,Collections.singleton("course"),100));
        items.getJSONObject(0).put("completed",true).put("status","已完成");
        assertFalse(ReminderPolicy.isCurrent(items,"a",1000,Collections.singleton("course"),100));
        assertFalse(ReminderPolicy.isCurrent(items,"missing",1000,Collections.singleton("course"),100));
        assertFalse(ReminderPolicy.isCurrent(items,"a",2000,Collections.singleton("course"),100));
    }
    @Test public void selectedAndRemindableAreRequired() throws Exception {
        JSONObject item=new JSONObject("{\"courseKey\":\"course\",\"deadline\":1000,\"remindable\":false}");
        assertFalse(ReminderPolicy.eligible(item,Collections.singleton("course"),100));
        item.put("remindable",true);
        assertFalse(ReminderPolicy.eligible(item,Collections.emptySet(),100));
        assertTrue(ReminderPolicy.eligible(item,Collections.singleton("course"),100));
        assertFalse(ReminderPolicy.eligible(item,Collections.singleton("course"),1000));
    }
}
