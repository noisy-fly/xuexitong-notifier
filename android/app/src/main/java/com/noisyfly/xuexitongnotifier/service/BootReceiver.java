package com.noisyfly.xuexitongnotifier.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.noisyfly.xuexitongnotifier.data.ConfigStore;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && new ConfigStore(context).isEnabled()) {
            NtfyListenerService.start(context);
        }
    }
}
