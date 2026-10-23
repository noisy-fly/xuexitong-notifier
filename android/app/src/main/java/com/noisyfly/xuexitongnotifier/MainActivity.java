package com.noisyfly.xuexitongnotifier;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.noisyfly.xuexitongnotifier.core.EndpointConfig;
import com.noisyfly.xuexitongnotifier.data.ConfigStore;
import com.noisyfly.xuexitongnotifier.notifications.NotificationDispatcher;
import com.noisyfly.xuexitongnotifier.service.NtfyListenerService;

public final class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;

    private EditText serverInput;
    private EditText topicInput;
    private EditText tokenInput;
    private TextView statusTitle;
    private TextView statusDetail;
    private ConfigStore store;
    private PendingAction pendingAction = PendingAction.NONE;
    private boolean receiverRegistered;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            renderStatus(
                    intent.getStringExtra(NtfyListenerService.EXTRA_STATE),
                    intent.getStringExtra(NtfyListenerService.EXTRA_DETAIL)
            );
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = new ConfigStore(this);
        serverInput = findViewById(R.id.server_input);
        topicInput = findViewById(R.id.topic_input);
        tokenInput = findViewById(R.id.token_input);
        statusTitle = findViewById(R.id.status_title);
        statusDetail = findViewById(R.id.status_detail);

        serverInput.setText(store.serverUrl());
        topicInput.setText(store.topic());
        tokenInput.setText(store.token());

        Button startButton = findViewById(R.id.start_button);
        Button stopButton = findViewById(R.id.stop_button);
        Button testButton = findViewById(R.id.test_button);
        startButton.setOnClickListener(view -> saveAndStart());
        stopButton.setOnClickListener(view -> stopListener());
        testButton.setOnClickListener(view -> requestNotificationAction(PendingAction.TEST));
        renderStatus(NtfyListenerService.lastState(), NtfyListenerService.lastDetail());
    }

    @Override
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(NtfyListenerService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
        receiverRegistered = true;
        renderStatus(NtfyListenerService.lastState(), NtfyListenerService.lastDetail());
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(statusReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            runPendingAction();
        } else {
            pendingAction = PendingAction.NONE;
            Toast.makeText(this, R.string.notification_permission_required, Toast.LENGTH_LONG).show();
        }
    }

    private void saveAndStart() {
        try {
            EndpointConfig config = new EndpointConfig(
                    serverInput.getText().toString(),
                    topicInput.getText().toString(),
                    tokenInput.getText().toString()
            );
            store.save(config);
            serverInput.setText(config.serverUrl());
            topicInput.setText(config.topic());
            requestNotificationAction(PendingAction.START);
        } catch (IllegalArgumentException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopListener() {
        store.setEnabled(false);
        NtfyListenerService.stop(this);
        renderStatus("stopped", getString(R.string.status_stopped));
    }

    private void requestNotificationAction(PendingAction action) {
        pendingAction = action;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
            return;
        }
        runPendingAction();
    }

    private void runPendingAction() {
        PendingAction action = pendingAction;
        pendingAction = PendingAction.NONE;
        if (action == PendingAction.START) {
            NtfyListenerService.start(this);
            renderStatus("connecting", getString(R.string.status_connecting));
        } else if (action == PendingAction.TEST) {
            new NotificationDispatcher(this).showTest();
            Toast.makeText(this, R.string.test_notification_sent, Toast.LENGTH_SHORT).show();
        }
    }

    private void renderStatus(String state, String detail) {
        String normalizedState = state == null ? "stopped" : state;
        int titleResource;
        int colorResource;
        switch (normalizedState) {
            case "connected":
                titleResource = R.string.status_title_connected;
                colorResource = R.color.status_ok;
                break;
            case "connecting":
            case "retrying":
                titleResource = R.string.status_title_connecting;
                colorResource = R.color.status_waiting;
                break;
            case "error":
                titleResource = R.string.status_title_error;
                colorResource = R.color.status_error;
                break;
            default:
                titleResource = R.string.status_title_stopped;
                colorResource = R.color.text_secondary;
                break;
        }
        statusTitle.setText(titleResource);
        statusTitle.setTextColor(getColor(colorResource));
        statusDetail.setText(detail == null ? getString(R.string.status_stopped) : detail);
    }

    private enum PendingAction {
        NONE,
        START,
        TEST
    }
}
