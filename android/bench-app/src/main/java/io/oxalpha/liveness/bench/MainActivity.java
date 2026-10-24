package io.oxalpha.liveness.bench;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.Callable;

/**
 * Bench launcher. Each button runs one job on a background thread (never the UI thread), keeps the screen on, and
 * prints the saved path and JSON. Results are also in logcat (tag LivenessBench) and under
 * Android/data/io.oxalpha.liveness.bench/files/bench/.
 */
public final class MainActivity extends Activity {
    private TextView out;
    private LinearLayout buttons;
    private volatile boolean busy;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        root.addView(buttons);
        out = new TextView(this);
        out.setTextSize(11f);
        out.setTypeface(android.graphics.Typeface.MONOSPACE);
        out.setTextIsSelectable(true);
        out.setText("Idle. Keep the phone cool, unplugged, screen on. One job at a time.");
        ScrollView sv = new ScrollView(this);
        sv.addView(out);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        addJob("YuNet detector only: ORT CPU", () -> BenchRunner.runYuNetDetector(this));
        addJob("Composite: YuNet + ML Kit (face_frame.png)", () -> CompositeLatencyBenchmark.run(this));
        addJob("Pipeline + REAL composite, lockstep: INT8 (face_frame.png)", () -> CompositePipelineBenchmark.run(this, "MiniFASNetV2-SE-int8.onnx"));
        addBlurCandJob("Blur cand: sharp_textured", "cand_sharp_textured");
        addBlurCandJob("Blur cand: defocused", "cand_defocused");
        addBlurCandJob("Blur cand: bright_light", "cand_bright_light");
        addBlurCandJob("Blur cand: plain_wall", "cand_plain_wall");
        addGateJob("Relative gate live: flap OFF (30 warmup + 300)", "flapoff");
        addGateJob("Relative gate live: flap ON (30 warmup + 300)", "flapon");
        addJob("Pipeline + REAL composite, 720x720 rot270 stride 768: INT8 (face_frame_720_sensor.png)", () -> CameraShapedPipelineBenchmark.run(this, "MiniFASNetV2-SE-int8.onnx"));
        addJob("PAD model only: INT8", () -> BenchRunner.runPad(this, "MiniFASNetV2-SE-int8.onnx"));
        addJob("Per-frame pipeline, lockstep: INT8", () -> BenchRunner.runPipeline(this, "MiniFASNetV2-SE-int8.onnx", liveness.android.PipelineBenchmark.Mode.LOCKSTEP));
        addJob("Per-frame pipeline, paced 30 fps: INT8", () -> BenchRunner.runPipeline(this, "MiniFASNetV2-SE-int8.onnx", liveness.android.PipelineBenchmark.Mode.PACED));
        addJob("PAD model only: FP32", () -> BenchRunner.runPad(this, "MiniFASNetV2-SE.onnx"));
        addJob("Per-frame pipeline, lockstep: FP32", () -> BenchRunner.runPipeline(this, "MiniFASNetV2-SE.onnx", liveness.android.PipelineBenchmark.Mode.LOCKSTEP));
        addJob("Per-frame pipeline, paced 30 fps: FP32", () -> BenchRunner.runPipeline(this, "MiniFASNetV2-SE.onnx", liveness.android.PipelineBenchmark.Mode.PACED));
    }

    private void addBlurCandJob(String title, String condition) {
        addJob(title, () -> {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.CAMERA}, 1));
                return "Camera permission requested. Grant it, then tap the button again.";
            }
            return CameraBlurCandidatesBenchmark.run(this, condition);
        });
    }

    private void addGateJob(String title, String condition) {
        addJob(title, () -> {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.CAMERA}, 1));
                return "Camera permission requested. Grant it, then tap the button again.";
            }
            return CameraRelativeGateBenchmark.run(this, condition);
        });
    }

    private void addJob(String label, Callable<String> job) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setGravity(Gravity.CENTER);
        btn.setOnClickListener((View v) -> {
            if (busy) return;
            busy = true;
            out.setText("Running: " + label + " ... (do not touch the phone)");
            new Thread(() -> {
                String res;
                try {
                    res = job.call();
                } catch (Throwable t) {
                    Log.e(BenchRunner.TAG, "job failed", t);
                    res = "FAILED: " + t;
                }
                final String text = res;
                runOnUiThread(() -> { out.setText(text); busy = false; });
            }, "bench-job").start();
        });
        buttons.addView(btn);
    }
}
