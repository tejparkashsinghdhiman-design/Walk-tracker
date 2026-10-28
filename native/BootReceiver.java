package com.walktracker.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;

/** Restarts the all-day step service after reboot, app update or quick-boot. */
public class BootReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c, Intent i) {
    try { Walk.load(c); if (Walk.active) Walk.pause(c); } catch (Exception e) { /* ignore */ }
    try {
      if (Build.VERSION.SDK_INT < 29
          || c.checkSelfPermission("android.permission.ACTIVITY_RECOGNITION") == PackageManager.PERMISSION_GRANTED) {
        ContextCompat.startForegroundService(c, new Intent(c, StepService.class));
        SnapReceiver.schedule(c);
      }
    } catch (Exception e) { /* ignore */ }
  }
}
