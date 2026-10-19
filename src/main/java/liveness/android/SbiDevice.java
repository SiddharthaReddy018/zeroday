package liveness.android;

/** A discovered SBI face device (from the Device Discovery / Device Info responses). */
public final class SbiDevice {
    public final String packageName;
    public final String appId;       // callbackId: on Android it is the app id used as action prefix
    public final String deviceId;
    public final String deviceCode;
    public final String status;
    public final String purpose;
    public final String specVersion; // first entry of specVersion[], or null

    public SbiDevice(String packageName, String appId, String deviceId, String deviceCode,
                     String status, String purpose, String specVersion) {
        this.packageName = packageName;
        this.appId = appId;
        this.deviceId = deviceId;
        this.deviceCode = deviceCode;
        this.status = status;
        this.purpose = purpose;
        this.specVersion = specVersion;
    }

    SbiDevice with(String status, String purpose, String deviceId) {
        return new SbiDevice(packageName, appId, deviceId != null ? deviceId : this.deviceId, deviceCode,
                status != null ? status : this.status, purpose != null ? purpose : this.purpose, specVersion);
    }
}
