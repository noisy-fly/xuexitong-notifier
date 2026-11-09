package com.noisyfly.xuexitongnotifier.core;
public final class ManualSyncOutcome {
    private ManualSyncOutcome() {}
    public static String classify(boolean fetched,boolean restricted,int errors) {
        if(!fetched) return "failure";
        if(restricted) return "verification";
        return errors>0?"partial":"success";
    }
}
