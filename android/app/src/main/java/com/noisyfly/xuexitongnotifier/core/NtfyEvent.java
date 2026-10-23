package com.noisyfly.xuexitongnotifier.core;

import java.util.Optional;
import org.json.JSONException;
import org.json.JSONObject;

public final class NtfyEvent {
    private final String id;
    private final String title;
    private final String message;
    private final long timeSeconds;

    private NtfyEvent(String id, String title, String message, long timeSeconds) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.timeSeconds = timeSeconds;
    }

    public static Optional<NtfyEvent> parse(String json) {
        try {
            JSONObject object = new JSONObject(json);
            if (!"message".equals(object.optString("event"))) {
                return Optional.empty();
            }
            String id = object.optString("id").trim();
            String message = object.optString("message").trim();
            if (id.isEmpty() || message.isEmpty()) {
                throw new IllegalArgumentException("ntfy 消息缺少 ID 或正文");
            }
            String title = object.optString("title").trim();
            if (title.isEmpty()) {
                title = "学习通新消息";
            }
            return Optional.of(new NtfyEvent(
                    id,
                    title,
                    message,
                    object.optLong("time", 0L)
            ));
        } catch (JSONException exception) {
            throw new IllegalArgumentException("ntfy 返回了无效 JSON", exception);
        }
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String message() {
        return message;
    }

    public long timeSeconds() {
        return timeSeconds;
    }
}

