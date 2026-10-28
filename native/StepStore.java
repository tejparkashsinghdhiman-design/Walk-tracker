package com.walktracker.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Daily step totals. Hardware counter is cumulative since boot, so we add deltas.
 *  State is cached in memory and written to disk at most every 10 s (missed steps are
 *  recovered from the next delta). Days and hours are assigned by sensor event time. */
class StepStore {
  private static String cDay = "", cNoti = "";
  private static long cToday = 0, cLast = -1, saved = 0;
  private static final long[] cHrs = new long[24];
  private static int cGoal = 8000;
  private static boolean loaded = false;

  static SharedPreferences p(Context c) { return c.getSharedPreferences("steps", Context.MODE_PRIVATE); }
  static String day(long ms) { return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(ms)); }

  private static void load(Context c) {
    if (loaded) return;
    SharedPreferences p = p(c);
    cDay = p.getString("day", "");
    cToday = p.getLong("today", 0);
    cLast = p.getLong("last", -1);
    cNoti = p.getString("noti", "");
    cGoal = p.getInt("goal", 8000);
    String[] a = p.getString("hrs", "").split(",");
    for (int i = 0; i < 24 && i < a.length; i++) {
      try { cHrs[i] = Long.parseLong(a[i]); } catch (Exception e) { cHrs[i] = 0; }
    }
    loaded = true;
  }

  private static void save(Context c, boolean force) {
    long t = SystemClock.elapsedRealtime();
    if (!force && t - saved < 10000) return;
    saved = t;
    StringBuilder h = new StringBuilder();
    for (int i = 0; i < 24; i++) h.append(i > 0 ? "," : "").append(cHrs[i]);
    p(c).edit().putString("day", cDay).putLong("today", cToday).putLong("last", cLast)
        .putString("noti", cNoti).putString("hrs", h.toString()).apply();
  }

  /** Returns true when today's goal was just reached. */
  static synchronized boolean onRaw(Context c, long raw, long wallMs) {
    load(c);
    String d = day(wallMs);
    if (!cDay.isEmpty() && d.compareTo(cDay) < 0) d = cDay;
    boolean roll = !d.equals(cDay);
    if (roll) {
      if (!cDay.isEmpty()) p(c).edit().putLong("d_" + cDay, cToday).apply();
      cToday = 0; cDay = d; Arrays.fill(cHrs, 0);
    }
    long delta = cLast < 0 ? 0 : (raw >= cLast ? raw - cLast : raw);
    cToday += delta;
    cLast = raw;
    if (delta > 0) {
      Calendar x = Calendar.getInstance();
      x.setTimeInMillis(wallMs);
      cHrs[x.get(Calendar.HOUR_OF_DAY)] += delta;
      Walk.onSteps(c, delta);
    }
    boolean hit = cToday >= cGoal && !cDay.equals(cNoti);
    if (hit) cNoti = cDay;
    save(c, roll || hit);
    return hit;
  }

  static synchronized void setGoal(Context c, int g) {
    load(c);
    cGoal = Math.max(100, g);
    p(c).edit().putInt("goal", cGoal).apply();
  }

  static synchronized int goal(Context c) { load(c); return cGoal; }

  static void setProfile(Context c, double stride, double w) {
    p(c).edit().putFloat("pstride", (float) stride).putFloat("pw", (float) w).apply();
  }

  static double stride(Context c) { return p(c).getFloat("pstride", 0.7f); }

  static double weight(Context c) { return p(c).getFloat("pw", 70f); }

  static synchronized void flush(Context c) { load(c); save(c, true); }

  static synchronized long today(Context c) {
    load(c);
    return day(System.currentTimeMillis()).equals(cDay) ? cToday : 0;
  }

  static synchronized JSArray hours(Context c) {
    load(c);
    JSArray a = new JSArray();
    boolean cur = day(System.currentTimeMillis()).equals(cDay);
    for (int i = 0; i < 24; i++) a.put(cur ? cHrs[i] : 0L);
    return a;
  }

  static synchronized JSArray history(Context c, int days) {
    load(c);
    days = Math.max(1, Math.min(days, 90));
    JSArray a = new JSArray();
    SharedPreferences p = p(c);
    SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd", Locale.US);
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DAY_OF_YEAR, -(days - 1));
    for (int i = 0; i < days; i++) {
      String d = f.format(cal.getTime());
      JSObject o = new JSObject();
      o.put("d", d);
      o.put("n", i == days - 1 ? today(c) : (d.equals(cDay) ? cToday : p.getLong("d_" + d, 0)));
      a.put(o);
      cal.add(Calendar.DAY_OF_YEAR, 1);
    }
    return a;
  }
}
