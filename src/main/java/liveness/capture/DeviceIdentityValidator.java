package liveness.capture;

public class DeviceIdentityValidator {
    private String registeredSerialNumber = null;
    private String registeredCertFingerprint = null;
    private boolean isRegistered = false;

    public void registerDevice(String serialNumber, String certFingerprint) {
        if (!isRegistered) {
            this.registeredSerialNumber = serialNumber;
            this.registeredCertFingerprint = certFingerprint;
            this.isRegistered = true;
        }
    }

    public boolean validateDevice(String serialNumber, String certFingerprint) {
        if (!isRegistered) return false;
        if (this.registeredSerialNumber == null || this.registeredCertFingerprint == null) return false;
        return this.registeredSerialNumber.equals(serialNumber) && this.registeredCertFingerprint.equals(certFingerprint);
    }

    public boolean isRegistered() {
        return isRegistered;
    }
}
