package com.walktracker.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
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
    @Permission(strings = {Manifest.permission.ACTIVITY_RECOGNITION}, alias = "act")
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

  @PluginMethod
  public void history(PluginCall c) {
    JSArray a = StepStore.history(getContext());
    JSObject o = new JSObject();
    o.put("days", a);
    c.resolve(o);
  }
}
