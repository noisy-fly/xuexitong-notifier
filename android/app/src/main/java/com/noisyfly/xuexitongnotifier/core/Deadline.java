package com.noisyfly.xuexitongnotifier.core;

public final class Deadline {
    private Deadline() {}

    public static boolean visible(long deadline, String status, long now) {
        return (deadline <= 0 || deadline > now) && !status.contains("已截止") && !status.contains("已结束");
    }

    public static int compare(long first, long second, long now) {
        int a=first<=0?2:(first>now?0:1);
        int b=second<=0?2:(second>now?0:1);
        return a==b?Long.compare(first,second):Integer.compare(a,b);
    }

    public static String formatRemaining(long nowMillis, long deadlineMillis) {
        if (deadlineMillis <= nowMillis) return "已截止";
        long difference = deadlineMillis - nowMillis;
        long minutes = difference / 60_000 + (difference % 60_000 == 0 ? 0 : 1);
        return "距截止:" + (minutes / 1440) + "d" + ((minutes % 1440) / 60) + "h" + (minutes % 60) + "m";
    }
}
