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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;

/** Foreground service that keeps listening to the hardware step counter all day.
 *  Re-registers every 5 min: this refreshes a stalled listener and delivers the current
 *  counter value, so steps taken while the CPU slept are always caught up. */
public class StepService extends Service implements SensorEventListener {
  static volatile long lastEvt = 0;
  static volatile boolean up = false;
  private SensorManager sm;
  private Sensor sensor;
  private long lastN = 0;
  private final Handler h = new Handler(Looper.getMainLooper());
  private final Runnable rereg = new Runnable() {
    @Override public void run() { listen(); h.postDelayed(this, 300000); }
  };

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

  private void listen() {
    if (sm == null || sensor == null) return;
    sm.unregisterListener(this);
    sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
  }

  @Override public int onStartCommand(Intent i, int f, int id) {
    if (Build.VERSION.SDK_INT >= 26) {
      ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
          new NotificationChannel("steps", "Step counter", NotificationManager.IMPORTANCE_LOW));
    }
    try {
      if (Build.VERSION.SDK_INT >= 34) startForeground(1, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
      else startForeground(1, note());
    } catch (Exception e) { up = false; stopSelf(); return START_NOT_STICKY; }
    if (sensor == null) {
      sm = (SensorManager) getSystemService(SENSOR_SERVICE);
      sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
      if (sensor == null) { up = false; stopSelf(); return START_NOT_STICKY; }
    }
    up = true;
    listen();
    h.removeCallbacks(rereg);
    h.postDelayed(rereg, 300000);
    return START_STICKY;
  }

  @Override public void onSensorChanged(SensorEvent e) {
    lastEvt = SystemClock.elapsedRealtime();
    long now = System.currentTimeMillis();
    long wall = now - (SystemClock.elapsedRealtimeNanos() - e.timestamp) / 1000000L;
    if (wall > now || now - wall > 172800000L) wall = now;
    if (StepStore.onRaw(this, (long) e.values[0], wall)) goalNote();
    long t = SystemClock.elapsedRealtime();
    if (t - lastN > 5000) {
      lastN = t;
      ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1, note());
    }
  }

  private void goalNote() {
    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel("goal", "Goal reached", NotificationManager.IMPORTANCE_DEFAULT));
    nm.notify(3, new NotificationCompat.Builder(this, "goal").setSmallIcon(android.R.drawable.star_on)
        .setContentTitle("Daily goal reached")
        .setContentText(String.format("You hit %,d steps today. Nice work!", StepStore.today(this)))
        .setAutoCancel(true).build());
  }

  @Override public void onAccuracyChanged(Sensor s, int a) {}

  @Override public void onDestroy() {
    up = false;
    h.removeCallbacks(rereg);
    if (sm != null) sm.unregisterListener(this);
    StepStore.flush(this);
    super.onDestroy();
  }
}
