package com.noisyfly.xuexitongnotifier;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.noisyfly.xuexitongnotifier.core.Deadline;
import com.noisyfly.xuexitongnotifier.core.TaskState;
import com.noisyfly.xuexitongnotifier.core.UpdateClient;
import com.noisyfly.xuexitongnotifier.core.UpdatePolicy;
import com.noisyfly.xuexitongnotifier.core.HomeworkClient;
import com.noisyfly.xuexitongnotifier.core.HomeworkParser;
import com.noisyfly.xuexitongnotifier.core.HomeworkMerge;
import com.noisyfly.xuexitongnotifier.data.HomeworkStore;
import com.noisyfly.xuexitongnotifier.service.HomeworkAlarms;
import com.noisyfly.xuexitongnotifier.service.HomeworkSync;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;

public final class MainActivity extends Activity {
    private HomeworkStore store;
    private LinearLayout root;
    private boolean receiverRegistered;
    private boolean checkingUpdate, pendingManualCheck, awaitingInstallPermission;
    private String updateStatus="正在检查应用更新…";
    private TextView updateStatusView;
    private boolean loadingCourses;
    private boolean showingTasks;
    private boolean resumed;
    private String currentCourse;
    private float touchX,touchY;
    private static final int INK=Color.rgb(35,48,44), MUTED=Color.rgb(100,114,106), ACCENT=Color.rgb(38,110,88);
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN) { touchX=event.getX(); touchY=event.getY(); }
        if(event.getActionMasked()==MotionEvent.ACTION_UP && store!=null && store.loggedIn()) {
            float dx=event.getX()-touchX, dy=event.getY()-touchY;
            if(Math.abs(dx)>dp(100) && Math.abs(dx)>Math.abs(dy)*1.5f && ((!showingTasks && dx<0)||(showingTasks && dx>0))) {
                if(showingTasks && currentCourse!=null) currentCourse=null; else showingTasks=!showingTasks; render(); return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }
    @Override public void onBackPressed() { if(currentCourse!=null) { currentCourse=null; render(); } else if(showingTasks) { showingTasks=false; render(); } else super.onBackPressed(); }
    private final BroadcastReceiver receiver=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) { if(resumed) { render(); showManualResult(); } }
    };
    private void showManualResult() {
        if(!store.prefs().getBoolean("manual_pending",false)) return;
        if(!store.selectedCourses().equals(store.prefs().getStringSet("manual_selection",java.util.Collections.emptySet()))) {
            store.prefs().edit().remove("manual_pending").remove("manual_selection").remove("manual_result").apply(); return;
        }
        String result=store.prefs().getString("manual_result","");
        if(result.isEmpty()) {
            if(HomeworkSync.isRunning() || HomeworkSync.pendingVerification(this)!=null) return;
            result="failure";
        }
        boolean needsVerification="verification".equals(result) && HomeworkSync.pendingVerification(this)!=null;
        android.content.SharedPreferences.Editor edit=store.prefs().edit().remove("manual_result");
        if(!needsVerification) edit.remove("manual_pending").remove("manual_selection");
        edit.apply();
        if("success".equals(result) || "partial".equals(result)) {
            showingTasks=true; currentCourse=null; render();
            boolean partial="partial".equals(result);
            new AlertDialog.Builder(this).setTitle(partial?"部分获取成功":"获取成功")
                .setMessage(partial?"部分内容未能获取，请查看同步问题。已打开作业专区。":"已完成同步，正在显示作业专区。")
                .setPositiveButton("知道了",null).show();
        } else if(needsVerification) {
            new AlertDialog.Builder(this).setTitle("需要学习通验证").setMessage("请先完成验证，之后会继续获取作业。")
                .setPositiveButton("打开验证",(d,w)->startActivity(new Intent(this,VerificationActivity.class))).setNegativeButton("稍后",null).show();
        } else {
            new AlertDialog.Builder(this).setTitle("获取失败").setMessage("verification".equals(result)?"学习通暂时限制访问，请稍后重试。":"获取未完成，请检查网络或查看同步问题后重试。")
                .setPositiveButton("知道了",null).show();
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store=new HomeworkStore(this);
        if(state!=null) awaitingInstallPermission=state.getBoolean("install_permission");
        if(store.prefs().getLong("retry_after",0)==0 && store.prefs().getString("errors","").contains("HTTP 202"))
            store.prefs().edit().putLong("retry_after",System.currentTimeMillis()+6*60*60_000L).apply();
        render();
        if(state==null) checkUpdate(false);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putBoolean("install_permission",awaitingInstallPermission);
    }
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override protected void onStart() {
        super.onStart(); IntentFilter filter=new IntentFilter(HomeworkSync.ACTION);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(receiver,filter);
        receiverRegistered=true;
    }
    @Override protected void onStop() { if(receiverRegistered) { unregisterReceiver(receiver); receiverRegistered=false; } super.onStop(); }
    @Override protected void onResume() { super.onResume(); resumed=true; if(store!=null) {
        if(awaitingInstallPermission && getPackageManager().canRequestPackageInstalls()) { awaitingInstallPermission=false; installUpdate(); }
        if(store.loggedIn()) HomeworkAlarms.schedule(this); render(); showManualResult();
    } }
    @Override protected void onPause() { resumed=false; super.onPause(); }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==42) { HomeworkAlarms.schedule(this); render(); }
    }
    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    private String taskLabel(JSONObject item) {
        String status=item.optString("status");
        String kind=item.optString("kind");
        return item.optString("course")+":"+item.optString("title")+("exam".equals(kind)?"（考试）":"activity".equals(kind)?"（课堂活动）":"task".equals(kind)?"（课程任务）":((status.contains("互评")||status.contains("互評"))?"（待互评）":""))+(status.isEmpty()?"":" · "+status);
    }
    private TextView text(String value,int size,int color) {
        TextView view=new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setLineSpacing(dp(3),1); return view;
    }
    private TextView add(String value,int size,int color) { TextView view=text(value,size,color); view.setPadding(0,dp(8),0,dp(8)); root.addView(view); return view; }
    private Button button(String label,Runnable action) { Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(INK); b.setTextSize(15); b.setBackground(surface(Color.WHITE,16)); b.setMinHeight(dp(52)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(5),0,dp(5)); root.addView(b,lp); b.setOnClickListener(v->action.run()); return b; }
    private EditText input(String hint,boolean password) {
        EditText e=new EditText(this); e.setSingleLine(true); e.setHint(hint);
        e.setInputType(password?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT);
        root.addView(e); return e;
    }
    private GradientDrawable surface(int color,int radius) {
        GradientDrawable bg=new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(radius)); return bg;
    }
    private LinearLayout card() {
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16),dp(13),dp(16),dp(13)); card.setBackground(surface(Color.WHITE,16));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.setMargins(0,dp(5),0,dp(5)); root.addView(card,params); return card;
    }
    private java.util.List<JSONObject> visibleItems() throws Exception {
        JSONArray items=HomeworkMerge.keepSelected(new JSONArray(store.prefs().getString("items","[]")),store.selectedCourses());
        java.util.List<JSONObject> visible=new java.util.ArrayList<>(); long now=System.currentTimeMillis();
        for(int i=0;i<items.length();i++) { JSONObject item=items.getJSONObject(i); if(Deadline.visible(item.optLong("deadline",0),item.optString("status"),now)) visible.add(item); }
        visible.sort((a,b)->Deadline.compare(a.optLong("deadline",0),b.optLong("deadline",0),now)); return visible;
    }
    private void render() {
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(22),dp(24),dp(22),dp(32)); root.setBackgroundColor(Color.rgb(246,248,244));
        scroll.addView(root); setContentView(scroll);
        updateStatusView=null;
        if(!store.loggedIn()) { showingTasks=false; add("作业截止提醒",28,INK); loginScreen(); return; }
        if(showingTasks) { if(currentCourse==null) taskScreen(); else courseScreen(); return; }
        add("学习通 · 作业提醒",27,INK).setTypeface(null,Typeface.BOLD);
        add("让每一次截止，都有准备",14,MUTED);
        updateStatusView=add(updateStatus,12,MUTED);
        java.util.List<JSONObject> visible=null;
        try { visible=visibleItems(); } catch(Exception ignored) { }
        int count=visible==null?0:visible.size();
        LinearLayout overview=card();
        TextView overviewTitle=text("作业专区",21,INK); overviewTitle.setTypeface(null,Typeface.BOLD); overview.addView(overviewTitle);
        overview.addView(text(visible==null?"缓存无法读取，请重新获取":count+" 项未截止或截止时间未知",14,MUTED));
        Button open=new Button(this); open.setText("进入作业专区  →"); open.setAllCaps(false); overview.addView(open);
        open.setOnClickListener(v->{showingTasks=true; currentCourse=null; render();});
        overview.addView(text("在首页向左滑动，也可以进入",12,MUTED));
        add("同步与提醒",20,INK).setTypeface(null,Typeface.BOLD);
        button("选择课程（已选 "+store.selectedCourses().size()+" 门）",this::selectCourses);
        if(store.selectedCourses().isEmpty()) add("请先选择需要提醒的课程。",14,Color.rgb(150,70,25));
        boolean notifications=getSystemService(NotificationManager.class).areNotificationsEnabled();
        boolean battery=getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        boolean exact=Build.VERSION.SDK_INT<31 || getSystemService(AlarmManager.class).canScheduleExactAlarms();
        Switch auto=new Switch(this); auto.setText("应用内定时更新"+(battery?"":"  ·  后台受限，不推荐")); auto.setChecked(store.prefs().getBoolean("auto",false)); root.addView(auto);
        auto.setOnCheckedChangeListener((b,checked)-> {store.prefs().edit().putBoolean("auto",checked).apply(); HomeworkSync.schedule(this);});
        button("更新间隔："+store.prefs().getInt("interval",60)+" 分钟",()->numberDialog("更新间隔（分钟，最少 15）","interval",60,15,10080,()->HomeworkSync.schedule(this)));
        button("手动获取作业",()->{ if(store.selectedCourses().isEmpty()) { Toast.makeText(this,"请先选择课程",Toast.LENGTH_SHORT).show(); selectCourses(); } else if(HomeworkSync.requestManualSync(this)) { Toast.makeText(this,"正在获取作业，请稍候…",Toast.LENGTH_SHORT).show(); } else Toast.makeText(this,"正在同步，请稍候",Toast.LENGTH_SHORT).show(); });
        Switch popup=new Switch(this); popup.setText("弹窗提醒"+((notifications&&exact)?"":"  ·  权限不足，不推荐")); popup.setChecked(store.prefs().getBoolean("popup",false)); root.addView(popup);
        popup.setOnCheckedChangeListener((b,checked)-> {
            store.prefs().edit().putBoolean("popup",checked).apply();
            if(checked && Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},42);
            HomeworkAlarms.schedule(this);
        });
        button("提前提醒："+store.prefs().getInt("advance",60)+" 分钟",()->numberDialog("截止前多少分钟提醒", "advance",60,1,10080,()->HomeworkAlarms.schedule(this)));
        button("测试提醒弹窗",()->{
            if(!getSystemService(NotificationManager.class).areNotificationsEnabled()) { Toast.makeText(this,"请先开启通知权限",Toast.LENGTH_LONG).show(); return; }
            HomeworkAlarms.showTest(this); Toast.makeText(this,"已发送测试通知，请检查系统通知设置",Toast.LENGTH_SHORT).show();
        });
        add("弹窗根据最近一次获取的作业安排，请定期更新。",13,MUTED);
        button("应用状态与权限",this::statusDialog);
        button("检查应用更新",()->checkUpdate(true));
        syncStatus();
        button("退出登录",()->new AlertDialog.Builder(this).setMessage("退出后将清除本机登录信息和作业缓存，停止提醒。")
                .setNegativeButton("取消",null).setPositiveButton("退出",(d,w)->{ store.prefs().edit().putBoolean("auto",false).putBoolean("popup",false).apply(); HomeworkSync.schedule(this); HomeworkAlarms.schedule(this); HomeworkSync.clearVerification(); store.logout(); render(); }).show());
    }
    private void syncStatus() {
        long updated=store.prefs().getLong("updated",0);
        add(updated==0?"尚未获取作业":"上次更新："+DateFormat.getDateTimeInstance().format(new Date(updated)),13,MUTED);
        String lastError=store.prefs().getString("last_error","");
        if(!lastError.isEmpty()) add("更新失败："+lastError+"\n下方为上次成功获取的数据。",14,Color.rgb(150,45,32));
        if(HomeworkSync.pendingVerification(this)!=null) button("打开学习通验证",()->startActivity(new Intent(this,VerificationActivity.class)));
        String errors=store.prefs().getString("errors","");
        if(!errors.isEmpty()) {
            add("部分内容未能获取（"+errors.split("\\n").length+" 项），列表可能不完整。",14,Color.rgb(150,70,25));
            button("查看同步问题",()->new AlertDialog.Builder(this).setTitle("同步问题").setMessage(errors).setPositiveButton("关闭",null).show());
        }
        if(store.prefs().getLong("retry_after",0)>System.currentTimeMillis()) add("学习通暂时限制访问，自动更新已暂停 6 小时；请稍后手动重试。",14,Color.rgb(150,70,25));
    }
    private java.util.Map<String,String> courseNames() throws Exception {
        java.util.Map<String,String> names=new java.util.TreeMap<>();
        JSONObject saved=new JSONObject(store.prefs().getString("course_names","{}"));
        for(String key:store.selectedCourses()) names.put(key,saved.optString(key,""));
        JSONArray all=new JSONArray(store.prefs().getString("items","[]"));
        for(int i=0;i<all.length();i++) { JSONObject item=all.optJSONObject(i); if(item!=null && names.containsKey(item.optString("courseKey"))) names.put(item.optString("courseKey"),item.optString("course","")); }
        return names;
    }
    private int state(JSONObject item) { return TaskState.resolve(item,store.prefs().getString("manual_"+item.optString("id"),"")); }
    private void taskScreen() {
        button("← 返回首页",()->{showingTasks=false; render();});
        add("我的课程",28,INK).setTypeface(null,Typeface.BOLD);
        add("选择课程，查看未截止的学习任务",14,MUTED);
        syncStatus();
        try {
            java.util.List<JSONObject> items=visibleItems(); java.util.Map<String,String> names=courseNames();
            if(names.isEmpty()) { add("还没有选择课程，回到首页选择课程。",15,MUTED); return; }
            int number=0;
            for(java.util.Map.Entry<String,String> entry:names.entrySet()) {
                final String key=entry.getKey(); String name=entry.getValue(); if(name.isEmpty()) name="课程 "+(++number)+"（暂无名称）";
                int pending=0; for(JSONObject item:items) if(key.equals(item.optString("courseKey")) && state(item)==TaskState.PENDING) pending++;
                LinearLayout row=card(); row.setPadding(dp(20),dp(20),dp(20),dp(20)); row.setElevation(dp(2));
                TextView heading=text(name+"  ›",18,INK); heading.setTypeface(null,Typeface.BOLD); row.addView(heading);
                TextView count=text("未完成  "+pending+" 项",14,ACCENT); count.setPadding(0,dp(9),0,0); row.addView(count);
                row.setContentDescription(name+"，未完成 "+pending+" 项，点击查看"); row.setClickable(true); row.setOnClickListener(v->{ currentCourse=key; render(); });
            }
        } catch(Exception e) { add("本地作业缓存无法读取，请重新获取。",14,Color.RED); }
    }
    private void courseScreen() {
        button("← 返回我的课程",()->{currentCourse=null; render();});
        try {
            String name=courseNames().get(currentCourse); if(name==null || name.isEmpty()) name="课程详情";
            add(name,26,INK).setTypeface(null,Typeface.BOLD);
            add("已完成 · 未完成 · 状态未知",14,MUTED);
            add("黄色项目可点击标记完成，再次点击标记未完成",13,MUTED);
            int count=0;
            for(JSONObject item:visibleItems()) {
                if(!currentCourse.equals(item.optString("courseKey"))) continue; count++;
                int state=state(item); boolean manual=TaskState.isUnknown(item);
                int bg=state==TaskState.DONE?Color.rgb(224,244,232):state==TaskState.PENDING?Color.rgb(255,232,231):Color.rgb(255,244,211);
                int fg=state==TaskState.DONE?Color.rgb(35,114,70):state==TaskState.PENDING?Color.rgb(157,54,54):Color.rgb(137,99,14);
                LinearLayout row=card(); row.setBackground(surface(bg,18)); row.setPadding(dp(18),dp(16),dp(18),dp(16));
                String kind=item.optString("kind"); String type="exam".equals(kind)?"考试":"activity".equals(kind)?"课堂活动":"task".equals(kind)?"课程任务":"作业";
                String stateLabel=state==TaskState.DONE?"已完成":state==TaskState.PENDING?"未完成":"状态未知";
                TextView badge=text(type+"  ·  "+stateLabel+(manual && state!=TaskState.UNKNOWN?"（手动）":""),13,fg); badge.setTypeface(null,Typeface.BOLD); row.addView(badge);
                TextView title=text(item.optString("title","未命名项目"),17,INK); title.setTypeface(null,Typeface.BOLD); title.setPadding(0,dp(6),0,dp(6)); row.addView(title);
                long due=item.optLong("deadline",0); String remaining=due==0?"截止时间未知":Deadline.formatRemaining(System.currentTimeMillis(),due)+(item.optBoolean("estimated")?"（约）":"");
                row.addView(text(remaining,14,fg));
                String status=item.optString("status"); if(item.optBoolean("stale")) status+=(status.isEmpty()?"":" · ")+"上次记录，请核实";
                if(!status.isEmpty()) row.addView(text("平台："+status,12,MUTED));
                if(manual && !item.optString("id").isEmpty()) {
                    row.addView(text("点击切换手动状态",12,fg)); row.setClickable(true);
                    row.setContentDescription(item.optString("title")+"，"+stateLabel+"，点击切换手动状态");
                    row.setOnClickListener(v->{ String key="manual_"+item.optString("id"); store.prefs().edit().putString(key,TaskState.next(store.prefs().getString(key,""))).apply(); HomeworkAlarms.schedule(this); int y=((ScrollView)root.getParent()).getScrollY(); render(); root.post(()->((ScrollView)root.getParent()).scrollTo(0,y)); });
                }
            }
            if(count==0) add("这门课程暂无未截止项目。",15,MUTED);
        } catch(Exception e) { add("本地作业缓存无法读取，请重新获取。",14,Color.RED); }
    }
    private void setUpdateStatus(String value) { updateStatus=value; if(updateStatusView!=null) updateStatusView.setText(value); }
    private void checkUpdate(boolean manual) {
        if(checkingUpdate) { pendingManualCheck|=manual; if(manual) Toast.makeText(this,"正在检查更新…",Toast.LENGTH_SHORT).show(); return; }
        checkingUpdate=true; pendingManualCheck=manual; setUpdateStatus("正在检查应用更新…"); if(manual) Toast.makeText(this,"正在检查更新…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            UpdatePolicy.Release release=null; String error=null;
            try { release=UpdateClient.fetch(); } catch(Exception e) { error=e.getMessage(); }
            final UpdatePolicy.Release result=release; final String failure=error;
            runOnUiThread(()->{
                boolean userRequested=pendingManualCheck; checkingUpdate=false; pendingManualCheck=false;
                if(isFinishing()||isDestroyed()) return;
                if(result==null) { setUpdateStatus("更新检查失败，请检查网络"); if(userRequested) new AlertDialog.Builder(this).setTitle("检查更新失败").setMessage(failure==null?"请检查网络后重试。":failure).setPositiveButton("知道了",null).show(); return; }
                try {
                    String current=getPackageManager().getPackageInfo(getPackageName(),0).versionName;
                    setUpdateStatus("已检查更新 · 当前 "+current);
                    if(!UpdatePolicy.shouldPrompt(result.tag,current,getSharedPreferences("updates",MODE_PRIVATE).getString("ignored_release",""),userRequested)) { if(userRequested) Toast.makeText(this,"当前已是最新正式版",Toast.LENGTH_LONG).show(); return; }
                    setUpdateStatus("发现新版本 "+result.tag);
                    new AlertDialog.Builder(this).setTitle("发现新版本 "+result.tag).setMessage("当前版本："+current+"\n下载后会校验安装包，再打开系统安装界面。")
                        .setPositiveButton("立即更新",(d,w)->downloadUpdate(result)).setNeutralButton("忽略此版本",(d,w)->getSharedPreferences("updates",MODE_PRIVATE).edit().putString("ignored_release",result.tag).apply())
                        .setNegativeButton("以后再说",null).show();
                } catch(Exception e) { if(userRequested) Toast.makeText(this,"无法识别版本信息",Toast.LENGTH_LONG).show(); }
            });
        },"update-check").start();
    }
    private void downloadUpdate(UpdatePolicy.Release release) {
        if(getPackageName().endsWith(".debug")) { new AlertDialog.Builder(this).setTitle("调试版无法覆盖升级").setMessage("请先安装正式版，之后可在正式版中使用应用内更新。正式版与调试版的数据相互独立。" ).setPositiveButton("知道了",null).show(); return; }
        android.app.ProgressDialog progress=new android.app.ProgressDialog(this); progress.setMessage("正在下载并校验安装包…"); progress.setCancelable(false); progress.show();
        new Thread(()->{ String error=null; try { UpdateClient.download(release,getCacheDir()); } catch(Exception e) { error=e.getMessage(); }
            final String failure=error; runOnUiThread(()->{ if(isFinishing()||isDestroyed()) return; progress.dismiss();
                if(failure!=null) { new AlertDialog.Builder(this).setTitle("更新下载失败").setMessage(failure).setPositiveButton("关闭",null).show(); return; }
                if(getPackageManager().canRequestPackageInstalls()) installUpdate();
                else new AlertDialog.Builder(this).setTitle("允许安装更新").setMessage("请在系统设置中允许本应用安装更新，然后返回。")
                    .setPositiveButton("打开设置",(d,w)->{ awaitingInstallPermission=true; try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName()))); } catch(Exception e) { awaitingInstallPermission=false; Toast.makeText(this,"无法打开安装权限设置",Toast.LENGTH_LONG).show(); } }).setNegativeButton("取消",null).show();
            }); },"update-download").start();
    }
    private void installUpdate() {
        Uri uri=Uri.parse("content://"+getPackageName()+".updates/update.apk");
        Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(i); } catch(Exception e) { Toast.makeText(this,"无法打开系统安装界面",Toast.LENGTH_LONG).show(); }
    }
    private void loginScreen() {
        add("使用学习通账号登录后，查看各课程作业和截止时间。",15,Color.DKGRAY);
        updateStatusView=add(updateStatus,12,MUTED);
        EditText username=input("学习通账号",false), password=input("密码",true);
        Button login=button("登录",()->{});
        login.setOnClickListener(v->{
            String user=username.getText().toString().trim(), pass=password.getText().toString();
            if(user.isEmpty()||pass.isEmpty()) { Toast.makeText(this,"请输入账号和密码",Toast.LENGTH_SHORT).show(); return; }
            login.setEnabled(false); login.setText("正在登录…");
            new Thread(()->{
                String error=null;
                try { HomeworkClient client=new HomeworkClient(); client.login(user,pass); store.saveCredentials(user,pass); }
                catch(Exception e) { error=e.getMessage()==null?"登录失败，请检查网络":e.getMessage(); }
                final String result=error;
                runOnUiThread(()->{ if(isFinishing()||isDestroyed()) return; if(result!=null) { login.setEnabled(true); login.setText("登录"); Toast.makeText(this,result,Toast.LENGTH_LONG).show(); } else { password.setText(""); render(); selectCourses(); } });
            },"homework-login").start();
        });
        add("账号密码加密保存在本机，用于自动登录。遇到学校验证或验证码时，请先在学习通处理。",13,Color.DKGRAY);
        button("检查应用更新",()->checkUpdate(true));
    }
    private void selectCourses() {
        if(loadingCourses) return;
        loadingCourses=true;
        Toast.makeText(this,"正在获取课程列表…",Toast.LENGTH_SHORT).show();
        String generation=store.prefs().getString("secret","");
        new Thread(()->{
            java.util.List<HomeworkParser.Course> courses=null; String error=null;
            try {
                JSONObject credentials=store.credentials(); if(credentials==null) throw new Exception("请先登录学习通");
                HomeworkClient client=new HomeworkClient(); client.login(credentials.getString("user"),credentials.getString("password"));
                courses=client.listCourses();
            } catch(Exception e) { error=e.getMessage()==null?"获取课程失败":e.getMessage(); }
            final java.util.List<HomeworkParser.Course> result=courses; final String failure=error;
            runOnUiThread(()->{
                loadingCourses=false;
                if(isFinishing()||isDestroyed()||!generation.equals(store.prefs().getString("secret",""))) return;
                if(failure!=null) { new AlertDialog.Builder(this).setTitle("课程列表获取失败").setMessage(failure).setPositiveButton("知道了",null).show(); return; }
                String[] labels=new String[result.size()]; boolean[] checked=new boolean[result.size()];
                java.util.Set<String> chosen=store.selectedCourses();
                for(int i=0;i<result.size();i++) { HomeworkParser.Course c=result.get(i); labels[i]=c.name+"（班级 "+c.clazz+"）"; checked[i]=chosen.contains(c.key()); }
                new AlertDialog.Builder(this).setTitle("选择需要提醒的课程")
                    .setMultiChoiceItems(labels,checked,(dialog,which,isChecked)->checked[which]=isChecked)
                    .setNegativeButton("取消",null).setPositiveButton("保存",(dialog,which)->{
                        java.util.Set<String> selection=new java.util.HashSet<>();
                        for(int i=0;i<result.size();i++) if(checked[i]) selection.add(result.get(i).key());
                        if(!HomeworkSync.updateSelection(this,selection)) { Toast.makeText(this,"保存课程失败，请重试",Toast.LENGTH_LONG).show(); return; }
                        JSONObject names=new JSONObject(); try { for(HomeworkParser.Course c:result) if(selection.contains(c.key())) names.put(c.key(),c.name); store.prefs().edit().putString("course_names",names.toString()).apply(); } catch(Exception ignored) { }
                        render();
                        if(!selection.isEmpty() && !HomeworkSync.sync(this,null)) Toast.makeText(this,"课程已保存，待当前同步结束后可手动获取",Toast.LENGTH_LONG).show();
                    }).show();
            });
        },"course-list").start();
    }
    private void numberDialog(String label,String key,int def,int min,int max,Runnable changed) {
        EditText field=new EditText(this); field.setInputType(InputType.TYPE_CLASS_NUMBER); field.setText(String.valueOf(store.prefs().getInt(key,def)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(label).setView(field).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try { int value=Integer.parseInt(field.getText().toString()); if(value<min||value>max) throw new NumberFormatException(); store.prefs().edit().putInt(key,value).apply(); changed.run(); dialog.dismiss(); render(); }
            catch(NumberFormatException e) { field.setError("请输入 "+min+" 到 "+max+" 之间的数字"); }
        })); dialog.show();
    }
    private void statusDialog() {
        boolean notifications=getSystemService(NotificationManager.class).areNotificationsEnabled();
        boolean battery=getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        boolean exact=Build.VERSION.SDK_INT<31 || getSystemService(AlarmManager.class).canScheduleExactAlarms();
        String status="通知权限："+(notifications?"已开启":"未开启")+"\n电池优化："+(battery?"已允许后台运行":"未豁免，系统可能延迟或停止更新")+"\n精确提醒："+(exact?"可用":"未开启，提醒可能延迟")+"\n\n小米等手机还可能需要在系统设置中允许自启动和后台运行。手动获取始终可用。";
        String[] choices={"通知设置","电池优化设置","精确提醒设置","应用系统设置"};
        new AlertDialog.Builder(this).setTitle("应用状态与权限").setMessage(status).setPositiveButton("打开权限设置",(dialog,which)->new AlertDialog.Builder(this).setTitle("选择设置").setItems(choices,(d,index)->{
            Intent i;
            if(index==0) i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
            else if(index==1) i=new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            else if(index==2 && Build.VERSION.SDK_INT>=31) i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()));
            else i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()));
            try { startActivity(i); } catch(Exception e) { Toast.makeText(this,"请在系统设置中打开对应权限",Toast.LENGTH_LONG).show(); }
        }).setNegativeButton("取消",null).show()).setNegativeButton("关闭",null).show();
    }
}
