package com.noisyfly.xuexitongnotifier;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.noisyfly.xuexitongnotifier.core.HomeworkClient;
import com.noisyfly.xuexitongnotifier.service.HomeworkSync;
import java.net.HttpCookie;
import java.util.Locale;

public final class VerificationActivity extends Activity {
    private WebView web;
    private HomeworkClient client;
    private static boolean trusted(String url) {
        Uri uri=Uri.parse(url); String host=uri.getHost();
        if(host==null || uri.getUserInfo()!=null) return false;
        host=host.toLowerCase(Locale.ROOT);
        int port=uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) && (port==-1 || port==443)
                && (host.equals("chaoxing.com") || host.endsWith(".chaoxing.com"));
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); client=HomeworkSync.pendingVerification(this);
        if(client==null || client.verificationUrl()==null || !trusted(client.verificationUrl())) { finish(); return; }
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        TextView explanation=new TextView(this); explanation.setText("请在下方由本人完成学习通验证，然后点击继续同步。"); explanation.setPadding(24,16,24,16); root.addView(explanation);
        web=new WebView(this); web.getSettings().setUserAgentString(HomeworkClient.USER_AGENT); web.getSettings().setJavaScriptEnabled(true); web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {
                String address=request.getUrl().toString();
                if(trusted(address)) return false;
                Toast.makeText(VerificationActivity.this,"已阻止非学习通或不安全的页面跳转",Toast.LENGTH_SHORT).show(); return true;
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        Button done=new Button(this); done.setText("已完成验证，继续同步"); root.addView(done); setContentView(root);
        CookieManager manager=CookieManager.getInstance(); manager.setAcceptCookie(true); manager.setAcceptThirdPartyCookies(web,false);
        for(HttpCookie cookie:client.sessionCookies()) {
            String domain=cookie.getDomain(); if(domain==null) continue;
            String host=domain.startsWith(".")?domain.substring(1):domain;
            if(!trusted("https://"+host+"/")) continue;
            String path=cookie.getPath()==null?"/":cookie.getPath();
            String value=cookie.getName()+"="+cookie.getValue()+"; Domain="+domain+"; Path="+path+"; Secure"+(cookie.isHttpOnly()?"; HttpOnly":"");
            manager.setCookie("https://"+host+path,value);
        }
        manager.flush(); web.loadUrl(client.verificationUrl());
        done.setOnClickListener(v->{
            try {
                String challenge=client.verificationUrl();
                client.importCookies(challenge,manager.getCookie(challenge));
                String current=web.getUrl(); if(current!=null && trusted(current)) client.importCookies(current,manager.getCookie(current));
                if(!HomeworkSync.resumeVerification(this)) throw new IllegalStateException("会话已失效，请返回后重新获取");
                Toast.makeText(this,"正在继续同步",Toast.LENGTH_SHORT).show(); finish();
            } catch(Exception e) { Toast.makeText(this,"无法继续同步，请返回后重新获取",Toast.LENGTH_LONG).show(); }
        });
    }
    @Override protected void onDestroy() { if(web!=null) { web.stopLoading(); web.destroy(); } super.onDestroy(); }
}
