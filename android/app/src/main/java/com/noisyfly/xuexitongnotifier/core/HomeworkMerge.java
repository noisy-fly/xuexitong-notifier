package com.noisyfly.xuexitongnotifier.core;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
public final class HomeworkMerge {
    private HomeworkMerge() {}
    public static JSONArray keepSelected(JSONArray items,Set<String> selected) throws JSONException {
        JSONArray filtered=new JSONArray();
        for(int i=0;i<items.length();i++) {
            JSONObject item=items.getJSONObject(i);
            if(selected.contains(item.optString("courseKey"))) filtered.put(item);
        }
        return filtered;
    }
    public static JSONArray merge(JSONArray fresh,JSONArray old,Set<String> failedCourses) throws JSONException {
        Map<String,JSONObject> previous=new HashMap<>();
        for(int i=0;i<old.length();i++) { JSONObject item=old.getJSONObject(i); previous.put(item.optString("id"),item); }
        JSONArray result=new JSONArray();
        java.util.Set<String> current=new java.util.HashSet<>();
        for(int i=0;i<fresh.length();i++) {
            JSONObject item=new JSONObject(fresh.getJSONObject(i).toString());
            current.add(item.optString("id"));
            JSONObject prior=previous.get(item.optString("id"));
            if(item.optBoolean("detailFailed") && prior!=null && prior.optLong("deadline")>0) {
                item.put("deadline",prior.optLong("deadline")); item.put("stale",true);
            }
            result.put(item);
        }
        for(int i=0;i<old.length();i++) {
            JSONObject item=old.getJSONObject(i);
            String course=item.optString("courseKey"); String kind=item.optString("kind","homework");
            if((failedCourses.contains(course) || failedCourses.contains(course+"|"+kind)) && !current.contains(item.optString("id"))) {
                JSONObject copy=new JSONObject(item.toString()); copy.put("stale",true); copy.put("statusStale",true); result.put(copy);
            }
        }
        return result;
    }
}
