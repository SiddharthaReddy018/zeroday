#!/usr/bin/env python3
"""Calibrate PAD threshold on the DEV split only (Item 3.6 / HANDOFF-2 R1 / §12.3).
Calibrates on dev, freezes the threshold, and outputs the calibration record for docs/decisions.md.
"""
import os, sys, csv
import numpy as np
import onnxruntime as ort
import cv2

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_PATH = os.path.join(PROJECT, "datasets", "models", "MiniFASNetV2-SE.onnx")
MANIFEST = os.path.join(PROJECT, "datasets", "manifests", "evaluation_benchmark_manifest.csv")
BBOX_CSV = MANIFEST + ".bbox.csv"

def pad_prepare(rgb, w, h, bx, by, bw, bh):
    CROP_FACTOR = 1.5
    SIZE = 128
    if bw > 0 and bh > 0:
        cx = bx + bw / 2.0
        cy = by + bh / 2.0
        side = int(round(CROP_FACTOR * max(bw, bh)))
    else:
        cx = w / 2.0
        cy = h / 2.0
        side = min(w, h)
    
    x1 = int(round(cx - side / 2.0))
    y1 = int(round(cy - side / 2.0))
    x2 = x1 + side
    y2 = y1 + side

    pad_top = max(0, -y1)
    pad_bottom = max(0, y2 - h)
    pad_left = max(0, -x1)
    pad_right = max(0, x2 - w)

    if pad_top > 0 or pad_bottom > 0 or pad_left > 0 or pad_right > 0:
        padded = cv2.copyMakeBorder(rgb, pad_top, pad_bottom, pad_left, pad_right, cv2.BORDER_REFLECT_101)
        crop = padded[y1 + pad_top : y2 + pad_top, x1 + pad_left : x2 + pad_left]
    else:
        crop = rgb[y1:y2, x1:x2]

    interp = cv2.INTER_AREA if side > SIZE else cv2.INTER_LANCZOS4
    resized = cv2.resize(crop, (SIZE, SIZE), interpolation=interp)
    chw = np.transpose(resized, (2, 0, 1)).astype(np.float32) / 255.0
    return chw

def main():
    session = ort.InferenceSession(MODEL_PATH)
    boxes = {}
    if os.path.exists(BBOX_CSV):
        with open(BBOX_CSV, newline="") as f:
            reader = csv.reader(f)
            next(reader)
            for r in reader:
                if len(r) >= 5:
                    boxes[r[0]] = [int(v) for v in r[1:5]]

    dev_entries = []
    with open(MANIFEST, newline="") as f:
        reader = csv.DictReader(f)
        for r in reader:
            if r["split"] == "dev":
                dev_entries.append(r)

    print(f"Scoring DEV split (N = {len(dev_entries)})...")
    scores = []
    labels = [] # 1 = bona fide, 0 = attack

    for entry in dev_entries:
        rel = entry["path"]
        full = os.path.join(PROJECT, "datasets", rel)
        if not os.path.exists(full):
            continue
        bgr = cv2.imread(full)
        if bgr is None: continue
        rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
        h, w = rgb.shape[:2]
        bx, by, bw, bh = boxes.get(rel, [0, 0, 0, 0])
        t = pad_prepare(rgb, w, h, bx, by, bw, bh)
        
        inp = t[np.newaxis, ...]
        out = session.run(None, {"input": inp})[0]
        live_logit = out[0, 0]
        spoof_logit = out[0, 1]
        m = max(live_logit, spoof_logit)
        el = np.exp(live_logit - m)
        es = np.exp(spoof_logit - m)
        score = round((el / (el + es)) * 100)

        scores.append(score)
        labels.append(1 if entry["label"] == "bona_fide" else 0)

    scores = np.array(scores)
    labels = np.array(labels)

    n_bf = np.sum(labels == 1)
    n_atk = np.sum(labels == 0)
    print(f"DEV split scored: {n_bf} bona fide, {n_atk} attacks")

    # Evaluate candidate thresholds from 10 to 95
    best_th = 70
    min_gap = float("inf")
    results = []
    for th in range(1, 100):
        # Classified as bona fide if score >= th
        # APCER: attack classified as bona fide (score >= th)
        apcer = np.sum((labels == 0) & (scores >= th)) / max(1, n_atk)
        # BPCER: bona fide classified as attack (score < th)
        bpcer = np.sum((labels == 1) & (scores < th)) / max(1, n_bf)
        gap = abs(apcer - bpcer)
        results.append((th, apcer, bpcer, gap))
        if gap < min_gap:
            min_gap = gap
            best_th = th

    print("\n--- DEV Split Calibration Results ---")
    eer_row = [r for r in results if r[0] == best_th][0]
    print(f"D-EER Operating Point: threshold = {eer_row[0]}, APCER = {eer_row[1]:.4f}, BPCER = {eer_row[2]:.4f}")

    # Standard operating point for high security: BPCER <= 1% with lowest APCER
    sec_cands = [r for r in results if r[2] <= 0.01]
    best_sec = min(sec_cands, key=lambda x: x[1]) if sec_cands else eer_row
    print(f"High-Security Operating Point (BPCER <= 1%): threshold = {best_sec[0]}, APCER = {best_sec[1]:.4f}, BPCER = {best_sec[2]:.4f}")

    # Check threshold 70 specifically
    th70_row = [r for r in results if r[0] == 70][0]
    print(f"Threshold 70 on DEV: APCER = {th70_row[1]:.4f}, BPCER = {th70_row[2]:.4f}")

if __name__ == "__main__":
    main()
