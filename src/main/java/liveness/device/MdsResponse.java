package liveness.device;

import java.util.List;

public class MdsResponse {
    private List<BiometricData> biometrics;
    private ErrorInfo error;
    private List<MdsDeviceInfo> deviceInfos;

    public List<BiometricData> getBiometrics() { return biometrics; }
    public void setBiometrics(List<BiometricData> biometrics) { this.biometrics = biometrics; }

    public ErrorInfo getError() { return error; }
    public void setError(ErrorInfo error) { this.error = error; }

    public List<MdsDeviceInfo> getDeviceInfos() { return deviceInfos; }
    public void setDeviceInfos(List<MdsDeviceInfo> deviceInfos) { this.deviceInfos = deviceInfos; }

    public static class BiometricData {
        private String bioType;
        private String bioSubType;
        private String bioValue;
        private String bioExtract;
        private String qualityScore;
        private String transactionId;
        private String timestamp;
        private String digitalId;
        private String deviceCode;
        private String purpose;
        private String env;
        private String requestedScore;

        public String getBioType() { return bioType; }
        public void setBioType(String bioType) { this.bioType = bioType; }

        public String getBioSubType() { return bioSubType; }
        public void setBioSubType(String bioSubType) { this.bioSubType = bioSubType; }

        public String getBioValue() { return bioValue; }
        public void setBioValue(String bioValue) { this.bioValue = bioValue; }

        public String getBioExtract() { return bioExtract; }
        public void setBioExtract(String bioExtract) { this.bioExtract = bioExtract; }

        public String getQualityScore() { return qualityScore; }
        public void setQualityScore(String qualityScore) { this.qualityScore = qualityScore; }

        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

        public String getDigitalId() { return digitalId; }
        public void setDigitalId(String digitalId) { this.digitalId = digitalId; }

        public String getDeviceCode() { return deviceCode; }
        public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }

        public String getPurpose() { return purpose; }
        public void setPurpose(String purpose) { this.purpose = purpose; }

        public String getEnv() { return env; }
        public void setEnv(String env) { this.env = env; }

        public String getRequestedScore() { return requestedScore; }
        public void setRequestedScore(String requestedScore) { this.requestedScore = requestedScore; }
    }

    public static class ErrorInfo {
        private String errorCode;
        private String errorMessage;

        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}
