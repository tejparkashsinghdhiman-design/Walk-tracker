package com.walktracker.app;

import android.content.Context;
import android.content.SharedPreferences;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Daily step totals. The hardware counter is cumulative since boot, so we add deltas. */
class StepStore {
  static SharedPreferences p(Context c) { return c.getSharedPreferences("steps", Context.MODE_PRIVATE); }
  static String day() { return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()); }

  static synchronized void onRaw(Context c, long raw) {
    SharedPreferences p = p(c);
    String d = day(), old = p.getString("day", "");
    long last = p.getLong("last", -1), today = p.getLong("today", 0);
    SharedPreferences.Editor e = p.edit();
    if (!d.equals(old)) {
      if (!old.isEmpty()) e.putLong("d_" + old, today);
      today = 0;
    }
    long delta = last < 0 ? 0 : (raw >= last ? raw - last : raw);
    today += delta;
    e.putString("day", d).putLong("today", today).putLong("last", raw).apply();
  }

  static synchronized long today(Context c) {
    SharedPreferences p = p(c);
    return day().equals(p.getString("day", "")) ? p.getLong("today", 0) : 0;
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
