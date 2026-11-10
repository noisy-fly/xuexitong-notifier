package com.noisyfly.xuexitongnotifier.core;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChapterParser {
    private ChapterParser() {}
    public static final class Section {
        public final String id,title; public final boolean completed;
        Section(String id,String title,boolean completed) { this.id=id; this.title=title; this.completed=completed; }
    }
    public static java.util.List<Section> sections(String html) {
        java.util.List<Section> result=new java.util.ArrayList<>(); java.util.Set<String> seen=new java.util.HashSet<>();
        Matcher tags=Pattern.compile("<div\\b([^>]*)>",Pattern.CASE_INSENSITIVE).matcher(html);
        while(tags.find()) {
            String attrs=tags.group(1); if(!HomeworkParser.hasClass(HomeworkParser.attribute(attrs,"class"),"chapter_item")) continue;
            String id=HomeworkParser.attribute(attrs,"id"),title=HomeworkParser.cleanText(HomeworkParser.attribute(attrs,"title"));
            if(!id.matches("cur[0-9]+")||title.isEmpty()||!seen.add(id)) continue;
            Matcher close=Pattern.compile("</li\\s*>",Pattern.CASE_INSENSITIVE).matcher(html); int end=close.find(tags.end())?close.start():tags.end();
            Matcher next=Pattern.compile("<div\\b([^>]*)>",Pattern.CASE_INSENSITIVE).matcher(html); next.region(tags.end(),end);
            while(next.find()) if(HomeworkParser.hasClass(HomeworkParser.attribute(next.group(1),"class"),"chapter_item")) { end=next.start(); break; }
            boolean completed=false; Matcher markers=Pattern.compile("<[^>]+>",Pattern.CASE_INSENSITIVE).matcher(html.substring(tags.end(),end));
            while(markers.find()) if(HomeworkParser.hasClass(HomeworkParser.attribute(markers.group(),"class"),"icon_yiwanc")) { completed=true; break; }
            result.add(new Section(id.substring(3),title,completed));
        }
        return result;
    }
    public static JSONArray cards(String html,String course,String courseKey,String chapterId,String chapterTitle,boolean chapterComplete) throws Exception {
        Matcher m=Pattern.compile("mArg\\s*=\\s*(\\{)").matcher(html);
        if(!m.find()) throw new IllegalArgumentException("章节卡片格式无法识别");
        Object value=new JSONTokener(html.substring(m.start(1))).nextValue();
        if(!(value instanceof JSONObject) || ((JSONObject)value).optJSONArray("attachments")==null) throw new IllegalArgumentException("章节任务数据不可用");
        JSONArray attachments=((JSONObject)value).getJSONArray("attachments"),out=new JSONArray();
        for(int i=0;i<attachments.length();i++) {
            JSONObject a=attachments.optJSONObject(i); if(a==null) continue; JSONObject prop=a.optJSONObject("property");
            String type=a.optString("type",""); if(!type.equalsIgnoreCase("workid") && (prop==null || (!prop.has("workid") && !prop.has("workId")))) continue;
            String workId=prop==null?"":prop.optString("workid",prop.optString("workId",""));
            if(workId.isEmpty() && a.optString("jobid","").matches("work-[0-9]+")) workId=a.optString("jobid").substring(5);
            if(workId.isEmpty()) continue;
            String title=(prop==null?chapterTitle:prop.optString("title",prop.optString("name",chapterTitle))).trim(); if(title.isEmpty()) title=chapterTitle;
            boolean known=a.has("isPassed") && !a.isNull("isPassed") && (a.opt("isPassed") instanceof Boolean);
            boolean completed=known?a.optBoolean("isPassed"):chapterComplete; known=known || chapterComplete;
            byte[] digest=MessageDigest.getInstance("SHA-256").digest((courseKey+":chapter:"+chapterId+":"+workId).getBytes(StandardCharsets.UTF_8)); StringBuilder id=new StringBuilder(); for(byte b:digest) id.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            JSONObject item=new JSONObject(); item.put("id",id.toString()); item.put("chapterId",chapterId); item.put("course",course); item.put("courseKey",courseKey); item.put("title",title); item.put("kind","chapter"); item.put("status",known?(completed?"已完成":"未完成"):"状态未知"); item.put("completed",completed);
            Object end=prop==null?null:prop.opt("endTime"); if(end==null||end==JSONObject.NULL) end=a.opt("endTime");
            long deadline=TaskParser.timestamp(end); item.put("deadline",deadline); item.put("remindable",!completed && deadline>0);
            int minutes=prop==null?0:prop.optInt("timeLimitMinutes",0); if(minutes>0 && minutes<=10080) item.put("timeLimitMinutes",minutes);
            out.put(item);
        }
        return out;
    }
}
