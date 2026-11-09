package com.noisyfly.xuexitongnotifier.core;

import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ReminderPolicy {
    private ReminderPolicy() {}
    public static boolean eligible(JSONObject item,Set<String> selected,long now) {
        return item!=null && !TaskState.staleStatus(item) && selected.contains(item.optString("courseKey"))
                && !item.optBoolean("completed",false) && item.optBoolean("remindable",true)
                && !HomeworkParser.isCompleted(item.optString("status")) && item.optLong("deadline",0)>now;
    }
    public static boolean isCurrent(JSONArray items,String id,long deadline,Set<String> selected,long now) {
        if(id==null || id.isEmpty()) return false;
        for(int i=0;i<items.length();i++) {
            JSONObject item=items.optJSONObject(i);
            if(item!=null && id.equals(item.optString("id")) && item.optLong("deadline",0)==deadline)
                return eligible(item,selected,now);
        }
        return false;
    }
}
