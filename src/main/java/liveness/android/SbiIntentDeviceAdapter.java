package liveness.android;

import liveness.core.FaceFrame;
import liveness.core.FrameQuality;
import liveness.device.DeviceAdapter;
import liveness.device.DeviceException;
import liveness.util.Sha256;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * T1 final-capture adapter for MOSIP SBI apps on Android.
 * <ul>
 *   <li>{@link #open()}: Device Discovery (io.sbi.device) then Device Info (appId.Info).</li>
 *   <li>{@link #captureFrame()}: unsupported (the SBI app owns the camera UI; no STREAM on Android).</li>
 *   <li>{@link #captureFinal(String)}: rCapture (appId.rCapture) with a spec-compliant transactionId derived
 *       from the engine's nonce-bound id, JWT verification, then cross-stream binding BEFORE the image is returned.</li>
 * </ul>
 * <b>transactionId mapping.</b> The engine's bound id ({@code base:nonce}) contains ':' and exceeds 50 chars, which a
 * spec-compliant SBI rejects (error 112). The wire id is {@code "T" + first 40 hex of SHA-256(boundId)}: unguessable
 * and session-unique because the nonce is hashed in. If the SBI echoes exactly the wire id, the engine is told the
 * original bound id came back; any other value is passed through unchanged and fails the nonce check.
 */
public final class SbiIntentDeviceAdapter implements DeviceAdapter {

    /** Platform image decoder (to RGB), supplied by the glue. */
    public interface RgbDecoder {
        final class Decoded {
            public final byte[] rgb;
            public final int width, height;
            public Decoded(byte[] rgb, int width, int height) {
                this.rgb = rgb;
                this.width = width;
                this.height = height;
            }
        }
        Decoded decode(byte[] encoded);
    }

    private final SbiIntentLauncher launcher;
    private final CrossStreamBinder binder;
    private final Supplier<FaceFrame> hostFrameSupplier;
    private final RgbDecoder decoder;
    private final SbiJwtVerifier verifier;
    private final String env;
    private final String purpose;
    private final long timeoutMs;

    private volatile SbiDevice device;
    private volatile boolean open;
    private volatile String lastReturnedTxId;

    public SbiIntentDeviceAdapter(SbiIntentLauncher launcher, CrossStreamBinder binder,
                                  Supplier<FaceFrame> hostFrameSupplier, RgbDecoder decoder,
                                  SbiJwtVerifier verifier, String env, String purpose, long timeoutMs) {
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.binder = Objects.requireNonNull(binder, "binder");
        this.hostFrameSupplier = Objects.requireNonNull(hostFrameSupplier, "hostFrameSupplier");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.env = env;
        this.purpose = purpose;
        this.timeoutMs = timeoutMs;
    }

    /** Wire transactionId (spec: [A-Za-z0-9-]{4,50}) for an engine-bound id. */
    public static String wireTransactionId(String boundTransactionId) {
        return "T" + Sha256.hexOf(boundTransactionId).substring(0, 40);
    }

    @Override
    public void open() throws DeviceException {
        SbiDevice found = SbiIntentBridge.discover(launcher, timeoutMs);
        device = SbiIntentBridge.info(launcher, found, verifier, timeoutMs);
        open = true;
    }

    @Override
    public FaceFrame captureFrame() throws DeviceException {
        throw new DeviceException("SBI_STREAM_UNSUPPORTED: live frames come from CameraX on Android");
    }

    @Override
    public FaceFrame captureFinal(String boundTransactionId) throws DeviceException {
        if (!open) throw new DeviceException("sbi: adapter not open");
        int sep = boundTransactionId == null ? -1 : boundTransactionId.lastIndexOf(':');
        if (sep < 0 || sep == boundTransactionId.length() - 1) {
            throw new DeviceException("SBI_BAD_TRANSACTION_ID: expected <base>:<nonce>");
        }
        String nonce = boundTransactionId.substring(sep + 1);
        String wireId = wireTransactionId(boundTransactionId);
        lastReturnedTxId = null;

        SbiIntentRequest req = SbiIntentBridge.buildCaptureRequest(device, wireId, env, purpose, null, 40, timeoutMs, Instant.now());
        SbiCaptureResult res = SbiIntentBridge.capture(launcher, req, verifier, timeoutMs + 2000L, env, purpose);

        // Translate: an exact echo of the wire id proves the nonce-bound request was answered.
        String effectiveReturned = wireId.equals(res.transactionId()) ? boundTransactionId : res.transactionId();

        RgbDecoder.Decoded decoded = Binding.check(binder, nonce, effectiveReturned, res, hostFrameSupplier.get(), decoder);

        lastReturnedTxId = effectiveReturned;
        String cert = "sbi:" + (res.deviceCode() == null ? device.appId : res.deviceCode());
        int w = decoded != null ? decoded.width : 0;
        int h = decoded != null ? decoded.height : 0;
        byte[] rgb = decoded != null ? decoded.rgb : null;
        return new FaceFrame(FrameQuality.GOOD, cert, cert.hashCode(),
                res.timestampMs(), 0, w, h, rgb,
                0, 0, w, h, 0.0, 0.0, 0.0, 1, 0.0, 0.0, null, 0L, res.image());
    }

    @Override
    public String lastReturnedTransactionId() { return lastReturnedTxId; }

    @Override
    public String captureMode() { return "HOST_CAMERA+SBI_RCAPTURE_INTENT"; }

    @Override
    public void close() {
        open = false;
        device = null;
    }

    @Override
    public boolean isOpen() { return open; }

    /** Kept separate so the binding rule is trivially testable. */
    static final class Binding {
        private Binding() {}

        static RgbDecoder.Decoded check(CrossStreamBinder binder, String nonce, String returnedTxId, SbiCaptureResult res,
                                        FaceFrame host, RgbDecoder decoder) throws DeviceException {
            RgbDecoder.Decoded d = null;
            try {
                d = decoder.decode(res.image());
            } catch (RuntimeException e) {
                // fall through: binder reports EMBEDDING_FAILED (fail closed)
            }
            CrossStreamBinder.Result r = binder.verify(
                    nonce, returnedTxId, host, res.timestampMs(),
                    d == null ? null : d.rgb, d == null ? 0 : d.width, d == null ? 0 : d.height);
            if (r != CrossStreamBinder.Result.OK) throw new DeviceException("BINDING_FAILURE: " + r.name());
            return d;
        }
    }
}
