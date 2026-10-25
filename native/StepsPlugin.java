package com.walktracker.app;

import android.Manifest;
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
    Walk.start(getContext(), c.getDouble("stride", 0.7));
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
  public void history(PluginCall c) {
    JSArray a = StepStore.history(getContext());
    JSObject o = new JSObject();
    o.put("days", a);
    c.resolve(o);
  }
}
