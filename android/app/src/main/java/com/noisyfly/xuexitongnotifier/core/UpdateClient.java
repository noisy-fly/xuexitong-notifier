package com.noisyfly.xuexitongnotifier.core;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Locale;

public final class UpdateClient {
    private static final String API="https://api.github.com/repos/noisy-fly/xuexitong-notifier/releases/latest";
    private UpdateClient() {}
    private static HttpURLConnection open(String url,boolean binary) throws Exception {
        for(int redirects=0;redirects<5;redirects++) {
            URI uri=new URI(url); String host=uri.getHost();
            if(!"https".equals(uri.getScheme()) || uri.getUserInfo()!=null || (uri.getPort()!=-1 && uri.getPort()!=443) || host==null || !(host.equals("api.github.com") || host.equals("github.com") || host.endsWith(".githubusercontent.com"))) throw new IOException("更新地址不受信任");
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setInstanceFollowRedirects(false); c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setRequestProperty("User-Agent","XuexitongNotifier");
            c.setRequestProperty("Accept",binary?"application/octet-stream":"application/vnd.github+json");
            int code=c.getResponseCode();
            if(code>=300 && code<400) { String next=c.getHeaderField("Location"); c.disconnect(); if(next==null) throw new IOException("更新跳转无效"); url=uri.resolve(next).toString(); continue; }
            if(code!=200) { c.disconnect(); throw new IOException(code==401||code==403||code==404?"无法访问更新，请检查网络或仓库可用性":"检查更新失败（HTTP "+code+"）"); }
            return c;
        }
        throw new IOException("更新跳转次数过多");
    }
    public static UpdatePolicy.Release fetch() throws Exception {
        HttpURLConnection c=open(API,false);
        try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buf=new byte[8192]; int n,total=0; while((n=in.read(buf))!=-1) { total+=n; if(total>1024*1024) throw new IOException("版本信息过大"); out.write(buf,0,n); }
            return UpdatePolicy.parse(new JSONObject(out.toString("UTF-8")));
        } finally { c.disconnect(); }
    }
    static void copyVerified(InputStream in,OutputStream out,long size,String expected) throws Exception {
        MessageDigest sha=MessageDigest.getInstance("SHA-256"); long total=0; byte[] b=new byte[32768]; int n;
        while((n=in.read(b))!=-1) { total+=n; if(total>size || total>UpdatePolicy.MAX_SIZE) throw new IOException("安装包大小异常"); out.write(b,0,n); sha.update(b,0,n); }
        StringBuilder hash=new StringBuilder(); for(byte value:sha.digest()) hash.append(String.format(Locale.ROOT,"%02x",value&255));
        if(total!=size || !hash.toString().equals(expected)) throw new IOException("安装包校验失败");
    }
    public static File download(UpdatePolicy.Release release,File directory) throws Exception {
        String url=release.url;
        File temp=File.createTempFile("update-", ".part",directory),target=new File(directory,"update.apk");
        try {
            HttpURLConnection c=open(url,true);
            try(InputStream in=c.getInputStream(); OutputStream out=new FileOutputStream(temp)) {
                copyVerified(in,out,release.size,release.digest);
            } finally { c.disconnect(); }
            if(target.exists() && !target.delete()) throw new IOException("无法替换旧安装包");
            if(!temp.renameTo(target)) throw new IOException("无法保存安装包"); return target;
        } finally { if(temp.exists()) temp.delete(); }
    }
}
