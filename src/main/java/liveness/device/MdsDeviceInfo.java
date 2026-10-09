package liveness.device;

import java.util.Collections;
import java.util.List;

public class MdsDeviceInfo {
    private final String deviceId;
    private final String serialNumber;
    private final String deviceCode;
    private final String deviceServiceVersion;
    private final String deviceSubId;
    private final String digitalId;
    private final String certFingerprint;
    private final String purpose;
    private final String env;
    private final List<String> specVersion;
    private final String bioType;
    private final String status;

    private MdsDeviceInfo(Builder builder) {
        this.deviceId = builder.deviceId;
        this.serialNumber = builder.serialNumber;
        this.deviceCode = builder.deviceCode;
        this.deviceServiceVersion = builder.deviceServiceVersion;
        this.deviceSubId = builder.deviceSubId;
        this.digitalId = builder.digitalId;
        this.certFingerprint = builder.certFingerprint;
        this.purpose = builder.purpose;
        this.env = builder.env;
        this.specVersion = builder.specVersion == null ? Collections.emptyList() : Collections.unmodifiableList(builder.specVersion);
        this.bioType = builder.bioType;
        this.status = builder.status;
    }

    public String getDeviceId() { return deviceId; }
    public String getSerialNumber() { return serialNumber; }
    public String getDeviceCode() { return deviceCode; }
    public String getDeviceServiceVersion() { return deviceServiceVersion; }
    public String getDeviceSubId() { return deviceSubId; }
    public String getDigitalId() { return digitalId; }
    public String getCertFingerprint() { return certFingerprint; }
    public String getPurpose() { return purpose; }
    public String getEnv() { return env; }
    public List<String> getSpecVersion() { return specVersion; }
    public String getBioType() { return bioType; }
    public String getStatus() { return status; }

    public boolean isReady() {
        return "Ready".equalsIgnoreCase(status);
    }

    public static class Builder {
        private String deviceId;
        private String serialNumber;
        private String deviceCode;
        private String deviceServiceVersion;
        private String deviceSubId;
        private String digitalId;
        private String certFingerprint;
        private String purpose;
        private String env;
        private List<String> specVersion;
        private String bioType;
        private String status;

        public Builder deviceId(String deviceId) { this.deviceId = deviceId; return this; }
        public Builder serialNumber(String serialNumber) { this.serialNumber = serialNumber; return this; }
        public Builder deviceCode(String deviceCode) { this.deviceCode = deviceCode; return this; }
        public Builder deviceServiceVersion(String deviceServiceVersion) { this.deviceServiceVersion = deviceServiceVersion; return this; }
        public Builder deviceSubId(String deviceSubId) { this.deviceSubId = deviceSubId; return this; }
        public Builder digitalId(String digitalId) { this.digitalId = digitalId; return this; }
        public Builder certFingerprint(String certFingerprint) { this.certFingerprint = certFingerprint; return this; }
        public Builder purpose(String purpose) { this.purpose = purpose; return this; }
        public Builder env(String env) { this.env = env; return this; }
        public Builder specVersion(List<String> specVersion) { this.specVersion = specVersion; return this; }
        public Builder bioType(String bioType) { this.bioType = bioType; return this; }
        public Builder status(String status) { this.status = status; return this; }

        public MdsDeviceInfo build() {
            return new MdsDeviceInfo(this);
        }
    }
}
