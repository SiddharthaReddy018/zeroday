package io.oxalpha.liveness.bench;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

import java.util.LinkedHashMap;
import java.util.Map;

/** Device state recorded next to every result, so a number is never separated from the conditions it came from. */
final class DeviceInfo {
    private DeviceInfo() {}

    static Map<String, Object> staticInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("manufacturer", Build.MANUFACTURER);
        m.put("model", Build.MODEL);
        m.put("device", Build.DEVICE);
        m.put("soc", Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : "n/a (<API 31)");
        m.put("android_release", Build.VERSION.RELEASE);
        m.put("sdk_int", Build.VERSION.SDK_INT);
        m.put("abis", String.join(",", Build.SUPPORTED_ABIS));
        m.put("cores", Runtime.getRuntime().availableProcessors());
        m.put("fingerprint", Build.FINGERPRINT);
        return m;
    }

    /** Thermal status, battery temperature (deg C) and level, charging flag. Call before and after a run. */
    static Map<String, Object> dynamicInfo(Context ctx) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (Build.VERSION.SDK_INT >= 29) {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            m.put("thermal_status", pm.getCurrentThermalStatus());   // 0 none .. 6 shutdown
        } else {
            m.put("thermal_status", null);
        }
        Intent b = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) {
            m.put("battery_temp_c", b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE) / 10.0);
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            m.put("battery_pct", scale > 0 ? 100.0 * level / scale : null);
            int plugged = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            m.put("charging", plugged != 0);
        }
        return m;
    }
}
