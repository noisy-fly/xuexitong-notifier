package com.noisyfly.xuexitongnotifier.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.noisyfly.xuexitongnotifier.core.EndpointConfig;
import com.noisyfly.xuexitongnotifier.core.RecentEventIds;

public final class ConfigStore {
    private static final String PREFS_NAME = "notifier_config";
    private static final String KEY_SERVER = "server_url";
    private static final String KEY_TOPIC = "topic";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_ENABLED = "listener_enabled";
    private static final String KEY_RECENT_IDS = "recent_event_ids";
    private static final int RECENT_ID_LIMIT = 100;

    private final SharedPreferences preferences;

    public ConfigStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void save(EndpointConfig config) {
        preferences.edit()
                .putString(KEY_SERVER, config.serverUrl())
                .putString(KEY_TOPIC, config.topic())
                .putString(KEY_TOKEN, config.token())
                .apply();
    }

    public EndpointConfig load() {
        return new EndpointConfig(
                preferences.getString(KEY_SERVER, "https://ntfy.sh"),
                preferences.getString(KEY_TOPIC, ""),
                preferences.getString(KEY_TOKEN, "")
        );
    }

    public String serverUrl() {
        return preferences.getString(KEY_SERVER, "https://ntfy.sh");
    }

    public String topic() {
        return preferences.getString(KEY_TOPIC, "");
    }

    public String token() {
        return preferences.getString(KEY_TOKEN, "");
    }

    public boolean isEnabled() {
        return preferences.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public synchronized boolean acceptEventId(String eventId) {
        RecentEventIds recent = new RecentEventIds(
                RECENT_ID_LIMIT,
                preferences.getString(KEY_RECENT_IDS, "")
        );
        if (!recent.accept(eventId)) {
            return false;
        }
        preferences.edit().putString(KEY_RECENT_IDS, recent.serialize()).apply();
        return true;
    }
}
