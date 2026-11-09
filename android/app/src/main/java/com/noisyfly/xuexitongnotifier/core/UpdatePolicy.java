package com.noisyfly.xuexitongnotifier.core;
import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigInteger;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdatePolicy {
    private static final Pattern VERSION=Pattern.compile("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?");
    private static final String REPO="/noisy-fly/xuexitong-notifier/releases/download/";
    public static final long MAX_SIZE=100L*1024*1024;
    private UpdatePolicy() {}
    private static Matcher version(String value) { Matcher m=VERSION.matcher(value); if(!m.matches()) throw new IllegalArgumentException("无法识别版本号"); return m; }
    public static boolean newer(String remote,String local) {
        Matcher a=version(remote),b=version(local);
        for(int i=1;i<=3;i++) { int c=new BigInteger(a.group(i)).compareTo(new BigInteger(b.group(i))); if(c!=0) return c>0; }
        String x=a.group(4),y=b.group(4); if(x==null||y==null) return x==null&&y!=null;
        String[] as=x.split("\\."),bs=y.split("\\.");
        for(int i=0;i<Math.min(as.length,bs.length);i++) { boolean an=as[i].matches("[0-9]+"),bn=bs[i].matches("[0-9]+"); int c=an&&bn?new BigInteger(as[i]).compareTo(new BigInteger(bs[i])):an!=bn?(an?-1:1):as[i].compareTo(bs[i]); if(c!=0) return c>0; }
        return as.length>bs.length;
    }
    public static boolean shouldPrompt(String remote,String local,String ignored,boolean manual) { return newer(remote,local) && (manual || !remote.equals(ignored)); }
    public static final class Release {
        public final String tag,url,digest; public final long size;
        Release(String t,String u,String d,long s) { tag=t; url=u; digest=d; size=s; }
    }
    public static Release parse(JSONObject json) {
        String tag=json.optString("tag_name"); version(tag);
        if(json.optBoolean("draft")||json.optBoolean("prerelease")) throw new IllegalArgumentException("没有可用的正式版");
        String name="xuexitong-notifier-"+tag+".apk"; JSONArray assets=json.optJSONArray("assets"); JSONObject asset=null;
        if(assets!=null) for(int i=0;i<assets.length();i++) { JSONObject a=assets.optJSONObject(i); if(a!=null && name.equals(a.optString("name"))) { if(asset!=null) throw new IllegalArgumentException("安装包不唯一"); asset=a; } }
        if(asset==null) throw new IllegalArgumentException("未找到安装包");
        String url=asset.optString("browser_download_url"),digest=asset.optString("digest"); long size=asset.optLong("size",0);
        try { URI u=new URI(url); if(!"https".equals(u.getScheme()) || !"github.com".equals(u.getHost()) || u.getUserInfo()!=null || (u.getPort()!=-1&&u.getPort()!=443) || !u.getPath().equals(REPO+tag+"/"+name) || u.getQuery()!=null || u.getFragment()!=null) throw new Exception(); }
        catch(Exception ex) { throw new IllegalArgumentException("安装包地址无效"); }
        if(!digest.matches("sha256:[0-9a-fA-F]{64}") || size<1 || size>MAX_SIZE) throw new IllegalArgumentException("安装包校验信息无效");
        return new Release(tag,url,digest.substring(7).toLowerCase(java.util.Locale.ROOT),size);
    }
}
