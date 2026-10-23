package com.noisyfly.xuexitongnotifier.service;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import com.noisyfly.xuexitongnotifier.R;
import com.noisyfly.xuexitongnotifier.core.EndpointConfig;
import com.noisyfly.xuexitongnotifier.core.NtfyEvent;
import com.noisyfly.xuexitongnotifier.core.ReconnectBackoff;
import com.noisyfly.xuexitongnotifier.data.ConfigStore;
import com.noisyfly.xuexitongnotifier.notifications.NotificationDispatcher;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class NtfyListenerService extends Service {
    public static final String ACTION_START = "com.noisyfly.xuexitongnotifier.action.START";
    public static final String ACTION_STOP = "com.noisyfly.xuexitongnotifier.action.STOP";
    public static final String ACTION_STATUS = "com.noisyfly.xuexitongnotifier.action.STATUS";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_DETAIL = "detail";
    private static final int FOREGROUND_ID = 1_001;

    private static volatile String lastState = "stopped";
    private static volatile String lastDetail = "监听未启动";

    private volatile boolean running;
    private volatile HttpURLConnection activeConnection;
    private Thread listenerThread;
    private ConfigStore store;
    private NotificationDispatcher notifications;

    public static void start(Context context) {
        Intent intent = new Intent(context, NtfyListenerService.class).setAction(ACTION_START);
        context.startForegroundService(intent);
    }

    public static void stop(Context context) {
        context.startService(new Intent(context, NtfyListenerService.class).setAction(ACTION_STOP));
    }

    public static String lastState() {
        return lastState;
    }

    public static String lastDetail() {
        return lastDetail;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        store = new ConfigStore(this);
        notifications = new NotificationDispatcher(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            store.setEnabled(false);
            stopListening();
            publishStatus("stopped", getString(R.string.status_stopped));
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            startForeground(FOREGROUND_ID, notifications.foreground(getString(R.string.status_connecting)));
            startListening();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopListening();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startListening() {
        final EndpointConfig config;
        try {
            config = store.load();
        } catch (IllegalArgumentException exception) {
            store.setEnabled(false);
            publishStatus("error", getString(R.string.status_invalid_config));
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }
        store.setEnabled(true);
        running = true;
        publishStatus("connecting", getString(R.string.status_connecting));
        listenerThread = new Thread(() -> listen(config), "ntfy-listener");
        listenerThread.start();
    }

    private void listen(EndpointConfig config) {
        ReconnectBackoff backoff = new ReconnectBackoff();
        while (running) {
            try {
                connectAndRead(config, backoff);
            } catch (IOException | IllegalArgumentException exception) {
                if (running) {
                    publishStatus("retrying", getString(R.string.status_reconnecting));
                }
            } finally {
                disconnectActiveConnection();
            }
            if (!running) {
                break;
            }
            try {
                Thread.sleep(backoff.nextDelayMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void connectAndRead(EndpointConfig config, ReconnectBackoff backoff) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(config.subscriptionUrl()).openConnection();
        activeConnection = connection;
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(70_000);
        connection.setRequestProperty("Accept", "application/x-ndjson");
        if (config.authorizationHeader() != null) {
            connection.setRequestProperty("Authorization", config.authorizationHeader());
        }
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("ntfy HTTP " + responseCode);
        }
        backoff.reset();
        publishStatus("connected", getString(R.string.status_connected));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(),
                StandardCharsets.UTF_8
        ))) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                Optional<NtfyEvent> parsed;
                try {
                    parsed = NtfyEvent.parse(line);
                } catch (IllegalArgumentException exception) {
                    continue;
                }
                if (parsed.isEmpty()) {
                    continue;
                }
                NtfyEvent event = parsed.get();
                if (store.acceptEventId(event.id())) {
                    notifications.showMessage(event);
                }
            }
        }
    }

    private void publishStatus(String state, String detail) {
        lastState = state;
        lastDetail = detail;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(FOREGROUND_ID, notifications.foreground(detail));
        Intent update = new Intent(ACTION_STATUS)
                .setPackage(getPackageName())
                .putExtra(EXTRA_STATE, state)
                .putExtra(EXTRA_DETAIL, detail);
        sendBroadcast(update);
    }

    private void stopListening() {
        running = false;
        disconnectActiveConnection();
        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
    }

    private void disconnectActiveConnection() {
        HttpURLConnection connection = activeConnection;
        activeConnection = null;
        if (connection != null) {
            connection.disconnect();
        }
    }
}
