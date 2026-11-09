package com.noisyfly.xuexitongnotifier.core;

import java.net.URLDecoder;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HomeworkParser {
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");
    private HomeworkParser() {}
    public static final class Course {
        public final String id, clazz, cpi, name;
        Course(String id, String clazz, String cpi, String name) { this.id=id; this.clazz=clazz; this.cpi=cpi; this.name=name; }
        public String key() { return id+":"+clazz+":"+cpi; }
    }
    public static final class Item {
        public final String url, title, status;
        public final long remainingMillis;
        public final boolean completed;
        Item(String url, String title, String status,long remainingMillis) { this.url=url; this.title=title; this.status=status; this.remainingMillis=remainingMillis; this.completed=isCompleted(status); }
    }
    public static List<Course> courses(String html) {
        Matcher m = Pattern.compile("stucoursemiddle\\?courseid=(\\d+)&(?:amp;)?clazzid=(\\d+)&(?:amp;)?cpi=(\\d+)").matcher(html);
        List<Course> out = new ArrayList<>(); Set<String> seen = new HashSet<>();
        while (m.find()) {
            if (!seen.add(m.group(1)+":"+m.group(2)+":"+m.group(3))) continue;
            String segment = html.substring(m.end(), Math.min(html.length(), m.end()+2500));
            Matcher name = Pattern.compile("course-name[^>]*title=\"([^\"]+)\"").matcher(segment);
            out.add(new Course(m.group(1), m.group(2), m.group(3), name.find() ? decode(name.group(1)) : "未知课程"));
        }
        return out;
    }
    public static List<Item> pending(String html) {
        List<Item> out=new ArrayList<>(); for(Item item:all(html)) if(!item.completed) out.add(item); return out;
    }
    public static boolean isCompleted(String status) {
        return status.contains("已提交") || status.contains("已完成") || status.contains("待批阅") || status.contains("待批閱") || status.contains("待批改") || status.contains("已批阅") || status.contains("已批閱") || status.contains("已批改") || status.contains("已互评") || status.contains("已互評");
    }
    public static List<Item> all(String html) {
        List<Item> out = new ArrayList<>();
        Matcher rows=Pattern.compile("<li\\b([^>]*)>([\\s\\S]*?)</li\\s*>",Pattern.CASE_INSENSITIVE).matcher(html);
        while(rows.find()) {
            String attrs=rows.group(1), body=rows.group(2);
            String rawUrl=attribute(attrs,"data"), title=null, status=null;
            Matcher paragraphs=Pattern.compile("<p\\b([^>]*)>([\\s\\S]*?)</p\\s*>",Pattern.CASE_INSENSITIVE).matcher(body);
            while(paragraphs.find()) {
                String classes=attribute(paragraphs.group(1),"class");
                if(hasClass(classes,"overHidden2")) {
                    title=cleanText(paragraphs.group(2));
                    if(title.isEmpty()) title=decode(attribute(paragraphs.group(1),"title")).trim();
                }
                if(hasClass(classes,"status")) status=cleanText(paragraphs.group(2));
            }
            if(title==null && !attrs.contains("goTask")) continue;
            if(title==null || title.isEmpty() || status==null)
                throw new IllegalArgumentException("作业列表格式无法识别，请稍后重试");
            String url;
            try { url=URLDecoder.decode(decode(rawUrl).replace("+","%2B"),"UTF-8"); } catch(Exception e) { url=""; }
            out.add(new Item(url,title,status,remaining(body)));
        }
        return out;
    }
    private static long remaining(String body) {
        Matcher div=Pattern.compile("<div\\b([^>]*)>",Pattern.CASE_INSENSITIVE).matcher(body);
        while(div.find()) {
            String classes=attribute(div.group(1),"class");
            if(!hasClass(classes,"time") || !hasClass(classes,"notOver")) continue;
            Matcher end=Pattern.compile("</div\\s*>",Pattern.CASE_INSENSITIVE).matcher(body);
            if(!end.find(div.end())) return -1;
            String text=cleanText(body.substring(div.end(),end.start()));
            Matcher m=Pattern.compile("剩余\\s*(?:(\\d+)\\s*天)?\\s*(?:(\\d+)\\s*小时)?\\s*(?:(\\d+)\\s*分钟)?").matcher(text);
            if(!m.find() || (m.group(1)==null && m.group(2)==null && m.group(3)==null)) return -1;
            try {
                long days=m.group(1)==null?0:Long.parseLong(m.group(1));
                long hours=m.group(2)==null?0:Long.parseLong(m.group(2));
                long minutes=m.group(3)==null?0:Long.parseLong(m.group(3));
                if(days>3650 || hours>87600 || minutes>5256000) return -1;
                return (days*1440+hours*60+minutes)*60_000;
            } catch(NumberFormatException e) { return -1; }
        }
        return -1;
    }
    static boolean hasClass(String classes,String name) {
        for(String value:classes.split("\\s+")) if(value.equals(name)) return true;
        return false;
    }
    static String attribute(String attrs,String name) {
        Matcher m=Pattern.compile("(?:^|\\s)"+Pattern.quote(name)+"\\s*=\\s*([\"'])(.*?)\\1",Pattern.CASE_INSENSITIVE).matcher(attrs);
        return m.find()?m.group(2):"";
    }
    static String cleanText(String html) {
        return decode(html.replaceAll("(?is)<script\\b[^>]*>.*?</script>|<style\\b[^>]*>.*?</style>"," ").replaceAll("<[^>]+>"," ")).replace('\u00a0',' ').replaceAll("\\s+"," ").trim();
    }
    public static int pageCount(String html) {
        Matcher div=Pattern.compile("<div\\b([^>]*)>",Pattern.CASE_INSENSITIVE).matcher(html);
        while(div.find()) if(attribute(div.group(1),"id").equals("page")) {
            Matcher end=Pattern.compile("</div\\s*>",Pattern.CASE_INSENSITIVE).matcher(html);
            if(!end.find(div.end())) throw new IllegalArgumentException("作业分页格式异常");
            Matcher page=Pattern.compile("<li\\b[^>]*>\\s*(\\d+)\\s*</li\\s*>",Pattern.CASE_INSENSITIVE).matcher(html.substring(div.end(),end.start()));
            int max=1;
            while(page.find()) { int number=Integer.parseInt(page.group(1)); if(number>100) throw new IllegalArgumentException("作业分页数量异常"); max=Math.max(max,number); }
            return max;
        }
        return 1;
    }
    public static long deadline(String html, long now) { return deadline(html,now,""); }
    public static long deadline(String html, long now,String status) {
        String text=cleanText(html);
        String date="(?:(\\d{4})[-/年])?(\\d{1,2})[-/月](\\d{1,2})(?:日)?\\s+(\\d{1,2}):(\\d{2})";
        Matcher direct=Pattern.compile("(?:截止时间|结束时间)\\s*[:：]\\s*"+date).matcher(text);
        boolean peer=status.contains("互评") || status.contains("互評");
        String rangeLabel=peer?"(?:互评时间|互評時間)":"作答时间";
        Matcher range=Pattern.compile(rangeLabel+"\\s*[:：]\\s*(?:\\d{4}[-/年])?\\d{1,2}[-/月]\\d{1,2}(?:日)?\\s+\\d{1,2}:\\d{2}\\s*(?:至|~|～|—)\\s*"+date).matcher(text);
        Matcher m=peer?(range.find()?range:null):(direct.find()?direct:(range.find()?range:null));
        if(m==null) return 0;
        int current=ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now),CHINA).getYear();
        int start=m.group(1)==null?current-1:Integer.parseInt(m.group(1));
        int end=m.group(1)==null?current+1:start;
        long best=0,distance=Long.MAX_VALUE;
        for(int y=start;y<=end;y++) try {
            long value=LocalDateTime.of(y,Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)),Integer.parseInt(m.group(4)),Integer.parseInt(m.group(5))).atZone(CHINA).toInstant().toEpochMilli();
            long d=Math.abs(value-now); if(d<distance) { distance=d; best=value; }
        } catch(RuntimeException ignored) { }
        return best;
    }
    public static String hidden(String html, String id) {
        Matcher tag=Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        while(tag.find()) {
            String t=tag.group();
            if (!Pattern.compile("\\bid=[\"']"+Pattern.quote(id)+"[\"']").matcher(t).find()) continue;
            Matcher v=Pattern.compile("\\bvalue=[\"']([^\"']*)[\"']").matcher(t);
            if (v.find()) return decode(v.group(1));
        }
        return "";
    }
    private static String decode(String s) {
        String text=s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
        Matcher m=Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);").matcher(text); StringBuffer out=new StringBuffer();
        while(m.find()) { String replacement=m.group(); try { String n=m.group(1); int cp=n.startsWith("x")?Integer.parseInt(n.substring(1),16):Integer.parseInt(n); replacement=new String(Character.toChars(cp)); } catch(RuntimeException ignored) { } m.appendReplacement(out,Matcher.quoteReplacement(replacement)); }
        m.appendTail(out); return out.toString();
    }
}
