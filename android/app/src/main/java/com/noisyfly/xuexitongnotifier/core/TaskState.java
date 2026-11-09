package com.noisyfly.xuexitongnotifier.core;
import org.json.JSONObject;
public final class TaskState {
    public static final int UNKNOWN=0, DONE=1, PENDING=2;
    private TaskState() {}
    public static boolean staleStatus(JSONObject item) {
        return item.optBoolean("statusStale",false) || ("exam".equals(item.optString("kind")) && item.optBoolean("stale",false) && !item.optBoolean("detailFailed",false));
    }
    public static int resolve(JSONObject item,String override) {
        if(staleStatus(item)) { if("done".equals(override)) return DONE; if("pending".equals(override)) return PENDING; return UNKNOWN; }
        String status=item.optString("status","");
        if(item.optBoolean("completed",false) || HomeworkParser.isCompleted(status)) return DONE;
        if(status.isEmpty() || status.contains("未知") || status.contains("待核实")) {
            if("done".equals(override)) return DONE;
            if("pending".equals(override)) return PENDING;
            return UNKNOWN;
        }
        return PENDING;
    }
    public static boolean isUnknown(JSONObject item) { return resolve(item,"")==UNKNOWN; }
    public static String next(String current) { return "done".equals(current)?"pending":"done"; }
}
