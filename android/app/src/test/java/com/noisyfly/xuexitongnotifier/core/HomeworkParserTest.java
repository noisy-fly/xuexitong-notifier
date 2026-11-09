package com.noisyfly.xuexitongnotifier.core;

import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;

public class HomeworkParserTest {
    @Test public void parsesCoursesAndDeduplicates() {
        String html = "stucoursemiddle?courseid=12&clazzid=34&cpi=56\" <a class=\"course-name\" title=\"数学\">" +
                "stucoursemiddle?courseid=12&clazzid=34&cpi=56\"";
        assertEquals(1, HomeworkParser.courses(html).size());
        assertEquals("数学", HomeworkParser.courses(html).get(0).name);
    }
    @Test public void parsesPendingItemsAndUnknownStatus() {
        String html = "<li onclick=\"goTask(this);\" data=\"https%3A%2F%2Fmooc1.chaoxing.com%2Fwork\"><p class=\"overHidden2 fl\">作业一</p><p class=\"status fl\">未提交</p></li>" +
                "<li onclick=\"goTask(this);\" data=\"x\"><p class=\"overHidden2 fl\">作业二</p><p class=\"status fl\">已提交</p></li>";
        assertEquals(1, HomeworkParser.pending(html).size());
        assertEquals("作业一", HomeworkParser.pending(html).get(0).title);
    }
    @Test public void excludesTraditionalAndPeerReviewedCompletedWork() {
        String row="<li onclick=\"goTask(this);\" data=\"https%3A%2F%2Fmooc1.chaoxing.com%2Fwork\"><p class=\"overHidden2 fl\">作业</p><p class=\"status fl\">STATUS</p></li>";
        assertEquals(0,HomeworkParser.pending(row.replace("STATUS","已互評")).size());
        assertEquals(0,HomeworkParser.pending(row.replace("STATUS","待批閱")).size());
    }
    @Test public void allAssignmentsKeepsCompletedAndPendingReview() {
        String html="<li><p class='overHidden2'>已完成的作业</p><p class='status'>已完成</p></li>"+
                "<li><p class='overHidden2'>待批阅的作业</p><p class='status'>待批阅</p></li>";
        java.util.List<HomeworkParser.Item> items=HomeworkParser.all(html);
        assertEquals(2,items.size()); assertTrue(items.get(0).completed); assertTrue(items.get(1).completed);
    }
    @Test public void assignsNearestYearAcrossNewYear() {
        long now = Instant.parse("2026-12-30T00:00:00Z").toEpochMilli();
        long expected = Instant.parse("2027-01-02T02:30:00Z").toEpochMilli();
        assertEquals(expected, HomeworkParser.deadline("作答时间: 12-28 10:30 至 01-02 10:30", now));
        assertEquals(0, HomeworkParser.deadline("无截止时间", now));
    }
    @Test public void handlesNestedMarkupAndAttributeOrderWithoutCrossingRows() {
        String html="<li data='https%3A%2F%2Fmooc1.chaoxing.com%2Fwork' onclick='goTask(this);'><p title='作业&#x4e00;' class='fl overHidden2'><span>作业&#x4e00;</span></p><p class='fl status'>未提交</p></li>";
        assertEquals(1,HomeworkParser.pending(html).size());
        assertEquals("作业一",HomeworkParser.pending(html).get(0).title);
        try { HomeworkParser.pending("<li onclick=\"goTask(this);\" data=\"x\"><p>坏数据</p></li>"); fail("unrecognized row must not silently disappear"); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void parsesExplicitYearsAndMarkupAroundLabels() {
        long now=Instant.parse("2026-12-30T00:00:00Z").toEpochMilli();
        assertEquals(Instant.parse("2028-01-02T02:30:00Z").toEpochMilli(),HomeworkParser.deadline("<span>截止时间</span>：<b>2028-01-02 10:30</b>",now));
        assertEquals(Instant.parse("2027-01-02T02:30:00Z").toEpochMilli(),HomeworkParser.deadline("<span>作答时间</span>：12-28 10:30&nbsp;至&nbsp;01-02 10:30",now));
        assertEquals(Instant.parse("2027-01-02T02:30:00Z").toEpochMilli(),HomeworkParser.deadline("<p class='hpInfo'>互评时间：12-28 10:30 至 01-02 10:30</p>",now,"待互评"));
        assertEquals(0,HomeworkParser.deadline("<p class='hpInfo'>互评时间：12-28 10:30 至 01-02 10:30</p>",now,"未交"));
        assertEquals(Instant.parse("2027-01-02T02:30:00Z").toEpochMilli(),HomeworkParser.deadline("截止时间：2026-12-31 10:30 <p>互评时间：12-28 10:30 至 01-02 10:30</p>",now,"待互评"));
    }
    @Test public void preservesPlusInEncodedDetailUrl() {
        String html="<li onclick=\"goTask(this);\" data=\"https%3A%2F%2Fmooc1.chaoxing.com%2Fwork%3Fx%3Da+b\"><p class=\"overHidden2 fl\">作业</p><p class=\"status fl\">未提交</p></li>";
        assertEquals("https://mooc1.chaoxing.com/work?x=a+b",HomeworkParser.pending(html).get(0).url);
    }
    @Test public void readsRemainingTimeFromTheAssignmentList() {
        String html="<li onclick=\"goTask(this);\" data=\"https%3A%2F%2Fmooc1.chaoxing.com%2Fwork\"><p class=\"overHidden2 fl\">作业</p><p class=\"status fl\">未交</p><div class=\"time notOver\"><img src=\"x\">剩余2天3小时4分钟</div></li>";
        assertEquals((2*24*60L+3*60L+4)*60_000L,HomeworkParser.pending(html).get(0).remainingMillis);
        assertEquals(-1,HomeworkParser.pending(html.replace("time notOver","time")).get(0).remainingMillis);
        assertEquals((2*24*60L+3*60L+4)*60_000L,HomeworkParser.pending(html.replace("<div class=\"time", "<div class=\"wrapper\"><div class=\"time").replace("</div></li>","</div></div></li>")).get(0).remainingMillis);
    }
    @Test public void readsPaginationOnlyFromPageControl() {
        String page="<div id=\"page\"><li class=\"xl-prevPage\">上一页</li><li class=\"xl-active\">1</li><li>2</li><li>3</li><li class=\"xl-nextPage\">下一页</li></div><li>99</li>";
        assertEquals(3,HomeworkParser.pageCount("<div class='wrapper'>"+page+"</div>"));
        assertEquals(1,HomeworkParser.pageCount("<p>暂无作业</p>"));
    }
    @Test public void parsesSelectedCourseExamAndEndTime() {
        String html="<li><div class='right-content'><p class='overHidden2 fl'>英语期末考试</p><p class='status fl'>待考试</p><div onclick=\"goTest('1','99','0','2026-11-10 12:00','2','true')\"></div></div></li>";
        java.util.List<ExamParser.Exam> exams=ExamParser.parse(html,Instant.parse("2026-11-08T00:00:00Z").toEpochMilli());
        assertEquals(1,exams.size()); assertEquals("英语期末考试",exams.get(0).title);
        assertEquals(Instant.parse("2026-11-10T04:00:00Z").toEpochMilli(),exams.get(0).deadline);
        assertEquals(0,ExamParser.parse(html.replace("待考试","已完成"),0).size());
    }
    @Test public void examWithUnknownEndRemainsVisibleButUnscheduled() {
        String html="<li><p class='overHidden2'>考试</p><p class='status'>未开始</p><a onclick=\"goTest('1','99','0','','2')\">考试</a></li>";
        assertEquals(0,ExamParser.parse(html,0).get(0).deadline);
    }
    @Test public void nonClickableUpcomingExamStillAppears() {
        String html="<li><p class='overHidden2'>待开考考试</p><p class='status'>未开始</p><p>结束时间：2026-11-10 12:00</p></li>";
        java.util.List<ExamParser.Exam> exams=ExamParser.parse(html,Instant.parse("2026-11-08T00:00:00Z").toEpochMilli());
        assertEquals(1,exams.size());
        assertEquals(Instant.parse("2026-11-10T04:00:00Z").toEpochMilli(),exams.get(0).deadline);
    }
}
