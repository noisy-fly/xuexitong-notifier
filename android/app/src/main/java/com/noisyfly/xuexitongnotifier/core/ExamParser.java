package com.noisyfly.xuexitongnotifier.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads list metadata only; never opens an exam or submits answers. */
public final class ExamParser {
    private ExamParser() {}
    public static final class Exam {
        public final String id,title,status;
        public final long deadline;
        public final boolean completed,estimated;
        Exam(String id,String title,String status,long deadline,boolean estimated) { this.id=id; this.title=title; this.status=status; this.deadline=deadline; this.completed=completed(status); this.estimated=estimated; }
    }
    public static List<Exam> parse(String html,long now) {
        List<Exam> result=new ArrayList<>();
        Matcher clicks=Pattern.compile("<(?:div|a)\\b([^>]*)>",Pattern.CASE_INSENSITIVE).matcher(html);
        while(clicks.find()) {
            String action=HomeworkParser.attribute(clicks.group(1),"onclick");
            Matcher call=Pattern.compile("goTest\\s*\\(([^)]*)\\)",Pattern.CASE_INSENSITIVE).matcher(action);
            if(!call.find()) continue;
            String[] args=call.group(1).split(",",-1);
            if(args.length<4) throw new IllegalArgumentException("考试列表格式无法识别");
            String id=unquote(args[1]);
            if(!id.matches("[0-9]+")) throw new IllegalArgumentException("考试编号无法识别");
            String block=container(html,clicks.start());
            if(block==null) throw new IllegalArgumentException("考试条目格式无法识别");
            String title=textByClass(block,"overHidden2","exam-title","title");
            String status=textByClass(block,"status","state","tip");
            if(title.isEmpty()) throw new IllegalArgumentException("考试标题无法识别");
            String raw=unquote(args[3]); long deadline=0;
            if(raw.matches("[0-9]{13}")) { try { deadline=Long.parseLong(raw); } catch(NumberFormatException ignored) {} }
            else if(raw.matches("[0-9]{10}")) { try { deadline=Long.parseLong(raw)*1000; } catch(NumberFormatException ignored) {} }
            else if(!raw.isEmpty()) deadline=HomeworkParser.deadline("截止时间："+raw,now);
            long remaining=HomeworkParser.remaining(block); boolean estimated=deadline==0 && remaining>=0;
            if(estimated) deadline=now+Math.max(60_000,remaining);
            result.add(new Exam(id,title,status,deadline,estimated));
        }
        Matcher rows=Pattern.compile("(?is)<li\\b[^>]*>(.*?)</li\\s*>").matcher(html);
        while(rows.find()) {
            String row=rows.group(1);
            if(row.contains("goTest")) continue;
            String title=textByClass(row,"overHidden2","exam-title");
            String status=textByClass(row,"status","state","tip");
            if(title.isEmpty() || status.isEmpty()) continue;
            long deadline=HomeworkParser.deadline(row,now);
            long remaining=HomeworkParser.remaining(row); boolean estimated=deadline==0 && remaining>=0;
            if(estimated) deadline=now+Math.max(60_000,remaining);
            result.add(new Exam("title:"+title,title,status,deadline,estimated));
        }
        return result;
    }
    private static boolean completed(String status) {
        return status.contains("已提交") || status.contains("已完成") || status.contains("已交卷") || status.contains("待批阅") || status.contains("待批閱") || status.contains("已批阅") || status.contains("已批閱") || status.contains("已批改");
    }
    private static String unquote(String input) { return input.trim().replaceAll("^[\\\"']|[\\\"']$","").trim(); }
    private static String container(String html,int position) {
        String best=null;
        Matcher tags=Pattern.compile("<(li|div)\\b([^>]*)>",Pattern.CASE_INSENSITIVE).matcher(html);
        while(tags.find() && tags.start()<=position) {
            String type=tags.group(1);
            if(type.equalsIgnoreCase("div") && !HomeworkParser.hasClass(HomeworkParser.attribute(tags.group(2),"class"),"right-content")) continue;
            int end=closingTag(html,tags.end(),type);
            if(end>=position && (best==null || end-tags.start()<best.length())) best=html.substring(tags.start(),end);
        }
        return best;
    }
    private static int closingTag(String html,int start,String name) {
        Matcher tags=Pattern.compile("</?"+name+"\\b[^>]*>",Pattern.CASE_INSENSITIVE).matcher(html); tags.region(start,html.length());
        int depth=1; while(tags.find()) { String t=tags.group(); if(t.startsWith("</")) depth--; else if(!t.endsWith("/>")) depth++; if(depth==0) return tags.end(); }
        return -1;
    }
    private static String textByClass(String html,String... names) {
        Matcher tags=Pattern.compile("<([a-z][a-z0-9]*)\\b([^>]*)>",Pattern.CASE_INSENSITIVE).matcher(html);
        while(tags.find()) for(String name:names) {
            if(!HomeworkParser.hasClass(HomeworkParser.attribute(tags.group(2),"class"),name)) continue;
            int end=closingTag(html,tags.end(),tags.group(1));
            if(end<0) return "";
            int close=html.lastIndexOf("</",end-1);
            if(close<tags.end()) return "";
            return HomeworkParser.cleanText(html.substring(tags.end(),close));
        }
        return "";
    }
}
