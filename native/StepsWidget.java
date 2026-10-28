package com.walktracker.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Home-screen widget: today's steps, goal progress, distance and calories. */
public class StepsWidget extends AppWidgetProvider {
  @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
    for (int id : ids) m.updateAppWidget(id, views(c));
  }

  static void refresh(Context c) {
    try {
      AppWidgetManager m = AppWidgetManager.getInstance(c);
      int[] ids = m.getAppWidgetIds(new ComponentName(c, StepsWidget.class));
      if (ids.length > 0) {
        RemoteViews v = views(c);
        for (int id : ids) m.updateAppWidget(id, v);
      }
    } catch (Exception e) { /* ignore */ }
  }

  private static int rid(Context c, String name, String type) {
    return c.getResources().getIdentifier(name, type, c.getPackageName());
  }

  private static RemoteViews views(Context c) {
    long n = StepStore.today(c);
    int g = Math.max(1, StepStore.goal(c));
    double km = n * StepStore.stride(c) / 1000.0;
    RemoteViews v = new RemoteViews(c.getPackageName(), rid(c, "widget_steps", "layout"));
    v.setTextViewText(rid(c, "w_steps", "id"), String.format("%,d", n));
    v.setProgressBar(rid(c, "w_bar", "id"), 100, (int) Math.min(100, n * 100 / g), false);
    v.setTextViewText(rid(c, "w_info", "id"),
        String.format("%.2f km  |  %d kcal  |  goal %,d", km, Math.round(0.55 * StepStore.weight(c) * km), g));
    Intent li = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
    if (li != null) {
      v.setOnClickPendingIntent(rid(c, "w_root", "id"), PendingIntent.getActivity(c, 0, li, PendingIntent.FLAG_IMMUTABLE));
    }
    return v;
  }
}
