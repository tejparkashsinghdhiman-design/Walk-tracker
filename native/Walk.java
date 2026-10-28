package com.walktracker.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.SystemClock;
import com.getcapacitor.JSObject;

/** Native walk session: steps from the hardware counter, distance from smoothed GPS. */
class Walk {
  static boolean active, running, accel;
  static double dist, estD, stride = 0.7;
  static long steps, ms, since, lastFix, lastStepT, anchorT, kT, saved;
  static float acc = -1;
  private static double kLat, kLng, kVar;
  private static boolean kOn, loaded;
  private static Location anchor;

  static synchronized void load(Context c) {
    if (loaded) return;
    loaded = true;
    SharedPreferences p = StepStore.p(c);
    active = p.getBoolean("w_a", false);
    if (!active) return;
    running = p.getBoolean("w_r", false);
    accel = p.getBoolean("w_x", false);
    dist = p.getFloat("w_d", 0);
    estD = p.getFloat("w_e", 0);
    steps = p.getLong("w_s", 0);
    ms = p.getLong("w_m", 0);
    stride = p.getFloat("w_k", 0.7f);
    since = lastFix = SystemClock.elapsedRealtime();
  }

  static synchronized long elapsed() { return ms + (running ? SystemClock.elapsedRealtime() - since : 0); }

  private static void reset() { kOn = false; anchor = null; }

  static synchronized void start(Context c, double strideM, boolean useAccel) {
    loaded = true; active = true; running = true; accel = useAccel; lastStepT = 0;
    dist = 0; estD = 0; steps = 0; ms = 0; acc = -1;
    stride = strideM > 0.2 && strideM < 1.5 ? strideM : 0.7;
    since = lastFix = SystemClock.elapsedRealtime();
    reset(); persist(c, true);
  }

  static synchronized void pause(Context c) {
    load(c);
    if (running) { ms = elapsed(); running = false; }
    persist(c, true);
  }

  static synchronized void resume(Context c) {
    load(c);
    if (active && !running) { running = true; since = lastFix = SystemClock.elapsedRealtime(); reset(); }
    persist(c, true);
  }

  static synchronized void stop(Context c) {
    load(c);
    if (running) { ms = elapsed(); running = false; }
    active = false;
    persist(c, true);
  }

  static synchronized void flush(Context c) { persist(c, true); }

  static synchronized void onSteps(Context c, long n) { if (!accel) add(c, n); }

  static synchronized void onAccelSteps(Context c, long n) { if (accel) add(c, n); }

  private static void add(Context c, long n) {
    load(c);
    if (!active || !running) return;
    steps += n;
    lastStepT = SystemClock.elapsedRealtime();
    if (SystemClock.elapsedRealtime() - lastFix > 8000) { dist += n * stride; estD += n * stride; reset(); }
    persist(c, false);
  }

  static synchronized void onFix(Context c, Location l) {
    load(c);
    acc = l.getAccuracy();
    if (!active || !running || acc > 25) return;
    long t = SystemClock.elapsedRealtime();
    lastFix = t;
    double a = Math.max(acc, 3), lat = l.getLatitude(), lng = l.getLongitude();
    if (!kOn) { kLat = lat; kLng = lng; kVar = a * a; kT = t; kOn = true; }
    else {
      kVar += Math.max(0, (t - kT) / 1000.0) * 9;
      double k = kVar / (kVar + a * a);
      kLat += k * (lat - kLat); kLng += k * (lng - kLng);
      kVar *= (1 - k); kT = t;
    }
    Location pt = new Location("k");
    pt.setLatitude(kLat); pt.setLongitude(kLng);
    if (anchor == null) { anchor = pt; anchorT = t; return; }
    if (steps >= 5 && t - lastStepT > 10000) { anchor = pt; anchorT = t; return; } // standing still: ignore GPS wander
    double dts = (t - anchorT) / 1000.0;
    if (dts <= 0) return;
    float d = anchor.distanceTo(pt);
    if (d < Math.max(2, acc * 0.3) || d / dts > 6) return;
    dist += d; anchor = pt; anchorT = t;
    persist(c, false);
  }

  static synchronized JSObject state(Context c) {
    load(c);
    JSObject o = new JSObject();
    o.put("active", active); o.put("running", running); o.put("accel", accel);
    o.put("dist", dist); o.put("estD", estD); o.put("steps", steps);
    o.put("ms", elapsed()); o.put("acc", (double) acc);
    return o;
  }

  private static void persist(Context c, boolean force) {
    long t = SystemClock.elapsedRealtime();
    if (!force && t - saved < 10000) return;
    saved = t;
    StepStore.p(c).edit().putBoolean("w_a", active).putBoolean("w_r", running).putBoolean("w_x", accel)
        .putFloat("w_d", (float) dist).putFloat("w_e", (float) estD).putLong("w_s", steps)
        .putLong("w_m", elapsed()).putFloat("w_k", (float) stride).apply();
  }
}
