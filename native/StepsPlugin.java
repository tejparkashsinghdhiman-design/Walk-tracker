package com.walktracker.app;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(name = "Steps", permissions = {
    @Permission(strings = {Manifest.permission.ACTIVITY_RECOGNITION}, alias = "act"),
    @Permission(strings = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, alias = "loc")
})
public class StepsPlugin extends Plugin {
  @PluginMethod
  public void start(PluginCall c) {
    if (Build.VERSION.SDK_INT >= 29 && getPermissionState("act") != PermissionState.GRANTED) {
      requestPermissionForAlias("act", c, "actCb");
      return;
    }
    go(c);
  }

  @PermissionCallback
  private void actCb(PluginCall c) {
    if (getPermissionState("act") == PermissionState.GRANTED) go(c);
    else c.reject("Physical activity permission denied");
  }

  private void go(PluginCall c) {
    Context x = getContext();
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(x, "android.permission.POST_NOTIFICATIONS") != 0) {
      ActivityCompat.requestPermissions(getActivity(), new String[] {"android.permission.POST_NOTIFICATIONS"}, 77);
    }
    try {
      ContextCompat.startForegroundService(x, new Intent(x, StepService.class));
      c.resolve();
    } catch (Exception e) {
      c.reject(String.valueOf(e.getMessage()));
    }
  }

  @PluginMethod
  public void today(PluginCall c) {
    JSObject o = new JSObject();
    o.put("steps", StepStore.today(getContext()));
    SensorManager sm = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
    o.put("sensor", sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null);
    c.resolve(o);
  }

  private boolean fine() {
    return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
  }

  private void svc(String a) {
    Context x = getContext();
    ContextCompat.startForegroundService(x, new Intent(x, WalkService.class).setAction(a));
  }

  @PluginMethod
  public void walkStart(PluginCall c) {
    if (!fine()) { requestPermissionForAlias("loc", c, "locCb"); return; }
    beginWalk(c);
  }

  @PermissionCallback
  private void locCb(PluginCall c) {
    if (fine()) beginWalk(c); else c.reject("Precise location permission denied");
  }

  private void beginWalk(PluginCall c) {
    Walk.start(getContext(), c.getDouble("stride", 0.7), c.getBoolean("accel", false) || !hasStepSensor());
    svc("go");
    c.resolve(Walk.state(getContext()));
  }

  @PluginMethod
  public void walkPause(PluginCall c) { Walk.pause(getContext()); svc("go"); c.resolve(Walk.state(getContext())); }

  @PluginMethod
  public void walkResume(PluginCall c) { Walk.resume(getContext()); svc("go"); c.resolve(Walk.state(getContext())); }

  @PluginMethod
  public void walkStop(PluginCall c) { Walk.stop(getContext()); svc("stop"); c.resolve(Walk.state(getContext())); }

  @PluginMethod
  public void walkState(PluginCall c) { c.resolve(Walk.state(getContext())); }

  @PluginMethod
  public void keepAlive(PluginCall c) {
    try {
      Context x = getContext();
      PowerManager pm = (PowerManager) x.getSystemService(Context.POWER_SERVICE);
      boolean ign = pm.isIgnoringBatteryOptimizations(x.getPackageName());
      if (!ign) {
        Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + x.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        x.startActivity(i);
      }
      JSObject o = new JSObject();
      o.put("ignoring", ign);
      c.resolve(o);
    } catch (Exception e) { c.resolve(); }
  }

  @PluginMethod
  public void setGoal(PluginCall c) { StepStore.setGoal(getContext(), c.getInt("goal", 8000)); c.resolve(); }

  @PluginMethod
  public void status(PluginCall c) {
    Context x = getContext();
    JSObject o = new JSObject();
    o.put("ignoring", ((PowerManager) x.getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(x.getPackageName()));
    c.resolve(o);
  }

  private boolean launch(Intent i) {
    try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); getContext().startActivity(i); return true; } catch (Exception e) { return false; }
  }

  private static final String[][] AUTO = {
    {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
    {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
    {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
    {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
    {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
    {"com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"},
    {"com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"}
  };

  @PluginMethod
  public void openSettings(PluginCall c) {
    String pkg = getContext().getPackageName(), kind = c.getString("kind", "app");
    boolean ok = false;
    if ("battery".equals(kind)) {
      ok = launch(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + pkg)));
    } else if ("autostart".equals(kind)) {
      for (String[] a : AUTO) {
        if (launch(new Intent().setComponent(new ComponentName(a[0], a[1])))) { ok = true; break; }
      }
    }
    if (!ok) launch(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
    JSObject o = new JSObject();
    o.put("opened", ok);
    c.resolve(o);
  }

  @PluginMethod
  public void shareText(PluginCall c) {
    Intent s = new Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, c.getString("text", "")).putExtra(Intent.EXTRA_SUBJECT, "Walk Tracker data");
    Intent ch = Intent.createChooser(s, "Export data");
    ch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try { getContext().startActivity(ch); c.resolve(); } catch (Exception e) { c.reject(String.valueOf(e.getMessage())); }
  }

  private boolean hasStepSensor() {
    SensorManager m = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
    return m.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null;
  }

  @PluginMethod
  public void diag(PluginCall c) {
    Context x = getContext();
    JSObject o = new JSObject();
    o.put("sensor", hasStepSensor());
    o.put("svc", StepService.up);
    long t = StepService.lastEvt;
    o.put("evtAgo", t == 0 ? -1 : (android.os.SystemClock.elapsedRealtime() - t) / 1000);
    o.put("act", Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(x, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED);
    o.put("ignoring", ((PowerManager) x.getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(x.getPackageName()));
    o.put("sdk", Build.VERSION.SDK_INT);
    o.put("brand", Build.MANUFACTURER + " " + Build.MODEL);
    c.resolve(o);
  }

  @PluginMethod
  public void history(PluginCall c) {
    JSArray a = StepStore.history(getContext(), c.getInt("days", 30));
    JSObject o = new JSObject();
    o.put("days", a);
    o.put("hours", StepStore.hours(getContext()));
    c.resolve(o);
  }
}
