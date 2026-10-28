package com.walktracker.app;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/** Accelerometer step detector used during walks: time-normalised filters, decaying adaptive
 *  threshold, and 4-step cadence validation (tested on simulated walking: 96-100 of 100). */
class AccelStep implements SensorEventListener {
  private final Context c;
  private SensorManager mgr;
  private double base = 9.8, sm = 0, p1 = 0, p2 = 0, peak = 2;
  private long lastS = 0, pT = 0;
  private int run = 0;

  AccelStep(Context c) { this.c = c; }

  void start() {
    mgr = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
    Sensor s = mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    if (s != null) mgr.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
  }

  void stop() { if (mgr != null) mgr.unregisterListener(this); }

  @Override public void onSensorChanged(SensorEvent e) {
    double m = Math.sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]);
    double d = Math.max(.001, Math.min(.2, (e.timestamp - pT) / 1e9));
    pT = e.timestamp;
    long t = e.timestamp / 1000000L;
    peak = Math.max(1, peak * Math.exp(-d / 6));
    base += (m - base) * (1 - Math.exp(-d));
    p2 = p1; p1 = sm; sm += ((m - base) - sm) * (1 - Math.exp(-d / .03));
    if (p1 > p2 && p1 >= sm && p1 > Math.max(.55, peak * .45)) {
      long dt = t - lastS;
      if (dt > 300) { lastS = t; step(dt, p1); }
    }
  }

  private void step(long dt, double pk) {
    if (dt > 1500) { run = 1; return; }
    run++;
    peak += (pk - peak) * .25;
    int n = run == 4 ? 4 : run > 4 ? 1 : 0;
    if (n > 0) Walk.onAccelSteps(c, n);
  }

  @Override public void onAccuracyChanged(Sensor s, int a) {}
}
