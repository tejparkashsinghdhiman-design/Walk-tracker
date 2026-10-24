package com.walktracker.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;

/** Restarts the step service after the phone reboots. */
public class BootReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c, Intent i) {
    try {
      if (Build.VERSION.SDK_INT < 29
          || c.checkSelfPermission("android.permission.ACTIVITY_RECOGNITION") == PackageManager.PERMISSION_GRANTED) {
        ContextCompat.startForegroundService(c, new Intent(c, StepService.class));
      }
    } catch (Exception e) { /* ignore */ }
  }
}
