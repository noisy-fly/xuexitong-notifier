package com.noisyfly.xuexitongnotifier.data;
import org.json.JSONArray;
import org.json.JSONException;
public final class ChapterCache {
    private ChapterCache() {}
    public static JSONArray all(HomeworkStore store) throws JSONException {
        JSONArray result=new JSONArray();
        for(String key:store.selectedCourses()) { JSONArray course=new JSONArray(store.prefs().getString("chapters_"+key,"[]")); for(int i=0;i<course.length();i++) result.put(course.getJSONObject(i)); }
        return result;
    }
    public static void append(JSONArray target,JSONArray items) { for(int i=0;i<items.length();i++) target.put(items.optJSONObject(i)); }
}
