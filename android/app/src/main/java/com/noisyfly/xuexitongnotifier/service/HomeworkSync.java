package com.noisyfly.xuexitongnotifier.service;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import com.noisyfly.xuexitongnotifier.core.HomeworkClient;
import com.noisyfly.xuexitongnotifier.core.HomeworkMerge;
import com.noisyfly.xuexitongnotifier.core.ManualSyncOutcome;
import com.noisyfly.xuexitongnotifier.data.HomeworkStore;
import org.json.JSONArray;
import org.json.JSONObject;

public final class HomeworkSync extends JobService {
    public static final String ACTION="com.noisyfly.xuexitongnotifier.HOMEWORK_UPDATED";
    private static final int JOB_ID=7331;
    private static boolean running;
    private static boolean chapterRunning;
    private static HomeworkClient pendingVerification;
    private static String pendingGeneration;
    private static java.util.Set<String> pendingSelection;
    public static synchronized HomeworkClient pendingVerification(Context context) {
        String current=new HomeworkStore(context).prefs().getString("secret","");
        return !current.isEmpty() && current.equals(pendingGeneration) && new HomeworkStore(context).selectedCourses().equals(pendingSelection)?pendingVerification:null;
    }
    public static synchronized void clearVerification() { pendingVerification=null; pendingGeneration=null; pendingSelection=null; }
    public static synchronized boolean updateSelection(Context context,java.util.Set<String> selection) {
        HomeworkStore store=new HomeworkStore(context);
        try {
            JSONArray filtered=HomeworkMerge.keepSelected(new JSONArray(store.prefs().getString("items","[]")),selection);
            if(!selection.equals(store.selectedCourses())) for(int i=0;i<filtered.length();i++) filtered.getJSONObject(i).put("stale",true);
            android.content.SharedPreferences.Editor edit=store.prefs().edit().putStringSet("selected_courses",new java.util.HashSet<>(selection)).putString("items",filtered.toString()).putString("errors","").putString("last_error","");
            for(String key:store.selectedCourses()) if(!selection.contains(key)) edit.remove("chapters_"+key).remove("chapters_error_"+key);
            boolean saved=edit.commit();
            if(saved) { clearVerification(); schedule(context); HomeworkAlarms.schedule(context); }
            return saved;
        } catch(Exception e) { return false; }
    }
    public static synchronized boolean isRunning() { return running || chapterRunning; }
    public static synchronized boolean beginChapterScan() { if(running || chapterRunning || pendingVerification!=null) return false; chapterRunning=true; return true; }
    public static synchronized void endChapterScan() { chapterRunning=false; }
    public static synchronized boolean requestManualSync(Context context) {
        HomeworkStore store=new HomeworkStore(context); java.util.Set<String> selection=store.selectedCourses();
        if(selection.isEmpty() || chapterRunning) return false;
        store.prefs().edit().putBoolean("manual_pending",true).putStringSet("manual_selection",selection).remove("manual_result").apply();
        if(running) return true;
        clearVerification();
        if(sync(context,null,true,null)) return true;
        store.prefs().edit().remove("manual_pending").remove("manual_selection").apply(); return false;
    }
    public static synchronized boolean sync(Context context, Runnable done) {
        if(running || chapterRunning) return false;
        clearVerification();
        return sync(context,done,true,null);
    }
    public static synchronized boolean resumeVerification(Context context) {
        HomeworkClient client=pendingVerification(context);
        if(client==null || running || chapterRunning) return false;
        client.clearVerificationUrl();
        clearVerification();
        return sync(context,null,true,client);
    }
    private static synchronized boolean sync(Context context, Runnable done, boolean manual, HomeworkClient existing) {
        if (running || chapterRunning) return false;
        java.util.Set<String> selection=new HomeworkStore(context).selectedCourses();
        if(selection.isEmpty()) return false;
        if (!manual && new HomeworkStore(context).prefs().getLong("retry_after",0)>System.currentTimeMillis()) return false;
        running=true;
        Context app=context.getApplicationContext();
        new Thread(() -> {
            HomeworkStore store=new HomeworkStore(app);
            String generation=store.prefs().getString("secret","");
            HomeworkClient client=existing==null?new HomeworkClient():existing;
            boolean successful=false, fetched=false, restricted=false; int errorCount=0;
            try {
                JSONObject credentials=store.credentials();
                if (credentials==null) throw new Exception("请先登录学习通");
                if(existing==null) client.login(credentials.getString("user"),credentials.getString("password"));
                HomeworkClient.Result result=client.fetch(selection,store.displaySnapshot(new JSONArray(store.prefs().getString("items","[]"))));
                synchronized(HomeworkSync.class) {
                    if (generation.isEmpty() || !generation.equals(store.prefs().getString("secret","")) || !selection.equals(store.selectedCourses())) return;
                    String errors=String.join("\n",result.errors);
                    JSONArray items=HomeworkMerge.merge(result.items,new JSONArray(store.prefs().getString("items","[]")),result.failedCourses);
                    store.prefs().edit().putString("items",items.toString()).putString("errors",errors).putLong("updated",System.currentTimeMillis()).putString("last_error","")
                            .putLong("retry_after",result.restricted?System.currentTimeMillis()+6*60*60_000L:0).apply();
                }
                HomeworkAlarms.schedule(app);
                fetched=true; restricted=result.restricted; errorCount=result.errors.size(); successful=!result.restricted;
            } catch(Exception e) {
                if (!generation.isEmpty() && generation.equals(store.prefs().getString("secret","")) && selection.equals(store.selectedCourses())) {
                    String error=e.getMessage()==null?"同步失败":e.getMessage();
                    android.content.SharedPreferences.Editor edit=store.prefs().edit().putString("last_error",error);
                    if(error.contains("学习通限制访问")) edit.putLong("retry_after",System.currentTimeMillis()+6*60*60_000L);
                    edit.apply();
                }
            } finally {
                synchronized(HomeworkSync.class) {
                    if(client.verificationUrl()!=null && generation.equals(store.prefs().getString("secret","")) && selection.equals(store.selectedCourses())) { pendingVerification=client; pendingGeneration=generation; pendingSelection=selection; }
                    if(store.prefs().getBoolean("manual_pending",false) && generation.equals(store.prefs().getString("secret",""))
                            && selection.equals(store.selectedCourses()) && selection.equals(store.prefs().getStringSet("manual_selection",java.util.Collections.emptySet()))
                            && store.prefs().getString("manual_result","").isEmpty())
                        store.prefs().edit().putString("manual_result",pendingVerification!=null?"verification":ManualSyncOutcome.classify(fetched,restricted,errorCount)).apply();
                    running=false;
                }
                app.sendBroadcast(new Intent(ACTION).setPackage(app.getPackageName()).putExtra("success",successful));
                if(done!=null) done.run();
            }
        },"homework-sync").start();
        return true;
    }
    public static void schedule(Context context) {
        HomeworkStore store=new HomeworkStore(context);
        JobScheduler jobs=context.getSystemService(JobScheduler.class);
        jobs.cancel(JOB_ID);
        if (!store.loggedIn() || store.selectedCourses().isEmpty() || !store.prefs().getBoolean("auto",false)) return;
        long minutes=Math.max(15,store.prefs().getInt("interval",60));
        JobInfo job=new JobInfo.Builder(JOB_ID,new ComponentName(context,HomeworkSync.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(minutes*60_000L).setPersisted(true).build();
        jobs.schedule(job);
    }
    @Override public boolean onStartJob(JobParameters params) {
        return sync(this,() -> jobFinished(params,false),false,null);
    }
    @Override public boolean onStopJob(JobParameters params) { return true; }
}
