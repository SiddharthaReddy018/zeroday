package liveness.capture;

import java.util.Optional;

public class CaptureIntegrityChecker {
    private final FrameSequenceMonitor sequenceMonitor;
    private final DeviceIdentityValidator deviceValidator;
    private final FreshnessChecker freshnessChecker;
    private final SessionTransactionBinder transactionBinder;

    public CaptureIntegrityChecker(FrameSequenceMonitor sequenceMonitor, 
                                   DeviceIdentityValidator deviceValidator,
                                   FreshnessChecker freshnessChecker,
                                   SessionTransactionBinder transactionBinder) {
        this.sequenceMonitor = sequenceMonitor;
        this.deviceValidator = deviceValidator;
        this.freshnessChecker = freshnessChecker;
        this.transactionBinder = transactionBinder;
    }

    public Optional<String> checkFrame(FrameFingerprint fp, String deviceSerial, String deviceCert) {
        if (deviceValidator.isRegistered()) {
            if (!deviceValidator.validateDevice(deviceSerial, deviceCert)) {
                return Optional.of("DEVICE_IDENTITY_CHANGED");
            }
        } else {
            deviceValidator.registerDevice(deviceSerial, deviceCert);
        }

        Optional<FrameSequenceMonitor.IntegrityViolation> violation = sequenceMonitor.addFrame(fp);
        if (violation.isPresent()) {
            return Optional.of(violation.get().name());
        }
        return Optional.empty();
    }

    public boolean verifyRCapture(String returnedTransactionId, long rcaptureTimestamp, long requestTimestamp) {
        return transactionBinder.verifyTransactionId(returnedTransactionId) && 
               freshnessChecker.checkFreshness(rcaptureTimestamp, requestTimestamp);
    }
}
