package com.noisyfly.xuexitongnotifier.core;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class UpdatePolicyTest {
    @Test public void comparesVersionsAndIgnoredRelease() {
        assertTrue(UpdatePolicy.newer("v0.10.0", "0.9.9"));
        assertFalse(UpdatePolicy.newer("0.3.0", "0.3.0"));
        assertTrue(UpdatePolicy.newer("0.3.0", "0.3.0-rc.1"));
        assertFalse(UpdatePolicy.shouldPrompt("v0.4.0", "0.3.0", "v0.4.0", false));
        assertTrue(UpdatePolicy.shouldPrompt("v0.4.0", "0.3.0", "v0.4.0", true));
    }
    @Test public void validatesReleaseAndRejectsDangerousAsset() throws Exception {
        String digest="sha256:"+"a".repeat(64);
        JSONObject a=new JSONObject().put("name","xuexitong-notifier-v0.4.0.apk").put("size",123).put("digest",digest)
            .put("browser_download_url","https://github.com/noisy-fly/xuexitong-notifier/releases/download/v0.4.0/xuexitong-notifier-v0.4.0.apk");
        JSONObject release=new JSONObject().put("tag_name","v0.4.0").put("assets",new JSONArray().put(a));
        assertEquals("v0.4.0",UpdatePolicy.parse(release).tag);
        a.put("browser_download_url","https://evil.example/file.apk");
        try { UpdatePolicy.parse(release); fail(); } catch(IllegalArgumentException expected) { }
    }
    @Test public void rejectsModifiedAndTruncatedDownloads() throws Exception {
        byte[] content="abc".getBytes("UTF-8"); String hash="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        UpdateClient.copyVerified(new java.io.ByteArrayInputStream(content),new java.io.ByteArrayOutputStream(),3,hash);
        try { UpdateClient.copyVerified(new java.io.ByteArrayInputStream(content),new java.io.ByteArrayOutputStream(),3,"0".repeat(64)); fail(); } catch(java.io.IOException expected) { }
        try { UpdateClient.copyVerified(new java.io.ByteArrayInputStream(content),new java.io.ByteArrayOutputStream(),4,hash); fail(); } catch(java.io.IOException expected) { }
    }
}
