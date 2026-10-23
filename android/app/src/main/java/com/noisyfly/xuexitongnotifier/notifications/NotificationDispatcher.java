package com.noisyfly.xuexitongnotifier.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.noisyfly.xuexitongnotifier.MainActivity;
import com.noisyfly.xuexitongnotifier.R;
import com.noisyfly.xuexitongnotifier.core.NtfyEvent;

public final class NotificationDispatcher {
    public static final String SERVICE_CHANNEL_ID = "listener_status";
    private static final String MESSAGE_CHANNEL_ID = "learning_messages";
    private static final int MESSAGE_ID_BASE = 2_000;

    private final Context context;
    private final NotificationManager manager;

    public NotificationDispatcher(Context context) {
        this.context = context.getApplicationContext();
        manager = context.getSystemService(NotificationManager.class);
        createChannels();
    }

    public Notification foreground(String detail) {
        return new Notification.Builder(context, SERVICE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.listener_notification_title))
                .setContentText(detail)
                .setContentIntent(contentIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    public void showMessage(NtfyEvent event) {
        Notification notification = new Notification.Builder(context, MESSAGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(event.title())
                .setContentText(event.message())
                .setStyle(new Notification.BigTextStyle().bigText(event.message()))
                .setContentIntent(contentIntent())
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .build();
        manager.notify(MESSAGE_ID_BASE + Math.floorMod(event.id().hashCode(), 10_000), notification);
    }

    public void showTest() {
        Notification notification = new Notification.Builder(context, MESSAGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.test_notification_title))
                .setContentText(context.getString(R.string.test_notification_body))
                .setContentIntent(contentIntent())
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .build();
        manager.notify(1_999, notification);
    }

    private PendingIntent contentIntent() {
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void createChannels() {
        NotificationChannel serviceChannel = new NotificationChannel(
                SERVICE_CHANNEL_ID,
                context.getString(R.string.listener_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        serviceChannel.setDescription(context.getString(R.string.listener_channel_description));

        NotificationChannel messageChannel = new NotificationChannel(
                MESSAGE_CHANNEL_ID,
                context.getString(R.string.message_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        messageChannel.setDescription(context.getString(R.string.message_channel_description));
        messageChannel.enableVibration(true);
        manager.createNotificationChannel(serviceChannel);
        manager.createNotificationChannel(messageChannel);
    }
}
