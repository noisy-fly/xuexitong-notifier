package com.noisyfly.xuexitongnotifier.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class TaskParser {
    private TaskParser() {}
    private static String stableId(String value) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder(); for(byte b:digest) out.append(String.format(Locale.ROOT,"%02x",b & 255)); return out.toString();
    }
    public static JSONArray activities(JSONObject payload,String course,String courseKey) throws Exception {
        Object data=payload.opt("data"); Object source=data instanceof JSONObject?((JSONObject)data).opt("activeList"):data;
        if(!(source instanceof JSONArray)) source=payload.opt("activeList");
        if(!(source instanceof JSONArray)) throw new JSONException("课堂活动列表格式无法识别");
        JSONArray list=(JSONArray)source, out=new JSONArray();
        for(int i=0;i<list.length();i++) {
            JSONObject item=list.optJSONObject(i); if(item==null) throw new JSONException("课堂活动条目格式异常");
            String id=item.optString("id",item.optString("activeId",""));
            String title=item.optString("nameOne",item.optString("name",item.optString("title",""))).trim();
            if(id.isEmpty() || title.isEmpty()) throw new JSONException("课堂活动缺少标识或标题");
            int state=item.optInt("status",-1);
            Object marker=item.opt("isFinish"); if(marker==null || marker==JSONObject.NULL) marker=item.opt("finished");
            String completion=String.valueOf(marker); boolean known=completion.equals("true")||completion.equals("false")||completion.equals("1")||completion.equals("0");
            boolean done=completion.equals("true")||completion.equals("1");
            String status=done?"已完成":state==1?(known?"进行中，未完成":"进行中，完成状态未知"):state==2?(known?"已结束，未完成":"已结束，完成状态未知"):"状态未知";
            long end=timestamp(item.opt("endTime"));
            JSONObject record=new JSONObject(); record.put("id",stableId(courseKey+":activity:"+id)); record.put("courseKey",courseKey);
            record.put("course",course); record.put("title",title); record.put("status",status); record.put("kind","activity");
            record.put("deadline",end); record.put("completed",done); record.put("remindable",!done && state==1); out.put(record);
        }
        return out;
    }
    public static JSONArray summaries(JSONObject payload,String course,String courseKey) throws Exception {
        JSONArray list=payload.optJSONArray("data");
        if(list==null) throw new JSONException("课程任务列表格式无法识别");
        JSONArray out=new JSONArray();
        for(int i=0;i<list.length();i++) {
            JSONObject item=list.optJSONObject(i); if(item==null) throw new JSONException("课程任务条目格式异常");
            String id=item.optString("id",""); String title=item.optString("name","").trim();
            if(id.isEmpty() || title.isEmpty()) throw new JSONException("课程任务缺少标识或标题");
            String qualification=item.optString("userTaskQualifyStatus",""); int count=item.optInt("planCount",0);
            boolean done=qualification.equals("已达标") || qualification.equals("已完成") || item.optDouble("taskStudyProgress",0)>=1 || (count>0 && item.optInt("planFinishCount",0)>=count);
            JSONObject record=new JSONObject(); record.put("id",stableId(courseKey+":task:"+id)); record.put("courseKey",courseKey);
            record.put("course",course); record.put("title",title); record.put("kind","task"); record.put("status",done?"已完成":"任务包，状态待核实");
            record.put("deadline",timestamp(item.opt("endDateStr"))); record.put("completed",done); record.put("remindable",false); record.put("detailIncomplete",!done); out.put(record);
        }
        return out;
    }
    public static JSONArray plans(JSONArray plans,String course,String courseKey,String taskId) throws Exception {
        JSONArray out=new JSONArray();
        for(int i=0;i<plans.length();i++) {
            JSONObject plan=plans.optJSONObject(i); if(plan==null) throw new JSONException("任务明细格式异常");
            String title=plan.optString("name","").trim(); String planId=plan.optString("planId","");
            if(title.isEmpty()) throw new JSONException("任务明细缺少标题");
            if(planId.isEmpty()) planId="title:"+title;
            Object marker=plan.opt("isFinish");
            if((marker==null || marker==JSONObject.NULL) && plan.optJSONObject("planUser")!=null) marker=plan.optJSONObject("planUser").opt("finish");
            String value=String.valueOf(marker); boolean known=value.equals("true")||value.equals("false")||value.equals("1")||value.equals("0");
            JSONObject score=plan.optJSONObject("score");
            boolean scoreDone=score!=null && (score.optBoolean("completed",false)||score.optInt("completed",0)==1);
            boolean done=value.equals("true")||value.equals("1")||scoreDone; if(scoreDone) known=true;
            long end=timestamp(plan.opt("endDateStr"));
            JSONObject record=new JSONObject(); record.put("id",stableId(courseKey+":task:"+taskId+":plan:"+planId)); record.put("courseKey",courseKey);
            record.put("course",course); record.put("title",title); record.put("kind","task"); record.put("status",done?"已完成":known?"未完成":"完成状态未知");
            record.put("deadline",end); record.put("completed",done); record.put("remindable",!done); out.put(record);
        }
        return out;
    }
    static long timestamp(Object value) {
        if(value==null || value==JSONObject.NULL) return 0;
        String text=String.valueOf(value).trim();
        try { if(text.matches("[0-9]{13}")) return Long.parseLong(text); if(text.matches("[0-9]{10}")) return Long.parseLong(text)*1000; } catch(NumberFormatException ignored) { }
        return HomeworkParser.deadline("截止时间："+text,System.currentTimeMillis());
    }
}
