#!/usr/bin/env python3
"""Parity check: Java FaceAligner.alignCrop vs OpenCV warpAffine with a least-squares similarity fit.

Usage (from repo root, after `mvn -q compile`):
    python3 tools/check_align_parity.py target/classes

Needs: python3, numpy, opencv-python, a JDK. Does NOT need the SFace model.
Note: this checks the transform + bilinear resampling + zero border. It does not call
FaceRecognizerSF.alignCrop itself (that needs the model); the maths is the same operation.
"""
import os, subprocess, sys, tempfile
import numpy as np, cv2

classes = sys.argv[1] if len(sys.argv) > 1 else "target/classes"
W, H = 320, 240
T = np.array([[38.2946, 51.6963], [73.5318, 51.5014], [56.0252, 71.7366],
              [41.5493, 92.3655], [70.7299, 92.2041]], np.float32)
DUMP = """import liveness.android.FaceAligner; import java.nio.file.*;
public class Dump { public static void main(String[] a) throws Exception {
 byte[] img=Files.readAllBytes(Paths.get("img.rgb"));
 String[] t=new String(Files.readAllBytes(Paths.get("pts.txt"))).trim().split("\\\\s+");
 double[] p=new double[10]; for(int i=0;i<10;i++)p[i]=Double.parseDouble(t[i]);
 Files.write(Paths.get("java.rgb"), FaceAligner.alignCrop(img,%d,%d,p)); }}
""" % (W, H)

cases = [
    np.array([[120, 100], [190, 95], [158, 140], [128, 175], [185, 172]], np.float32),   # upright
    np.array([[100, 120], [160, 90], [140, 140], [125, 185], [175, 160]], np.float32),   # rotated
    np.array([[40, 40], [110, 38], [75, 80], [48, 115], [105, 112]], np.float32),        # near edge
]
worst = 0
classes = os.path.abspath(classes)
with tempfile.TemporaryDirectory() as d:
    open(os.path.join(d, "Dump.java"), "w").write(DUMP)
    subprocess.check_call(["javac", "-d", d, "-cp", classes, os.path.join(d, "Dump.java")])
    rng = np.random.default_rng(1)
    img = cv2.GaussianBlur(rng.integers(0, 256, (H, W, 3), dtype=np.uint8), (0, 0), 3)
    img.tofile(os.path.join(d, "img.rgb"))
    for i, pts in enumerate(cases):
        open(os.path.join(d, "pts.txt"), "w").write(" ".join(str(v) for v in pts.flatten()))
        subprocess.check_call(["java", "-cp", d + os.pathsep + classes, "Dump"], cwd=d)
        M = cv2.estimateAffinePartial2D(pts, T, method=cv2.LMEDS)[0]
        ref = cv2.warpAffine(img, M, (112, 112), flags=cv2.INTER_LINEAR,
                             borderMode=cv2.BORDER_CONSTANT, borderValue=0)
        j = np.fromfile(os.path.join(d, "java.rgb"), np.uint8).reshape(112, 112, 3)
        diff = np.abs(ref.astype(int) - j.astype(int))
        print("case %d: max diff %d, mean %.3f" % (i, diff.max(), diff.mean()))
        worst = max(worst, diff.max())
print("PASS" if worst <= 2 else "FAIL (max diff %d)" % worst)
sys.exit(0 if worst <= 2 else 1)
