package com.walktracker.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;

/** Foreground service that keeps GPS running for a walk while the screen is off. */
public class WalkService extends Service implements LocationListener {
  private LocationManager lm;
  private boolean on = false;
  private long lastN = 0;

  @Override public IBinder onBind(Intent i) { return null; }

  private Notification note() {
    Intent li = getPackageManager().getLaunchIntentForPackage(getPackageName());
    PendingIntent pi = PendingIntent.getActivity(this, 0, li, PendingIntent.FLAG_IMMUTABLE);
    return new NotificationCompat.Builder(this, "walk")
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle(String.format("%s: %.2f km, %,d steps", Walk.running ? "Walking" : "Paused", Walk.dist / 1000, Walk.steps))
        .setContentText("Walk is being tracked in the background.")
        .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).build();
  }

  private void off() {
    if (on && lm != null) { lm.removeUpdates(this); on = false; }
  }

  @Override public int onStartCommand(Intent i, int f, int id) {
    Walk.load(this);
    if (Build.VERSION.SDK_INT >= 26) {
      ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
          new NotificationChannel("walk", "Walk tracking", NotificationManager.IMPORTANCE_LOW));
    }
    try {
      if (Build.VERSION.SDK_INT >= 34) startForeground(2, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
      else startForeground(2, note());
    } catch (Exception e) { Walk.pause(this); stopSelf(); return START_NOT_STICKY; }
    String act = i == null ? null : i.getAction();
    if ("stop".equals(act) || !Walk.active) {
      off(); stopForeground(true); stopSelf(); return START_NOT_STICKY;
    }
    if (Walk.running) {
      if (!on) {
        try {
          lm = (LocationManager) getSystemService(LOCATION_SERVICE);
          lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
          on = true;
        } catch (Exception e) { /* no GPS permission or provider: steps still count */ }
      }
    } else off();
    return START_STICKY;
  }

  @Override public void onLocationChanged(Location l) {
    Walk.onFix(this, l);
    long t = SystemClock.elapsedRealtime();
    if (t - lastN > 4000) {
      lastN = t;
      ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(2, note());
    }
  }

  @Override public void onStatusChanged(String p, int s, Bundle b) {}
  @Override public void onProviderEnabled(String p) {}
  @Override public void onProviderDisabled(String p) {}

  @Override public void onDestroy() {
    off();
    Walk.flush(this);
    super.onDestroy();
  }
}
