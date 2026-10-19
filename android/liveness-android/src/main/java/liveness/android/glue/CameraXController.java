package liveness.android.glue;

import android.content.Context;
import android.util.Size;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import liveness.android.CameraXDeviceAdapter;

/** Binds front-camera Preview + ImageAnalysis (feeding CameraXDeviceAdapter). Call from the main thread. */
public final class CameraXController {

    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private ProcessCameraProvider provider;

    public void bind(Context ctx, LifecycleOwner owner, PreviewView view, CameraXDeviceAdapter adapter) {
        final com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> f =
                ProcessCameraProvider.getInstance(ctx);
        f.addListener(() -> {
            try {
                provider = f.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(view.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build();
                analysis.setAnalyzer(analysisExecutor, new CameraXAnalyzer(adapter));
                provider.unbindAll();
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis);
            } catch (ExecutionException | InterruptedException e) {
                // Surfaces to the engine as a frame timeout -> DEVICE_ERROR.
            }
        }, ContextCompat.getMainExecutor(ctx));
    }

    public void unbind() {
        if (provider != null) provider.unbindAll();
        analysisExecutor.shutdown();
    }
}
