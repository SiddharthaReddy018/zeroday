package liveness.device;

/** Raised by DeviceAdapter on open/close/capture failures. */
public class DeviceException extends Exception {
    public DeviceException(String message) { super(message); }
    public DeviceException(String message, Throwable cause) { super(message, cause); }
}
