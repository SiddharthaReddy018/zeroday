#!/usr/bin/env python3
"""Preprocessing parity check (Item 3.2).
Compares PadPreprocessor implementation with OpenCV reference on 25+ real face crops.
Measures max abs difference and score shift under real MiniFASNet inference.
"""
import os, sys, glob, csv
import cv2
import numpy as np
import onnxruntime as ort

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_PATH = os.path.join(PROJECT, "datasets", "models", "MiniFASNetV2-SE.onnx")
MANIFEST = os.path.join(PROJECT, "datasets", "manifests", "evaluation_benchmark_manifest.csv")
BBOX_CSV = MANIFEST + ".bbox.csv"

def pad_preprocessor_python(rgb, w, h, bbox_x, bbox_y, bbox_w, bbox_h):
    """Exact python port of PadPreprocessor.java area/bilinear resampling with reflect-101."""
    CROP_FACTOR = 1.5
    SIZE = 128
    if bbox_w > 0 and bbox_h > 0:
        cx = bbox_x + bbox_w / 2.0
        cy = bbox_y + bbox_h / 2.0
        side = CROP_FACTOR * max(bbox_w, bbox_h)
    else:
        cx = w / 2.0
        cy = h / 2.0
        side = min(w, h)
    
    x0 = cx - side / 2.0
    y0 = cy - side / 2.0
    scale = side / SIZE

    def reflect_101(i, n):
        if n == 1: return 0
        period = 2 * (n - 1)
        m = ((i % period) + period) % period
        return m if m < n else period - m

    out = np.zeros((3, SIZE, SIZE), dtype=np.float32)

    if scale > 1.0: # area resample
        for oy in range(SIZE):
            sy0 = y0 + oy * scale
            sy1 = sy0 + scale
            for ox in range(SIZE):
                sx0 = x0 + ox * scale
                sx1 = sx0 + scale
                r, g, b, wsum = 0.0, 0.0, 0.0, 0.0
                iy_start = int(np.floor(sy0))
                iy_end = int(np.ceil(sy1))
                ix_start = int(np.floor(sx0))
                ix_end = int(np.ceil(sx1))
                for iy in range(iy_start, iy_end):
                    wy = min(sy1, iy + 1) - max(sy0, iy)
                    if wy <= 0: continue
                    ry = reflect_101(iy, h)
                    for ix in range(ix_start, ix_end):
                        wx = min(sx1, ix + 1) - max(sx0, ix)
                        if wx <= 0: continue
                        rx = reflect_101(ix, w)
                        wt = wx * wy
                        pixel = rgb[ry, rx]
                        r += wt * pixel[0]
                        g += wt * pixel[1]
                        b += wt * pixel[2]
                        wsum += wt
                out[0, oy, ox] = (r / wsum) / 255.0
                out[1, oy, ox] = (g / wsum) / 255.0
                out[2, oy, ox] = (b / wsum) / 255.0
    else: # bilinear
        for oy in range(SIZE):
            sy = y0 + (oy + 0.5) * scale - 0.5
            iy = int(np.floor(sy))
            fy = sy - iy
            for ox in range(SIZE):
                sx = x0 + (ox + 0.5) * scale - 0.5
                ix = int(np.floor(sx))
                fx = sx - ix
                for c in range(3):
                    p00 = rgb[reflect_101(iy, h), reflect_101(ix, w), c]
                    p10 = rgb[reflect_101(iy, h), reflect_101(ix + 1, w), c]
                    p01 = rgb[reflect_101(iy + 1, h), reflect_101(ix, w), c]
                    p11 = rgb[reflect_101(iy + 1, h), reflect_101(ix + 1, w), c]
                    val = (p00 * (1 - fx) + p10 * fx) * (1 - fy) + (p01 * (1 - fx) + p11 * fx) * fy
                    out[c, oy, ox] = val / 255.0
    return out

