package com.noisyfly.xuexitongnotifier.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class HomeworkClient {
    public static final String USER_AGENT="Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 Chrome/118.0 Mobile Safari/537.36";
    static final class Restricted extends IOException { Restricted() { super("学习通限制访问（HTTP 202/429），请稍后手动重试"); } }
    interface Transport { String request(String address, String body) throws Exception; }
    private final Transport transport;
    private long lastRequestAt;
    public HomeworkClient() { this.transport=null; }
    HomeworkClient(Transport transport) { this.transport=transport; }
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    java.net.CookieStore cookieStore() { return cookies.getCookieStore(); }
    private String verificationUrl;
    public String verificationUrl() { return verificationUrl; }
    public void clearVerificationUrl() { verificationUrl=null; }
    public List<java.net.HttpCookie> sessionCookies() { return new ArrayList<>(cookies.getCookieStore().getCookies()); }
    public void importCookies(String address,String header) throws Exception {
        URL origin=secureUrl(address);
        if(header==null || header.isEmpty()) return;
        for(String part:header.split(";\\s*")) {
            int eq=part.indexOf('='); if(eq<=0) continue;
            String name=part.substring(0,eq).trim();
            List<java.net.HttpCookie> existing=new ArrayList<>(cookies.getCookieStore().get(origin.toURI()));
            java.net.HttpCookie prior=null;
            for(java.net.HttpCookie old:existing) if(old.getName().equalsIgnoreCase(name)) {
                if(prior==null) prior=old;
                cookies.getCookieStore().remove(null,old);
            }
            java.net.HttpCookie cookie=new java.net.HttpCookie(name,part.substring(eq+1));
            cookie.setVersion(prior==null?0:prior.getVersion());
            if(prior!=null && prior.getDomain()!=null) cookie.setDomain(prior.getDomain());
            cookie.setPath(prior!=null && prior.getPath()!=null?prior.getPath():"/");
            cookie.setSecure(true); cookie.setHttpOnly(prior!=null && prior.isHttpOnly());
            cookies.getCookieStore().add(origin.toURI(),cookie);
        }
    }
    private static final byte[] KEY = "u2oh6Vu^HWe4_AES".getBytes(StandardCharsets.UTF_8);
    public static final class Result {
        public final JSONArray items = new JSONArray();
        public final List<String> errors = new ArrayList<>();
        public final java.util.Set<String> failedCourses = new java.util.HashSet<>();
        public boolean restricted;
        public int reusedChapterSections;
    }
    private String encrypt(String value) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(KEY,"AES"),new IvParameterSpec(KEY));
        return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
    private static String enc(String s) throws Exception { return URLEncoder.encode(s,"UTF-8"); }
    public void login(String user, String password) throws Exception {
        String body="fid=-1&uname="+enc(encrypt(user))+"&password="+enc(encrypt(password))+"&refer="+enc("https%3A%2F%2Fi.chaoxing.com")+"&t=true&forbidotherlogin=0&validate=&doubleFactorLogin=0&independentId=0&independentNameId=0";
        JSONObject result=new JSONObject(request("https://passport2.chaoxing.com/fanyalogin",body));
        if (!result.optBoolean("status",false)) throw new IOException("登录失败："+result.optString("msg2","请检查账号密码或完成平台验证"));
    }
    private List<HomeworkParser.Course> pendingCourses;
    private int nextCourse;
    private Result completed;
    private java.util.Map<String,JSONObject> previousHomework;
    public Result fetchChapterCourse(String selectedKey) throws Exception { return fetchChapterCourse(selectedKey,new JSONArray()); }
    public Result fetchChapterCourse(String selectedKey,JSONArray previous) throws Exception {
        Result result=new Result(); HomeworkParser.Course selected=null;
        for(HomeworkParser.Course course:listCourses()) if(course.key().equals(selectedKey)) { selected=course; break; }
        if(selected==null) throw new IOException("所选课程不在课程列表中，请重新选择课程");
        HomeworkParser.Course c=selected; java.util.Set<String> ids=new java.util.HashSet<>(); boolean partial=false; int requests=0;
        java.util.Map<String,java.util.List<JSONObject>> cachedSections=new java.util.HashMap<>();
        for(int i=0;i<previous.length();i++) { JSONObject old=previous.optJSONObject(i);
            if(old==null || old.optString("chapterId").isEmpty() || !selectedKey.equals(old.optString("courseKey")) || !"chapter".equals(old.optString("kind"))) continue;
            cachedSections.computeIfAbsent(old.optString("chapterId"),k->new java.util.ArrayList<>()).add(old);
        }
        try {
            String params="courseid="+enc(c.id)+"&clazzid="+enc(c.clazz)+"&cpi="+enc(c.cpi);
            String middle=request("https://mooc1.chaoxing.com/visit/stucoursemiddle?"+params+"&ismooc2=1&v=2",null);
            String stu=HomeworkParser.hidden(middle,"enc"),op=HomeworkParser.hidden(middle,"openc");
            if(stu.isEmpty()) throw new IOException("课程章节入口不可用");
            String tree=request("https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse?"+params+"&enc="+enc(stu)+"&openc="+enc(op)+"&fromMiddle=1&ut=s",null);
            if(tree.contains("没有此页面访问权限") || tree.contains("用户登录")) throw new IOException("课程章节不可访问");
            java.util.List<ChapterParser.Section> sections=ChapterParser.sections(tree);
            if(sections.isEmpty()) throw new IOException("未能识别课程章节");
            for(ChapterParser.Section section:sections) {
                java.util.List<JSONObject> cached=cachedSections.getOrDefault(section.id,java.util.Collections.emptyList()); boolean reusable=section.completed;
                for(JSONObject old:cached) if(!old.optBoolean("chapterScanned",false) || old.optBoolean("stale",false) || !old.optBoolean("_display_done",TaskState.resolve(old,"")==TaskState.DONE)) { reusable=false; break; }
                if(reusable && !cached.isEmpty()) { result.reusedChapterSections++; for(JSONObject old:cached) { JSONObject copy=new JSONObject(old.toString()); copy.remove("_display_done"); copy.put("completed",true); copy.put("status","已完成"); if(ids.add(copy.getString("id"))) result.items.put(copy); } continue; }
                int sectionStart=result.items.length(); boolean fullyRead=false; String previousPage=null;
                for(int order=0;order<8;order++) {
                    if(++requests>120) { partial=true; break; }
                    try {
                        String content=request("https://mooc1.chaoxing.com/knowledge/cards?clazzid="+enc(c.clazz)+"&courseid="+enc(c.id)+"&knowledgeid="+enc(section.id)+"&num="+order+"&ut=s&cpi="+enc(c.cpi)+"&v=20160407-1",null);
                        if(content.equals(previousPage)) { fullyRead=true; break; }
                        if(!content.matches("(?s).*mArg\\s*=\\s*\\{.*")) { if(order==0) partial=true; else fullyRead=true; break; }
                        JSONArray records=ChapterParser.cards(content,c.name,c.key(),section.id,section.title,section.completed);
                        for(int i=0;i<records.length();i++) { JSONObject item=records.getJSONObject(i); if(ids.add(item.getString("id"))) result.items.put(item); }
                        previousPage=content; if(order==7) partial=true;
                    } catch(Restricted ex) { throw ex; }
                    catch(Exception ex) { partial=true; break; }
                }
                if(fullyRead) for(int i=sectionStart;i<result.items.length();i++) result.items.getJSONObject(i).put("chapterScanned",true);
                if(requests>120) break;
            }
        } catch(Restricted ex) { result.restricted=true; partial=true; }
        if(partial) { result.errors.add("部分章节无法读取或达到请求上限，测试列表可能不完整"); result.failedCourses.add(selectedKey+"|chapter"); }
        return result;
    }
    private static Result snapshot(Result source) {
        Result copy=new Result();
        for(int i=0;i<source.items.length();i++) copy.items.put(source.items.optJSONObject(i));
        copy.errors.addAll(source.errors); copy.failedCourses.addAll(source.failedCourses);
        return copy;
    }
    private static String courseKey(HomeworkParser.Course c) { return c.key(); }
    public List<HomeworkParser.Course> listCourses() throws Exception {
            String base=request("https://i.chaoxing.com/base",null);
            java.util.regex.Matcher fid=java.util.regex.Pattern.compile("(?:\"fid\"\\s*:\\s*\"?|var fid = ')(\\d+)").matcher(base);
            String f=fid.find()?fid.group(1):"427";
            String html=request("https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata?courseType=1&courseFid="+f+"&courseTeacherid=0&courseOrderBy=0",null);
            List<HomeworkParser.Course> courses=HomeworkParser.courses(html);
            if(courses.isEmpty()) throw new IOException("未能识别课程列表，请检查登录状态或稍后重试");
            return courses;
    }
    public Result fetch() throws Exception { return fetch(null); }
    public Result fetch(java.util.Set<String> selected) throws Exception { return fetch(selected,new JSONArray()); }
    public Result fetch(java.util.Set<String> selected,JSONArray previous) throws Exception {
        if(selected!=null && selected.isEmpty()) return new Result();
        if(pendingCourses==null) {
            previousHomework=new java.util.HashMap<>();
            for(int i=0;i<previous.length();i++) { JSONObject old=previous.optJSONObject(i); if(old!=null && !old.optString("id").isEmpty()) previousHomework.put(old.optString("id"),old); }
            pendingCourses=listCourses();
            nextCourse=0; completed=new Result();
            if(selected!=null) {
                java.util.Set<String> missing=new java.util.HashSet<>(selected);
                for(HomeworkParser.Course course:pendingCourses) missing.remove(course.key());
                if(!missing.isEmpty()) { completed.failedCourses.addAll(missing); completed.errors.add("部分已选课程不在当前课程列表中，请重新选择课程"); }
                pendingCourses.removeIf(c -> !selected.contains(c.key()));
            }
        }
        for(;nextCourse<pendingCourses.size();nextCourse++) {
            HomeworkParser.Course c=pendingCourses.get(nextCourse);
            JSONArray courseItems=new JSONArray(); List<String> courseErrors=new ArrayList<>(); java.util.Set<String> courseFailures=new java.util.HashSet<>();
            try {
                String params="courseid="+enc(c.id)+"&clazzid="+enc(c.clazz)+"&cpi="+enc(c.cpi);
                String page=request("https://mooc1.chaoxing.com/visit/stucoursemiddle?"+params+"&ismooc2=1&v=2",null);
                String work=HomeworkParser.hidden(page,"workEnc"), op=HomeworkParser.hidden(page,"openc"), stu=HomeworkParser.hidden(page,"enc");

                try {
                if(work.isEmpty() || stu.isEmpty()) throw new IOException("课程作业入口不可用");
                int totalPages=1;
                for(int pageNumber=1;pageNumber<=totalPages;pageNumber++) {
                String list=request("https://mooc1.chaoxing.com/mooc2/work/list?courseId="+enc(c.id)+"&classId="+enc(c.clazz)+"&cpi="+enc(c.cpi)+"&ut=s&openc="+enc(op)+"&enc="+enc(work)+"&t="+System.currentTimeMillis()+"&stuenc="+enc(stu)+"&isdisplaytable=2&pageNum="+pageNumber,null);
                totalPages=Math.max(totalPages,HomeworkParser.pageCount(list));
                long listFetchedAt=System.currentTimeMillis();
                if(list.contains("没有此页面访问权限") || list.contains("用户登录")) throw new IOException("作业列表不可访问");
                List<HomeworkParser.Item> items=HomeworkParser.all(list);
                if(items.isEmpty() && !list.contains("goTask") && !list.contains("暂无") && !list.contains("没有作业") && !list.contains("0/0")) throw new IOException("作业列表格式无法识别");
                for(HomeworkParser.Item item:items) {
                    java.util.regex.Matcher workId=java.util.regex.Pattern.compile("[?&]workId=([^&]+)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(item.url);
                    String identity=workId.find()?workId.group(1):(item.url.isEmpty()?"title:"+item.title:item.url);
                    byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest((courseKey(c)+":"+identity).getBytes(StandardCharsets.UTF_8));
                    StringBuilder id=new StringBuilder(); for(byte value:digest) id.append(String.format(java.util.Locale.ROOT,"%02x",value & 255));
                    JSONObject prior=previousHomework.get(id.toString());
                    boolean reuse=item.completed && !item.url.isEmpty() && prior!=null && courseKey(c).equals(prior.optString("courseKey"))
                            && "homework".equals(prior.optString("kind","homework")) && !prior.optBoolean("stale",false) && prior.optBoolean("_display_done",TaskState.resolve(prior,"")==TaskState.DONE);
                    boolean peer=item.status.contains("互评") || item.status.contains("互評");
                    boolean estimated=reuse?prior.optBoolean("estimated",false):item.remainingMillis>=0 && !peer;
                    long deadline=reuse?prior.optLong("deadline",0):(estimated?listFetchedAt+Math.max(60_000,item.remainingMillis):0); boolean detailFailed=false;
                    if(!estimated && !item.completed && item.url.isEmpty()) { detailFailed=true; courseErrors.add(c.name+" / "+item.title+"：详情链接不可用"); }
                    boolean canReadDetail=!item.completed || isSafeCompletedDetail(item.url);
                    if(!reuse && !estimated && canReadDetail && !item.url.isEmpty()) try {
                        String detailPage=request(item.url,null);
                        deadline=HomeworkParser.deadline(detailPage,System.currentTimeMillis(),item.status);
                        if(deadline==0 && !item.completed) { detailFailed=true; courseErrors.add(c.name+" / "+item.title+"：截止时间无法识别"); }
                    } catch(Restricted ex) { throw ex; }
                    catch(Exception ex) { if(!item.completed) { detailFailed=true; courseErrors.add(c.name+" / "+item.title+"：详情读取失败"); } }
                    JSONObject record=new JSONObject();
                    record.put("course",c.name); record.put("title",item.title); record.put("status",item.status);
                    record.put("id",id.toString()); record.put("courseKey",courseKey(c)); record.put("deadline",deadline); record.put("detailFailed",detailFailed); record.put("estimated",estimated); record.put("completed",item.completed);
                    courseItems.put(record);
                }
                }
                } catch(Restricted ex) { throw ex; }
                catch(Exception ex) { courseFailures.add(courseKey(c)+"|homework"); courseErrors.add(c.name+"：作业未完整获取（"+ex.getMessage()+"）"); }
                String examEnc=HomeworkParser.hidden(page,"examEnc");
                try {
                    if(examEnc.isEmpty() || op.isEmpty()) throw new IOException("考试入口不可用");
                    String examPage=request("https://mooc1.chaoxing.com/exam-ans/mooc2/exam/exam-list?courseid="+enc(c.id)+"&clazzid="+enc(c.clazz)+"&cpi="+enc(c.cpi)+"&ut=s&t="+System.currentTimeMillis()+"&stuenc="+enc(examEnc)+"&enc="+enc(stu)+"&openc="+enc(op),null);
                    if(examPage.contains("没有此页面访问权限") || examPage.contains("用户登录")) throw new IOException("考试列表不可访问");
                    List<ExamParser.Exam> exams=ExamParser.parse(examPage,System.currentTimeMillis());
                    if(exams.isEmpty() && !examPage.contains("goTest") && !examPage.contains("暂无") && !examPage.contains("没有考试")) throw new IOException("考试列表格式无法识别");
                    for(ExamParser.Exam exam:exams) {
                        JSONObject record=new JSONObject(); record.put("course",c.name); record.put("title",exam.title); record.put("status",exam.status);
                        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest((courseKey(c)+":exam:"+exam.id).getBytes(StandardCharsets.UTF_8));
                        StringBuilder id=new StringBuilder(); for(byte value:digest) id.append(String.format(java.util.Locale.ROOT,"%02x",value & 255));
                        record.put("id",id.toString()); record.put("courseKey",courseKey(c)); record.put("kind","exam"); record.put("deadline",exam.deadline); record.put("detailFailed",exam.deadline==0); record.put("completed",exam.completed); record.put("estimated",exam.estimated);
                        if(exam.deadline==0) courseErrors.add(c.name+" / "+exam.title+"：考试截止时间无法识别");
                        courseItems.put(record);
                    }
                } catch(Restricted ex) { throw ex; }
                catch(Exception ex) { courseFailures.add(courseKey(c)+"|exam"); courseErrors.add(c.name+"：考试未完整获取（"+ex.getMessage()+"）"); }
                try {
                    String activityUrl="https://mobilelearn.chaoxing.com/v2/apis/active/student/activelist?fid=0&courseId="+enc(c.id)+"&classId="+enc(c.clazz)+"&showNotStartedActive=0&_="+System.currentTimeMillis();
                    JSONArray activities=TaskParser.activities(new JSONObject(request(activityUrl,null)),c.name,courseKey(c));
                    for(int i=0;i<activities.length();i++) courseItems.put(activities.getJSONObject(i));
                } catch(Restricted ex) { throw ex; }
                catch(Exception ex) { courseFailures.add(courseKey(c)+"|activity"); courseErrors.add(c.name+"：课堂活动未完整获取"); }
                try {
                    String taskUrl="https://mobilelearn.chaoxing.com/v2/apis/active/getData?DB_STRATEGY=DEFAULT&courseId="+enc(c.id)+"&classId="+enc(c.clazz);
                    JSONObject payload=new JSONObject(request(taskUrl,null));
                    JSONArray summaries=TaskParser.summaries(payload,c.name,courseKey(c));
                    JSONArray packages=payload.getJSONArray("data");
                    for(int i=0;i<packages.length();i++) {
                        try {
                            JSONArray plans=expandTaskPackage(packages.getJSONObject(i),c);
                            if(plans.length()==0) throw new IOException("任务包明细为空");
                            for(int j=0;j<plans.length();j++) courseItems.put(plans.getJSONObject(j));
                        } catch(Restricted ex) { throw ex; }
                        catch(Exception ex) { courseItems.put(summaries.getJSONObject(i)); courseFailures.add(courseKey(c)+"|task"); courseErrors.add(c.name+"：课程任务包明细未完整读取"); }
                    }
                } catch(Restricted ex) { throw ex; }
                catch(Exception ex) { courseFailures.add(courseKey(c)+"|task"); courseErrors.add(c.name+"：课程任务未完整获取"); }
                for(int i=0;i<courseItems.length();i++) completed.items.put(courseItems.getJSONObject(i));
                completed.errors.addAll(courseErrors);
                completed.failedCourses.addAll(courseFailures);
            } catch(Restricted ex) {
                Result partial=snapshot(completed); partial.errors.add(c.name+"："+ex.getMessage()); partial.restricted=true;
                for(int i=nextCourse;i<pendingCourses.size();i++) partial.failedCourses.add(courseKey(pendingCourses.get(i)));
                return partial;
            } catch(Exception ex) {
                completed.failedCourses.add(courseKey(c)); completed.errors.add(c.name+"："+ex.getMessage());
            }
        }
        Result result=snapshot(completed); pendingCourses=null; completed=null; previousHomework=null; nextCourse=0;
        return result;
    }
    private static boolean isSafeCompletedDetail(String address) {
        try { return secureUrl(address).getPath().endsWith("/work/task"); }
        catch(Exception e) { return false; }
    }
    private JSONArray expandTaskPackage(JSONObject task,HomeworkParser.Course course) throws Exception {
        String taskId=task.optString("id",""); if(taskId.isEmpty()) throw new IOException("任务包缺少标识");
        String landingUrl="https://task.chaoxing.com/api/v1/middlePageApi/jumpStudyPlanList?taskId="+enc(taskId)+"&moocClassId="+enc(course.clazz);
        String landing=request(landingUrl,null,"https://mobilelearn.chaoxing.com/");
        java.util.regex.Pattern tokenPattern=java.util.regex.Pattern.compile("(?:const|let|var)\\s+eTaskUserId\\s*=\\s*['\"]([^'\"]+)['\"]");
        java.util.regex.Matcher token=tokenPattern.matcher(landing);
        if(!token.find()) {
            landingUrl="https://task.chaoxing.com/userStudyPlan/studyPlanSubPage?taskId="+enc(taskId)+"&encryJumpGroupId=null";
            landing=request(landingUrl,null,"https://task.chaoxing.com/"); token=tokenPattern.matcher(landing);
            if(!token.find()) throw new IOException("任务包会话不可用");
        }
        String userId=token.group(1);
        String groupUrl="https://task.chaoxing.com/userStudyPlan/getGroupData?encryTaskUserId="+enc(userId);
        JSONArray groups=new JSONObject(request(groupUrl,"",landingUrl)).optJSONArray("data");
        if(groups==null || groups.length()>50) throw new IOException("任务分组格式异常");
        JSONArray all=new JSONArray();
        for(int i=0;i<groups.length();i++) {
            JSONObject group=groups.getJSONObject(i); String groupId=group.optString("encryptGroupId","");
            if(groupId.isEmpty()) throw new IOException("任务分组缺少标识");
            String planUrl="https://task.chaoxing.com/userStudyPlan/getPlanDataByGroupId?encryTaskUserId="+enc(userId)+"&encryGroupId="+enc(groupId);
            JSONArray data=new JSONObject(request(planUrl,"",landingUrl)).optJSONArray("data");
            if(data==null) throw new IOException("任务明细格式异常");
            JSONArray normalized=TaskParser.plans(data,course.name,courseKey(course),taskId);
            for(int j=0;j<normalized.length();j++) all.put(normalized.getJSONObject(j));
        }
        return all;
    }
    static URL secureUrl(String address) throws IOException {
        URL url=new URL(address);
        String host=url.getHost().toLowerCase(java.util.Locale.ROOT);
        boolean trusted=host.equals("chaoxing.com") || host.endsWith(".chaoxing.com");
        if(!trusted || url.getUserInfo()!=null || !(url.getProtocol().equals("https") || url.getProtocol().equals("http")) ||
                (url.getPort()!=-1 && url.getPort()!=(url.getProtocol().equals("https")?443:80)))
            throw new IOException("不支持的学习通地址："+url.getProtocol()+"://"+host);
        if(url.getProtocol().equals("http")) return new URL("https",host,url.getFile());
        return url;
    }
    private String request(String address,String body) throws Exception { return request(address,body,null); }
    private String request(String address,String body,String referer) throws Exception {
        if (transport != null) return transport.request(address,body);
        URL url=secureUrl(address);
        for (int redirects=0;redirects<6;redirects++) {
            long wait=500-(android.os.SystemClock.elapsedRealtime()-lastRequestAt);
            if(lastRequestAt>0 && wait>0) Thread.sleep(wait);
            lastRequestAt=android.os.SystemClock.elapsedRealtime();
            HttpURLConnection c=(HttpURLConnection)url.openConnection();
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(15000); c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent",USER_AGENT);
            if(referer!=null) c.setRequestProperty("Referer",secureUrl(referer).toString());
            Map<String,List<String>> headers=cookies.get(url.toURI(),java.util.Collections.emptyMap());
            for (Map.Entry<String,List<String>> h:headers.entrySet()) if (!h.getValue().isEmpty()) c.setRequestProperty(h.getKey(),String.join("; ",h.getValue()));
            if (body!=null) { c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8"); if(url.getHost().equalsIgnoreCase("task.chaoxing.com")) c.setRequestProperty("X-Requested-With","XMLHttpRequest"); try(OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));} }
            int code=c.getResponseCode(); cookies.put(url.toURI(),c.getHeaderFields());
            if (code>=300 && code<400) { String location=c.getHeaderField("Location"); c.disconnect(); if(location==null) throw new IOException("重定向缺少地址"); url=secureUrl(new URL(url,location).toString()); body=null; continue; }
            if (code==202 || code==429) {
                if(code==202 && url.getPath().toLowerCase(java.util.Locale.ROOT).contains("antispider")) verificationUrl=url.toString();
                c.disconnect(); throw new Restricted();
            }
            if (code!=200) { c.disconnect(); throw new IOException("网络响应 HTTP "+code); }
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buf=new byte[8192]; int n,total=0;
                while((n=in.read(buf))!=-1) { total+=n; if(total>5_000_000) throw new IOException("响应过大"); out.write(buf,0,n); }
                String page=new String(out.toByteArray(),StandardCharsets.UTF_8);
                if(url.getPath().toLowerCase(java.util.Locale.ROOT).contains("antispider") && (page.contains("验证码") || page.contains("9010"))) { verificationUrl=url.toString(); throw new Restricted(); }
                return page;
            } finally { c.disconnect(); }
        }
        throw new IOException("重定向次数过多");
    }
}
