package com.noisyfly.xuexitongnotifier.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class EndpointConfigTest {
    @Test
    public void normalizesServerAndBuildsSubscriptionUrl() {
        EndpointConfig config = new EndpointConfig(
                "https://ntfy.sh/",
                "xuexitong_topic-123",
                ""
        );

        assertEquals("https://ntfy.sh", config.serverUrl());
        assertEquals(
                "https://ntfy.sh/xuexitong_topic-123/json",
                config.subscriptionUrl()
        );
        assertNull(config.authorizationHeader());
    }

    @Test
    public void createsBearerAuthorizationHeader() {
        EndpointConfig config = new EndpointConfig(
                "https://notify.example.com",
                "private_topic",
                "secret-token"
        );

        assertEquals("Bearer secret-token", config.authorizationHeader());
    }

    @Test
    public void allowsLanHttpServer() {
        EndpointConfig config = new EndpointConfig(
                "http://192.168.1.10:8080",
                "private_topic",
                ""
        );

        assertEquals(
                "http://192.168.1.10:8080/private_topic/json",
                config.subscriptionUrl()
        );
    }

    @Test
    public void rejectsUnsafeOrAmbiguousEndpoints() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EndpointConfig("ftp://ntfy.example.com", "topic_name", "")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EndpointConfig("https://user@ntfy.example.com", "topic_name", "")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EndpointConfig("https://ntfy.example.com/path", "topic_name", "")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EndpointConfig("https://ntfy.example.com", "bad/topic", "")
        );
    }
}

