package com.noisyfly.xuexitongnotifier.core;
import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChapterParserTest {
    @Test public void identifiesTestsAndNeverTreatsVideosAsTests() throws Exception {
        String html="<script>var mArg = {\"attachments\":[{\"type\":\"video\",\"isPassed\":false,\"property\":{\"objectid\":\"x\"}},{\"type\":\"workid\",\"isPassed\":true,\"property\":{\"workid\":\"123\",\"title\":\"测验\"}},{\"type\":\"workid\",\"property\":{\"workid\":\"456\"}}]};</script>";
        JSONArray a=ChapterParser.cards(html,"课程","1:2:3","44","第一章",false);
        assertEquals(2,a.length()); assertTrue(a.getJSONObject(0).getBoolean("completed"));
        assertEquals("状态未知",a.getJSONObject(1).getString("status"));
        assertEquals("chapter",a.getJSONObject(0).getString("kind"));
        assertEquals(0,a.getJSONObject(1).getLong("deadline"));
    }
    @Test public void rejectsUnrecognizedCards() throws Exception {
        try { ChapterParser.cards("<html>unknown</html>","课程","1:2:3","44","第一章",false); fail(); } catch(IllegalArgumentException expected) { }
    }
    @Test public void completedChapterCanConfirmCompletionButPendingChapterCannot() throws Exception {
        String html="<script>mArg = {\"attachments\":[{\"type\":\"workid\",\"jobid\":\"work-123\",\"property\":{\"title\":\"测验\"}}]};</script>";
        assertTrue(ChapterParser.cards(html,"课程","1:2:3","44","第一章",true).getJSONObject(0).getBoolean("completed"));
        assertEquals("状态未知",ChapterParser.cards(html,"课程","1:2:3","44","第一章",false).getJSONObject(0).getString("status"));
    }
    @Test public void readsSectionCompletionWithoutTreatingOtherStatusAsCompleted() {
        String tree="<li><div class='chapter_item' id='cur11' title='测验'></div><i class='icon_yiwanc'></i></li><li><div class='chapter_item' id='cur22' title='练习'></div></li>";
        java.util.List<ChapterParser.Section> sections=ChapterParser.sections(tree);
        assertEquals(2,sections.size()); assertTrue(sections.get(0).completed); assertFalse(sections.get(1).completed);
    }
    @Test public void treatsOnlyExplicitEndTimeAsDeadlineAndKeepsDurationSeparate() throws Exception {
        String html="mArg = {\"attachments\":[{\"type\":\"workid\",\"isPassed\":false,\"property\":{\"workid\":\"77\",\"endTime\":1800000000000,\"timeLimitMinutes\":30}}]};";
        org.json.JSONObject item=ChapterParser.cards(html,"课程","1:2:3","44","第一章",false).getJSONObject(0);
        assertEquals(1800000000000L,item.getLong("deadline")); assertEquals(30,item.getInt("timeLimitMinutes")); assertTrue(item.getBoolean("remindable"));
    }
    @Test public void completionMarkerMustBelongToSameSection() {
        String html="<div class='chapter_item' id='cur11' title='一'></div><div class='chapter_item' id='cur22' title='二'></div><i class='icon_yiwanc'></i></li>";
        java.util.List<ChapterParser.Section> sections=ChapterParser.sections(html);
        assertEquals(2,sections.size()); assertFalse(sections.get(0).completed); assertTrue(sections.get(1).completed);
        assertFalse(ChapterParser.sections("<div class='chapter_item' id='cur11' title='一'></div><i class='icon_yiwanc'></i>").get(0).completed);
    }
}
