package com.noisyfly.xuexitongnotifier.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.noisyfly.xuexitongnotifier.MainActivity;
import com.noisyfly.xuexitongnotifier.R;
import com.noisyfly.xuexitongnotifier.core.Deadline;
import com.noisyfly.xuexitongnotifier.core.ReminderPolicy;
import com.noisyfly.xuexitongnotifier.core.TaskState;
import com.noisyfly.xuexitongnotifier.data.HomeworkStore;
import com.noisyfly.xuexitongnotifier.data.ChapterCache;
import org.json.JSONArray;
import org.json.JSONObject;

public final class HomeworkAlarms extends BroadcastReceiver {
    public static void showTest(Context c) {
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        String channel="homework_deadlines";
        manager.createNotificationChannel(new NotificationChannel(channel,"作业截止提醒",NotificationManager.IMPORTANCE_HIGH));
        PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        manager.notify(7332,new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("测试科目:测试作业").setContentText("距截止:1d0h0m").setContentIntent(open).setAutoCancel(true).build());
    }
    private static PendingIntent pending(Context c,int id,Intent i) {
        return PendingIntent.getBroadcast(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void schedule(Context c) {
        HomeworkStore store=new HomeworkStore(c); AlarmManager manager=c.getSystemService(AlarmManager.class);
        int previous=store.prefs().getInt("alarm_count",0);
        for(int i=0;i<previous;i++) manager.cancel(pending(c,i,new Intent(c,HomeworkAlarms.class)));
        int count=0;
        if (store.loggedIn() && store.prefs().getBoolean("popup",false)) try {
            JSONArray items=new JSONArray(store.prefs().getString("items","[]"));
            try { ChapterCache.append(items,ChapterCache.all(store)); } catch(org.json.JSONException ignored) { }
            long now=System.currentTimeMillis(); long advance=store.prefs().getInt("advance",60)*60_000L;
            for(int i=0;i<items.length();i++) {
                JSONObject item=items.getJSONObject(i); long due=item.optLong("deadline");
                if(!ReminderPolicy.eligible(item,store.selectedCourses(),now) || TaskState.resolve(item,store.prefs().getString("manual_"+item.optString("id"),""))==TaskState.DONE) continue;
                String key=Integer.toHexString((item.optString("id")+":"+due).hashCode());
                if(store.prefs().getBoolean("sent_"+key,false)) continue;
                String status=item.optString("status");
                String title=item.optString("title")+("exam".equals(item.optString("kind"))?"（考试）":((status.contains("互评")||status.contains("互評"))?"（待互评）":""));
                if(status.contains("状态未知")) title+="（完成状态未知）";
                Intent intent=new Intent(c,HomeworkAlarms.class).putExtra("course",item.optString("course"))
                        .putExtra("title",title).putExtra("deadline",due).putExtra("key",key).putExtra("itemId",item.optString("id")).putExtra("courseKey",item.optString("courseKey"));
                PendingIntent pi=pending(c,count++,intent); long when=Math.max(now+1000,due-advance);
                if(Build.VERSION.SDK_INT<31 || manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
                else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
            }
        } catch(Exception ignored) { }
        store.prefs().edit().putInt("alarm_count",count).apply();
    }
    @Override public void onReceive(Context c,Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) { HomeworkSync.schedule(c); schedule(c); return; }
        HomeworkStore store=new HomeworkStore(c);
        String key=intent.getStringExtra("key"); long due=intent.getLongExtra("deadline",0);
        if (!store.loggedIn() || !store.prefs().getBoolean("popup",false) || !store.selectedCourses().contains(intent.getStringExtra("courseKey")) || key==null || due<=System.currentTimeMillis() || store.prefs().getBoolean("sent_"+key,false)) return;
        try {
            JSONArray current=new JSONArray(store.prefs().getString("items","[]"));
            try { ChapterCache.append(current,ChapterCache.all(store)); } catch(org.json.JSONException ignored) { }
            String itemId=intent.getStringExtra("itemId");
            if(!ReminderPolicy.isCurrent(current,itemId,due,store.selectedCourses(),System.currentTimeMillis())) return;
            for(int i=0;i<current.length();i++) { JSONObject item=current.optJSONObject(i); if(item!=null && item.optString("id").equals(itemId) && TaskState.resolve(item,store.prefs().getString("manual_"+itemId,""))==TaskState.DONE) return; }
        } catch(Exception e) { return; }
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        if(!manager.areNotificationsEnabled()) return;
        String channel="homework_deadlines";
        manager.createNotificationChannel(new NotificationChannel(channel,"作业截止提醒",NotificationManager.IMPORTANCE_HIGH));
        String title=intent.getStringExtra("course")+":"+intent.getStringExtra("title");
        String text=Deadline.formatRemaining(System.currentTimeMillis(),due);
        PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).build();
        manager.notify(key.hashCode(),notification);
        store.prefs().edit().putBoolean("sent_"+key,true).apply();
    }
}
