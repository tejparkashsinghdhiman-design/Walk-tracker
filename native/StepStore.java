package com.walktracker.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Daily step totals. Hardware counter is cumulative since boot, so we add deltas.
 *  State is cached in memory and written to disk at most every 10 s (missed steps are
 *  recovered from the next delta). Days are assigned by sensor event time. */
class StepStore {
  private static String cDay = "";
  private static long cToday = 0, cLast = -1, saved = 0;
  private static boolean loaded = false;

  static SharedPreferences p(Context c) { return c.getSharedPreferences("steps", Context.MODE_PRIVATE); }
  static String day(long ms) { return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(ms)); }

  private static void load(Context c) {
    if (loaded) return;
    SharedPreferences p = p(c);
    cDay = p.getString("day", "");
    cToday = p.getLong("today", 0);
    cLast = p.getLong("last", -1);
    loaded = true;
  }

  private static void save(Context c, boolean force) {
    long t = SystemClock.elapsedRealtime();
    if (!force && t - saved < 10000) return;
    saved = t;
    p(c).edit().putString("day", cDay).putLong("today", cToday).putLong("last", cLast).apply();
  }

  static synchronized void onRaw(Context c, long raw, long wallMs) {
    load(c);
    String d = day(wallMs);
    if (!cDay.isEmpty() && d.compareTo(cDay) < 0) d = cDay;
    boolean roll = !d.equals(cDay);
    if (roll) {
      if (!cDay.isEmpty()) p(c).edit().putLong("d_" + cDay, cToday).apply();
      cToday = 0;
      cDay = d;
    }
    long delta = cLast < 0 ? 0 : (raw >= cLast ? raw - cLast : raw);
    cToday += delta;
    cLast = raw;
    save(c, roll);
  }

  static synchronized void flush(Context c) { load(c); save(c, true); }

  static synchronized long today(Context c) {
    load(c);
    return day(System.currentTimeMillis()).equals(cDay) ? cToday : 0;
  }

  static JSArray history(Context c) {
    JSArray a = new JSArray();
    SharedPreferences p = p(c);
    SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd", Locale.US);
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DAY_OF_YEAR, -6);
    for (int i = 0; i < 7; i++) {
      String d = f.format(cal.getTime());
      JSObject o = new JSObject();
      o.put("d", d);
      o.put("n", i == 6 ? today(c) : p.getLong("d_" + d, 0));
      a.put(o);
      cal.add(Calendar.DAY_OF_YEAR, 1);
    }
    return a;
  }
}
