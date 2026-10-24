package liveness.android.glue;

import androidx.activity.ComponentActivity;

import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import liveness.android.AndroidDeviceAdapter;
import liveness.android.CameraXDeviceAdapter;
import liveness.android.CompositeFaceDetector;
import liveness.android.CrossStreamBinder;
import liveness.android.EnvironmentIntegrityChecker;
import liveness.android.FaceDetector;
import liveness.android.SFaceEmbedder;
import liveness.android.SbiIntentDeviceAdapter;
import liveness.android.SbiJwtVerifier;
import liveness.android.YuNetFaceDetector;
import liveness.challenge.ChallengeGenerator;
import liveness.crypto.AndroidKeyStoreSigner;
import liveness.device.DeviceAdapter;
import liveness.engine.MiniFasNetScorer;
import liveness.engine.SameFaceChecker;
import liveness.flow.RetryRateLimiter;
import liveness.policy.LivenessConfig;
import liveness.service.LivenessService;

/**
 * Reference bootstrap class for Android MOSIP Face Liveness + PAD integration.
 * <p>
 * Demonstrates reference wiring of:
 * <ul>
 *   <li>{@link AndroidKeyStoreSigner} (hardware-backed key generation and attestation signing)</li>
 *   <li>{@link SFaceEmbedder} (production-grade ONNX face embedding model for same-face checks)</li>
 *   <li>{@link SbiJwtVerifier} (cryptographic trust verification of SBI JWT responses)</li>
 *   <li>{@link LivenessFlutterPlugin#configure(LivenessService, Supplier)} (channel binding)</li>
 * </ul>
 * Integrators can adapt this template to initialize the liveness engine during
 * Android Activity or Application lifecycle startup.
 */
public final class LivenessAndroidBootstrap {

    private LivenessAndroidBootstrap() {}

    /**
     * Builds the YuNet + ML Kit composite detector to pass to {@link #init}.
     * YuNet supplies face count, bounding box and SFace alignment points; ML Kit supplies
     * eye/mouth contours and head pose. Benchmark candidate: on-device accuracy not yet validated.
     * The caller owns the result and should close it.
     *
     * @param yunetModelPath YuNet ONNX model on the device file system
     */
    public static CompositeFaceDetector createCompositeDetector(Path yunetModelPath) {
        YuNetFaceDetector yunet = new YuNetFaceDetector(yunetModelPath);
        try {
            return new CompositeFaceDetector(yunet, new MlKitFaceDetector());
        } catch (RuntimeException e) {
            try {
                yunet.close();
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    /**
     * Initializes and configures the Liveness service and Flutter plugin.
     *
     * @param activity the host ComponentActivity for SBI result launcher registration
     * @param detector face detector for CameraX frame observation
     * @param miniFasNetModelBytes raw model bytes for MiniFASNet PAD model
     * @param miniFasNetModelSha256 pinned SHA-256 (hex) of the PAD model; mandatory, a mismatch fails closed
     * @param sfaceModelBytes raw model bytes for SFace identity embedding model
     * @param sfaceModelSha256 pinned SHA-256 (hex) of the SFace model; mandatory, a mismatch fails closed
     * @param sbiTrustAnchorCerts trusted X.509 certificates for SBI JWT verification
     * @return the configured LivenessService
     * @throws Exception if keystore, model, or verifier initialization fails
     */
    public static LivenessService init(
            ComponentActivity activity,
            FaceDetector detector,
            byte[] miniFasNetModelBytes,
            String miniFasNetModelSha256,
            byte[] sfaceModelBytes,
            String sfaceModelSha256,
            List<X509Certificate> sbiTrustAnchorCerts) throws Exception {

        // 1. Hardware-backed KeyStore signing setup
        String keyAlias = AndroidKeyStoreSigner.DEFAULT_ALIAS;
        AndroidKeyStoreSigner.ensureKey(keyAlias);
        AndroidKeyStoreSigner signer = new AndroidKeyStoreSigner(keyAlias);

        // 2. Production Face Embedder (SFace 112x112)
        SFaceEmbedder sfaceEmbedder = new SFaceEmbedder(sfaceModelBytes, sfaceModelSha256, detector);

        // 3. Cryptographic trust verification of SBI JWT responses
        SbiJwtVerifier jwtVerifier = (sbiTrustAnchorCerts != null && !sbiTrustAnchorCerts.isEmpty())
                ? new SbiJwtVerifier(sbiTrustAnchorCerts, Instant::now)
                : SbiJwtVerifier.disabledForDevelopment();

        // 4. PAD Scorer & Same Face Checker
        MiniFasNetScorer padScorer = new MiniFasNetScorer(miniFasNetModelBytes, miniFasNetModelSha256);
        SameFaceChecker sameFaceChecker = new SameFaceChecker(SFaceEmbedder.CALIBRATED_THRESHOLD);

        // 5. Config, Challenge Generator, and Rate Limiter
        LivenessConfig config = LivenessConfig.defaultConfig();
        ChallengeGenerator challengeGen = new ChallengeGenerator(
                ChallengeGenerator.DEFAULT_POOL,
                Collections.emptyList()
        );
        RetryRateLimiter retryLimiter = new RetryRateLimiter(5, 60_000);

        // 6. Assemble LivenessService
        LivenessService service = new LivenessService(
                config, padScorer, sameFaceChecker, challengeGen, retryLimiter, signer);
        service.setFaceEmbedder(sfaceEmbedder);

        // 7. Device factory: AndroidDeviceAdapter combining CameraX and SBI
        AndroidSbiLauncher launcher = new AndroidSbiLauncher(activity);
        Supplier<DeviceAdapter> deviceFactory = () -> {
            CameraXDeviceAdapter camera = new CameraXDeviceAdapter("front-camera", detector);
            CrossStreamBinder binder = new CrossStreamBinder(
                    sameFaceChecker,
                    sfaceEmbedder,
                    10_000 // 10 second max cross-stream drift
            );
            SbiIntentDeviceAdapter sbi = new SbiIntentDeviceAdapter(
                    launcher,
                    binder,
                    camera::latestFrame,
                    new AndroidRgbDecoder(),
                    jwtVerifier,
                    "MOSIP_Registration_Client",
                    "Registration",
                    5000
            );
            EnvironmentIntegrityChecker integrityChecker = new EnvironmentIntegrityChecker(
                    new AndroidEnvProbe(activity.getApplicationContext())
            );
            return new AndroidDeviceAdapter(integrityChecker, camera, sbi);
        };

        // 8. Configure Flutter Plugin channel
        LivenessFlutterPlugin.configure(service, deviceFactory);

        return service;
    }
}
