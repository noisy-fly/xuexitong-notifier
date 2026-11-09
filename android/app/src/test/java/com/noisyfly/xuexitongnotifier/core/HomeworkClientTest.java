package com.noisyfly.xuexitongnotifier.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class HomeworkClientTest {
    private static HomeworkClient mock(HomeworkClient.Transport inner) {
        return new HomeworkClient((url,body)->{
            try { return inner.request(url,body); }
            catch(AssertionError error) {
                if(url.contains("student/activelist")) return "{\"data\":{\"activeList\":[]}}";
                if(url.contains("active/getData")) return "{\"data\":[]}";
                throw error;
            }
        });
    }

    @Test public void verifiedCookiesReplaceOldDomainCookie() throws Exception {
        HomeworkClient client=new HomeworkClient();
        java.net.HttpCookie prior=new java.net.HttpCookie("challenge","old"); prior.setDomain(".chaoxing.com"); prior.setPath("/");
        client.cookieStore().add(java.net.URI.create("https://mooc1.chaoxing.com/"),prior);
        client.importCookies("https://mooc1.chaoxing.com/","challenge=new");
        assertEquals(1,client.sessionCookies().size());
        assertEquals("new",client.sessionCookies().get(0).getValue());
        assertEquals(".chaoxing.com",client.sessionCookies().get(0).getDomain());
    }
    @Test public void importedBrowserCookieUsesPlainCookieHeader() throws Exception {
        HomeworkClient client=new HomeworkClient();
        client.importCookies("https://mooc1.chaoxing.com/","challenge=verified");
        assertEquals(0,client.sessionCookies().get(0).getVersion());
    }
    @Test public void upgradesOnlyTrustedHttpRedirects() throws Exception {
        assertEquals("https://i.chaoxing.com/path?a=1",HomeworkClient.secureUrl("http://i.chaoxing.com/path?a=1").toString());
        try { HomeworkClient.secureUrl("http://chaoxing.com.attacker.example/path"); fail("untrusted host"); }
        catch(java.io.IOException expected) { }
        try { HomeworkClient.secureUrl("http://i.chaoxing.com:8080/path"); fail("unexpected port"); }
        catch(java.io.IOException expected) { }
    }
    @Test public void syncKeepsPartialResultsAndReportsFailures() throws Exception {
        HomeworkClient client=mock((url,body)-> {
            if(url.contains("fanyalogin")) { assertFalse(body.contains("example-password")); return "{\"status\":true}"; }
            if(url.endsWith("/base")) return "var fid = '1'";
            if(url.contains("courselistdata")) return "stucoursemiddle?courseid=1&clazzid=2&cpi=3\"<a class=\"course-name\" title=\"课程甲\"> stucoursemiddle?courseid=4&clazzid=5&cpi=6\"<a class=\"course-name\" title=\"课程乙\">";
            if(url.contains("stucoursemiddle?courseid=4")) throw new java.io.IOException("失败");
            if(url.contains("stucoursemiddle?courseid=1")) return "<input id=\"workEnc\" value=\"w\"><input id=\"enc\" value=\"s\">";
            if(url.contains("/work/list")) return "<li onclick=\"goTask(this);\" data=\"https%3A%2F%2Fmooc1.chaoxing.com%2Fdetail\"><p class=\"overHidden2 fl\">作业</p><p class=\"status fl\">未提交</p></li>";
            if(url.endsWith("/detail")) return "作答时间: 01-01 12:00 至 01-02 12:00";
            throw new AssertionError(url);
        });
        client.login("example-user","example-password");
        HomeworkClient.Result result=client.fetch();
        assertEquals(1,result.items.length());
        assertEquals("作业",result.items.getJSONObject(0).getString("title"));
        assertEquals(2,result.errors.size());
        assertTrue(result.errors.stream().anyMatch(e -> e.contains("课程乙")));
    }
    @Test public void failedLoginDoesNotPretendSuccess() throws Exception {
        HomeworkClient client=mock((url,body)->"{\"status\":false,\"msg2\":\"需要验证码\"}");
        try { client.login("u","p"); fail("must reject"); }
        catch(java.io.IOException e) { assertTrue(e.getMessage().contains("需要验证码")); }
    }
    @Test public void cannotInterpretUnrecognizedCourseListAsEmpty() throws Exception {
        HomeworkClient client=mock((url,body)->url.endsWith("/base")?"":"unrecognized page");
        try { client.fetch(); fail("must reject"); }
        catch(java.io.IOException expected) { assertTrue(expected.getMessage().contains("课程列表")); }
    }
    @Test public void resumesAtInterruptedCourseWithoutRepeatingEarlierCourses() throws Exception {
        final int[] courseOne={0}, courseTwo={0};
        HomeworkClient client=mock((url,body)->{
            if(url.endsWith("/base")) return "var fid = '1'";
            if(url.contains("courselistdata")) return "stucoursemiddle?courseid=1&clazzid=2&cpi=3\"<a class=\"course-name\" title=\"甲\"> stucoursemiddle?courseid=4&clazzid=5&cpi=6\"<a class=\"course-name\" title=\"乙\">";
            if(url.contains("stucoursemiddle?courseid=1")) {courseOne[0]++; return "<input id=\"workEnc\" value=\"w\"><input id=\"enc\" value=\"s\">";}
            if(url.contains("stucoursemiddle?courseid=4")) {if(++courseTwo[0]==1) throw new HomeworkClient.Restricted(); return "<input id=\"workEnc\" value=\"w\"><input id=\"enc\" value=\"s\">";}
            if(url.contains("/work/list")) return "暂无作业";
            throw new AssertionError(url);
        });
        assertTrue(client.fetch().restricted);
        assertFalse(client.fetch().restricted);
        assertEquals(1,courseOne[0]); assertEquals(2,courseTwo[0]);
    }
    @Test public void usesListCountdownWithoutRequestingDetail() throws Exception {
        HomeworkClient client=mock((url,body)->{
            if(url.endsWith("/base")) return "var fid = '1'";
            if(url.contains("courselistdata")) return "stucoursemiddle?courseid=1&clazzid=2&cpi=3\"<a class=\"course-name\" title=\"甲\">";
            if(url.contains("stucoursemiddle")) return "<input id=\"workEnc\" value=\"w\"><input id=\"enc\" value=\"s\">";
            if(url.contains("/work/list")) return "<li onclick=\"goTask(this);\" data=\"https%3A%2F%2Fmooc1.chaoxing.com%2Fwork\"><p class=\"overHidden2 fl\">作业</p><p class=\"status fl\">未交</p><div class=\"time notOver\">剩余90小时37分钟</div></li>";
            throw new AssertionError("unexpected detail request");
        });
        long before=System.currentTimeMillis(); HomeworkClient.Result result=client.fetch();
        assertEquals(1,result.items.length());
        assertTrue(result.items.getJSONObject(0).getBoolean("estimated"));
        assertTrue(result.items.getJSONObject(0).getLong("deadline")>=before+(90*60L+37)*60_000);
    }
    @Test public void fetchesEveryAdvertisedHomeworkPage() throws Exception {
        final int[] calls={0};
        HomeworkClient client=mock((url,body)->{
            if(url.endsWith("/base")) return "var fid = '1'";
            if(url.contains("courselistdata")) return "stucoursemiddle?courseid=1&clazzid=2&cpi=3\"<a class=\"course-name\" title=\"甲\">";
            if(url.contains("stucoursemiddle")) return "<input id=\"workEnc\" value=\"w\"><input id=\"enc\" value=\"s\">";
            if(url.contains("/work/list")) { calls[0]++; return "暂无作业<div id=\"page\"><li>1</li><li>2</li></div>"; }
            throw new AssertionError("unexpected request");
        });
        assertEquals(1,client.fetch().errors.size()); assertEquals(2,calls[0]);
    }
    @Test public void fetchesOnlySelectedCoursesAndDoesNothingWhenSelectionEmpty() throws Exception {
        final int[] first={0}, second={0};
        HomeworkClient client=mock((url,body)->{
            if(url.endsWith("/base")) return "var fid = '1'";
            if(url.contains("courselistdata")) return "stucoursemiddle?courseid=1&clazzid=2&cpi=3\"<a class=\"course-name\" title=\"甲\"> stucoursemiddle?courseid=4&clazzid=5&cpi=6\"<a class=\"course-name\" title=\"乙\">";
            if(url.contains("stucoursemiddle?courseid=1")) {first[0]++; return "<input id=\"workEnc\" value=\"w\"><input id=\"enc\" value=\"s\">";}
            if(url.contains("stucoursemiddle?courseid=4")) {second[0]++; throw new AssertionError("unselected course requested");}
            if(url.contains("/work/list")) return "暂无作业";
            throw new AssertionError("unexpected request");
        });
        assertEquals(0,client.fetch(java.util.Collections.emptySet()).items.length());
        assertEquals(1,client.fetch(java.util.Collections.singleton("1:2:3")).errors.size());
        assertEquals(1,first[0]); assertEquals(0,second[0]);
    }
    @Test public void includesExamForSelectedCourse() throws Exception {
        HomeworkClient client=mock((url,body)->{
            if(url.endsWith("/base")) return "var fid = '1'";
            if(url.contains("courselistdata")) return "stucoursemiddle?courseid=1&clazzid=2&cpi=3\"<a class=\"course-name\" title=\"英语\">";
            if(url.contains("stucoursemiddle")) return "<input id=\"workEnc\" value=\"w\"><input id=\"enc\" value=\"s\"><input id=\"examEnc\" value=\"e\"><input id=\"openc\" value=\"o\">";
            if(url.contains("/work/list")) return "暂无作业";
            if(url.contains("/exam/exam-list")) return "<li><div class='right-content'><p class='overHidden2'>英语考试</p><p class='status'>待考试</p><div onclick=\"goTest('1','99','0','2026-11-10 12:00','2')\"></div></div></li>";
            throw new AssertionError("unexpected request");
        });
        HomeworkClient.Result result=client.fetch(java.util.Collections.singleton("1:2:3"));
        assertEquals(1,result.items.length()); assertEquals("exam",result.items.getJSONObject(0).getString("kind"));
        assertTrue(result.items.getJSONObject(0).getLong("deadline")>0);
    }
    @Test public void completedAssignmentIsKeptWithoutOpeningDetail() throws Exception {
        HomeworkClient client=mock((url,body)->{
            if(url.endsWith("/base")) return "var fid = '1'";
            if(url.contains("courselistdata")) return "stucoursemiddle?courseid=1&clazzid=2&cpi=3\"<a class=\"course-name\" title=\"课程\">";
            if(url.contains("stucoursemiddle")) return "<input id=\"workEnc\" value=\"w\"><input id=\"enc\" value=\"s\"><input id=\"examEnc\" value=\"e\"><input id=\"openc\" value=\"o\">";
            if(url.contains("/work/list")) return "<li><p class='overHidden2'>作业</p><p class='status'>已完成</p></li>";
            if(url.contains("/exam/exam-list")) return "暂无考试";
            throw new AssertionError("unexpected detail request");
        });
        HomeworkClient.Result result=client.fetch(java.util.Collections.singleton("1:2:3"));
        assertEquals(1,result.items.length()); assertTrue(result.items.getJSONObject(0).getBoolean("completed"));
        assertEquals(0,result.items.getJSONObject(0).getLong("deadline"));
    }
    @Test public void completedAssignmentReadsSafeMetadataDeadlineWithoutScheduling() throws Exception {
        HomeworkClient client=mock((url,body)->{
            if(url.endsWith("/base")) return "var fid = '1'";
            if(url.contains("courselistdata")) return "stucoursemiddle?courseid=1&clazzid=2&cpi=3\"<a class=\"course-name\" title=\"课程\">";
            if(url.contains("stucoursemiddle")) return "<input id=\"workEnc\" value=\"w\"><input id=\"enc\" value=\"s\"><input id=\"examEnc\" value=\"e\"><input id=\"openc\" value=\"o\">";
            if(url.contains("/work/list")) return "<li onclick=\"goTask(this);\" data=\"https%3A%2F%2Fmooc1.chaoxing.com%2Fmooc-ans%2Fmooc2%2Fwork%2Ftask%3FworkId%3D1\"><p class='overHidden2'>作业</p><p class='status'>已完成</p></li>";
            if(url.contains("/work/task")) return "作答时间：2026-11-01 10:00 至 2026-11-10 10:00";
            if(url.contains("/exam/exam-list")) return "暂无考试";
            throw new AssertionError("unexpected request");
        });
        org.json.JSONObject item=client.fetch(java.util.Collections.singleton("1:2:3")).items.getJSONObject(0);
        assertTrue(item.getBoolean("completed")); assertTrue(item.getLong("deadline")>0);
    }
    @Test public void activityStatusDoesNotConfuseEndedWithCompleted() throws Exception {
        org.json.JSONArray items=TaskParser.activities(new org.json.JSONObject("{\"data\":{\"activeList\":[{\"id\":1,\"nameOne\":\"主题讨论\",\"status\":1,\"isFinish\":0,\"endTime\":1800000000000},{\"id\":2,\"nameOne\":\"活动\",\"status\":2,\"endTime\":1700000000000},{\"id\":3,\"nameOne\":\"未知\",\"status\":1}]}}"),"课程","1:2:3");
        assertEquals(3,items.length());
        assertTrue(items.getJSONObject(0).getBoolean("remindable"));
        assertFalse(items.getJSONObject(1).getBoolean("completed"));
        assertFalse(items.getJSONObject(1).getBoolean("remindable"));
        assertTrue(items.getJSONObject(2).getBoolean("remindable"));
        assertTrue(items.getJSONObject(2).getString("status").contains("未知"));
    }
    @Test public void taskPlanRecognizesScoreCompletion() throws Exception {
        org.json.JSONArray plans=new org.json.JSONArray("[{\"planId\":\"1\",\"name\":\"任务\",\"score\":{\"completed\":1},\"endDateStr\":\"2026-11-10 10:00\"}]");
        org.json.JSONObject item=TaskParser.plans(plans,"课程","1:2:3","task").getJSONObject(0);
        assertTrue(item.getBoolean("completed")); assertFalse(item.getBoolean("remindable"));
    }
    @Test public void selectedCourseIncludesClassroomActivities() throws Exception {
        HomeworkClient client=mock((url,body)->{
            if(url.endsWith("/base")) return "var fid = '1'";
            if(url.contains("courselistdata")) return "stucoursemiddle?courseid=1&clazzid=2&cpi=3\"<a class=\"course-name\" title=\"课程\">";
            if(url.contains("stucoursemiddle")) return "<input id=\"workEnc\" value=\"w\"><input id=\"enc\" value=\"s\"><input id=\"examEnc\" value=\"e\"><input id=\"openc\" value=\"o\">";
            if(url.contains("/work/list")) return "暂无作业";
            if(url.contains("/exam/exam-list")) return "暂无考试";
            if(url.contains("student/activelist")) return "{\"data\":{\"activeList\":[{\"id\":123,\"nameOne\":\"主题讨论\",\"status\":1,\"endTime\":1800000000000}]}}";
            if(url.contains("active/getData")) return "{\"data\":[]}";
            throw new AssertionError("unexpected request");
        });
        HomeworkClient.Result result=client.fetch(java.util.Collections.singleton("1:2:3"));
        assertEquals(1,result.items.length()); assertEquals("activity",result.items.getJSONObject(0).getString("kind"));
    }
    @Test public void expandsTaskPackageIntoIndividualPlans() throws Exception {
        HomeworkClient client=mock((url,body)->{
            if(url.endsWith("/base")) return "var fid = '1'";
            if(url.contains("courselistdata")) return "stucoursemiddle?courseid=1&clazzid=2&cpi=3\"<a class=\"course-name\" title=\"课程\">";
            if(url.contains("stucoursemiddle")) return "<input id=\"workEnc\" value=\"w\"><input id=\"enc\" value=\"s\"><input id=\"examEnc\" value=\"e\"><input id=\"openc\" value=\"o\">";
            if(url.contains("/work/list")) return "暂无作业";
            if(url.contains("/exam/exam-list")) return "暂无考试";
            if(url.contains("student/activelist")) return "{\"data\":{\"activeList\":[]}}";
            if(url.contains("active/getData")) return "{\"data\":[{\"id\":123,\"name\":\"任务包\"}]}";
            if(url.contains("jumpStudyPlanList")) return "<script>var eTaskUserId = 'opaque';</script>";
            if(url.contains("getGroupData")) { assertNotNull(body); return "{\"data\":[{\"encryptGroupId\":\"group\"}]}"; }
            if(url.contains("getPlanDataByGroupId")) return "{\"data\":[{\"planId\":\"9\",\"name\":\"AI实践\",\"isFinish\":0,\"endDateStr\":\"2026-11-10 12:00\"}]}";
            throw new AssertionError("unexpected request");
        });
        HomeworkClient.Result result=client.fetch(java.util.Collections.singleton("1:2:3"));
        assertEquals(1,result.items.length()); assertEquals("AI实践",result.items.getJSONObject(0).getString("title"));
        assertTrue(result.items.getJSONObject(0).getBoolean("remindable")); assertTrue(result.items.getJSONObject(0).getLong("deadline")>0);
        assertEquals(0,result.errors.size());
    }
    @Test public void stillReadsExamsWhenHomeworkEntryFails() throws Exception {
        HomeworkClient client=mock((url,body)->{
            if(url.endsWith("/base")) return "var fid = '1'";
            if(url.contains("courselistdata")) return "stucoursemiddle?courseid=1&clazzid=2&cpi=3\"<a class=\"course-name\" title=\"英语\">";
            if(url.contains("stucoursemiddle")) return "<input id=\"enc\" value=\"s\"><input id=\"examEnc\" value=\"e\"><input id=\"openc\" value=\"o\">";
            if(url.contains("/exam/exam-list")) return "<li><div class='right-content'><p class='overHidden2'>考试</p><p class='status'>待考试</p><div onclick=\"goTest('1','99','0','2026-11-10 12:00','2')\"></div></div></li>";
            throw new AssertionError("unexpected request");
        });
        HomeworkClient.Result result=client.fetch(java.util.Collections.singleton("1:2:3"));
        assertEquals(1,result.items.length()); assertEquals("exam",result.items.getJSONObject(0).getString("kind"));
        assertTrue(result.failedCourses.contains("1:2:3|homework"));
    }
}
