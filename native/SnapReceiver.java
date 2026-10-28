package com.walktracker.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;

/** Alarm-driven step snapshot (every 15 min and just after midnight). Works even when the
 *  foreground service was killed: reads the cumulative counter once and adds the delta. */
public class SnapReceiver extends BroadcastReceiver {
  static void schedule(Context c) {
    try {
      Calendar m = Calendar.getInstance();
      m.add(Calendar.DAY_OF_YEAR, 1);
      m.set(Calendar.HOUR_OF_DAY, 0); m.set(Calendar.MINUTE, 0); m.set(Calendar.SECOND, 1); m.set(Calendar.MILLISECOND, 0);
      long at = Math.min(System.currentTimeMillis() + 15 * 60 * 1000L, m.getTimeInMillis());
      PendingIntent pi = PendingIntent.getBroadcast(c, 7, new Intent(c, SnapReceiver.class),
          PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
      ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    } catch (Exception e) { /* ignore */ }
  }

  @Override public void onReceive(final Context c, Intent i) {
    schedule(c);
    final PendingResult pr = goAsync();
    final SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
    final Sensor s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    final AtomicBoolean done = new AtomicBoolean(false);
    if (s == null) { pr.finish(); return; }
    final SensorEventListener l = new SensorEventListener() {
      @Override public void onSensorChanged(SensorEvent e) {
        if (!done.compareAndSet(false, true)) return;
        sm.unregisterListener(this);
        long now = System.currentTimeMillis();
        long wall = now - (SystemClock.elapsedRealtimeNanos() - e.timestamp) / 1000000L;
        if (wall > now || now - wall > 172800000L) wall = now;
        if (StepStore.onRaw(c, (long) e.values[0], wall)) StepService.goalNote(c);
        StepStore.flush(c);
        StepsWidget.refresh(c);
        pr.finish();
      }
      @Override public void onAccuracyChanged(Sensor x, int a) {}
    };
    sm.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL);
    new Handler(Looper.getMainLooper()).postDelayed(() -> {
      if (done.compareAndSet(false, true)) { sm.unregisterListener(l); pr.finish(); }
    }, 5000);
  }
}
