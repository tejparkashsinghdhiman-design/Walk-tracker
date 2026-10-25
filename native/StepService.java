package com.walktracker.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;

/** Foreground service that keeps listening to the hardware step counter all day. */
public class StepService extends Service implements SensorEventListener {
  private SensorManager sm;
  private long lastN = 0;
  private boolean on = false;

  @Override public IBinder onBind(Intent i) { return null; }

  private Notification note() {
    Intent li = getPackageManager().getLaunchIntentForPackage(getPackageName());
    PendingIntent pi = PendingIntent.getActivity(this, 0, li, PendingIntent.FLAG_IMMUTABLE);
    return new NotificationCompat.Builder(this, "steps")
        .setSmallIcon(android.R.drawable.ic_menu_directions)
        .setContentTitle(String.format("%,d steps today", StepStore.today(this)))
        .setContentText("Counting in the background. Resets at midnight.")
        .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).build();
  }

  @Override public int onStartCommand(Intent i, int f, int id) {
    if (Build.VERSION.SDK_INT >= 26) {
      ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
          new NotificationChannel("steps", "Step counter", NotificationManager.IMPORTANCE_LOW));
    }
    try {
      if (Build.VERSION.SDK_INT >= 34) startForeground(1, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
      else startForeground(1, note());
    } catch (Exception e) { stopSelf(); return START_NOT_STICKY; }
    if (!on) {
      sm = (SensorManager) getSystemService(SENSOR_SERVICE);
      Sensor s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
      if (s == null) { stopSelf(); return START_NOT_STICKY; }
      on = sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
    }
    return START_STICKY;
  }

  @Override public void onSensorChanged(SensorEvent e) {
    long now = System.currentTimeMillis();
    long wall = now - (SystemClock.elapsedRealtimeNanos() - e.timestamp) / 1000000L;
    if (wall > now || now - wall > 172800000L) wall = now;
    StepStore.onRaw(this, (long) e.values[0], wall);
    long t = SystemClock.elapsedRealtime();
    if (t - lastN > 5000) {
      lastN = t;
      ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1, note());
    }
  }

  @Override public void onAccuracyChanged(Sensor s, int a) {}

  @Override public void onDestroy() {
    if (sm != null) sm.unregisterListener(this);
    StepStore.flush(this);
    super.onDestroy();
  }
}