def opencv_reference_prepare(rgb_img, bbox_x, bbox_y, bbox_w, bbox_h):
    """OpenCV reference implementation using INTER_AREA / LANCZOS4 and BORDER_REFLECT_101."""
    h, w = rgb_img.shape[:2]
    CROP_FACTOR = 1.5
    SIZE = 128
    if bbox_w > 0 and bbox_h > 0:
        cx = bbox_x + bbox_w / 2.0
        cy = bbox_y + bbox_h / 2.0
        side = int(round(CROP_FACTOR * max(bbox_w, bbox_h)))
    else:
        cx = w / 2.0
        cy = h / 2.0
        side = min(w, h)
    
    x1 = int(round(cx - side / 2.0))
    y1 = int(round(cy - side / 2.0))
    x2 = x1 + side
    y2 = y1 + side

    # Determine padding needed if crop falls outside image
    pad_top = max(0, -y1)
    pad_bottom = max(0, y2 - h)
    pad_left = max(0, -x1)
    pad_right = max(0, x2 - w)

    if pad_top > 0 or pad_bottom > 0 or pad_left > 0 or pad_right > 0:
        padded = cv2.copyMakeBorder(rgb_img, pad_top, pad_bottom, pad_left, pad_right, cv2.BORDER_REFLECT_101)
        crop = padded[y1 + pad_top : y2 + pad_top, x1 + pad_left : x2 + pad_left]
    else:
        crop = rgb_img[y1:y2, x1:x2]

    interp = cv2.INTER_AREA if side > SIZE else cv2.INTER_LANCZOS4
    resized = cv2.resize(crop, (SIZE, SIZE), interpolation=interp)
    chw = np.transpose(resized, (2, 0, 1)).astype(np.float32) / 255.0
    return chw

def main():
    if not os.path.exists(BBOX_CSV):
        sys.exit(f"Sidecar not found: {BBOX_CSV}")
    
    session = ort.InferenceSession(MODEL_PATH)

    boxes = {}
    with open(BBOX_CSV, newline="") as f:
        reader = csv.reader(f)
        header = next(reader)
        for row in reader:
            if len(row) >= 5:
                boxes[row[0]] = [int(v) for v in row[1:5]]

    test_samples = list(boxes.keys())[:30]
    print(f"Comparing preprocessing on {len(test_samples)} face crops...")

    max_abs_diffs = []
    mean_abs_diffs = []
    score_shifts = []

    for path in test_samples:
        full_path = os.path.join(PROJECT, "datasets", path)
        if not os.path.exists(full_path):
            continue
        bgr = cv2.imread(full_path)
        if bgr is None: continue
        rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
        h, w = rgb.shape[:2]
        bx, by, bw, bh = boxes[path]

        t_custom = pad_preprocessor_python(rgb, w, h, bx, by, bw, bh)
        t_opencv = opencv_reference_prepare(rgb, bx, by, bw, bh)

        abs_diff = np.abs(t_custom - t_opencv)
        max_diff = np.max(abs_diff)
        mean_diff = np.mean(abs_diff)
        max_abs_diffs.append(max_diff)
        mean_abs_diffs.append(mean_diff)

        # Score under MiniFASNet
        def score(t):
            inp = t[np.newaxis, ...]
            out = session.run(None, {"input": inp})[0]
            live_logit = out[0, 0]
            spoof_logit = out[0, 1]
            m = max(live_logit, spoof_logit)
            el = np.exp(live_logit - m)
            es = np.exp(spoof_logit - m)
            return round((el / (el + es)) * 100)

        s_custom = score(t_custom)
        s_opencv = score(t_opencv)
        score_shifts.append(abs(s_custom - s_opencv))

    print("\n--- Preprocessing Parity Results (N = %d) ---" % len(max_abs_diffs))
    print("Max Absolute Pixel Difference: %.6f (out of 1.0)" % max(max_abs_diffs))
    print("Mean Absolute Pixel Difference: %.6f" % np.mean(mean_abs_diffs))
    print("Max Score Shift (0-100 scale): %d points" % max(score_shifts))
    print("Mean Score Shift: %.2f points" % np.mean(score_shifts))
    print("Percent of samples with 0 score shift: %.1f%%" % (100.0 * sum(1 for s in score_shifts if s == 0) / len(score_shifts)))

if __name__ == "__main__":
    main()
