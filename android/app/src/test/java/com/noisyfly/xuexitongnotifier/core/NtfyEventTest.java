package com.noisyfly.xuexitongnotifier.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import org.junit.Test;

public final class NtfyEventTest {
    @Test
    public void parsesMessageEvent() {
        Optional<NtfyEvent> parsed = NtfyEvent.parse(
                "{\"id\":\"event-1\",\"event\":\"message\","
                        + "\"time\":1730000000,\"title\":\"课程通知\","
                        + "\"message\":\"作业提交时间有调整\"}"
        );

        assertTrue(parsed.isPresent());
        assertEquals("event-1", parsed.get().id());
        assertEquals("课程通知", parsed.get().title());
        assertEquals("作业提交时间有调整", parsed.get().message());
        assertEquals(1730000000L, parsed.get().timeSeconds());
    }

    @Test
    public void ignoresKeepaliveAndOpenEvents() {
        assertFalse(NtfyEvent.parse("{\"event\":\"keepalive\"}").isPresent());
        assertFalse(NtfyEvent.parse("{\"event\":\"open\"}").isPresent());
    }

    @Test
    public void suppliesReadableFallbacks() {
        NtfyEvent parsed = NtfyEvent.parse(
                "{\"id\":\"event-2\",\"event\":\"message\",\"message\":\"正文\"}"
        ).orElseThrow();

        assertEquals("学习通新消息", parsed.title());
        assertEquals("正文", parsed.message());
    }

    @Test
    public void rejectsMalformedOrIncompleteMessages() {
        assertThrows(IllegalArgumentException.class, () -> NtfyEvent.parse("not-json"));
        assertThrows(
                IllegalArgumentException.class,
                () -> NtfyEvent.parse("{\"event\":\"message\",\"id\":\"missing-body\"}")
        );
    }
}

