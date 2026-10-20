#!/usr/bin/env python3
"""Video-level session evaluation on real Kaggle Anti-Spoofing Replay dataset.
Evaluates full video presentations through the quality gate, face detection,
and MiniFASNet PAD scoring to compute ISO/IEC 30107-3 session-level metrics:
- APAR (Attack Presentation Acquisition Rate)
- APNRR (Attack Presentation Non-Response Rate)
- BPNRR (Bona Fide Presentation Non-Response Rate)
- Video-Level APCER & BPCER
"""
import os, sys, glob
import cv2
import numpy as np
import onnxruntime as ort

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_PATH = os.path.join(PROJECT, "datasets", "models", "MiniFASNetV2-SE.onnx")

VIDEO_DIRS = {
    "print": ("datasets/replay-attack/printouts", "attack"),
    "screen": ("datasets/replay-attack/replay", "attack"),
    "cutout": ("datasets/replay-attack/cut-out printouts", "attack"),
    "live_video": ("datasets/replay-attack/live_video", "bona_fide")
}

def pad_crop_tensor(rgb, bbox):
    """1.5x square crop, reflect-101 padding, resize 128x128, CHW [0, 1]."""
    h, w = rgb.shape[:2]
    bx, by, bw, bh = bbox
    cx = bx + bw / 2.0
    cy = by + bh / 2.0
    side = int(round(1.5 * max(bw, bh)))
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

    resized = cv2.resize(crop, (128, 128), interpolation=cv2.INTER_AREA)
    return np.transpose(resized, (2, 0, 1)).astype(np.float32) / 255.0

def evaluate_video(video_path, cascade, session, min_qual_frames=3, sample_stride=5):
    cap = cv2.VideoCapture(video_path)
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0

    consecutive_qual = 0
    acquired = False
    pad_scores = []
    frame_idx = 0

    while True:
        ret, bgr = cap.read()
        if not ret:
            break
        frame_idx += 1
        if frame_idx % sample_stride != 0:
            continue

        gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
        h, w = gray.shape

        # Quality check 1: blur (Laplacian variance >= 100)
        blur_var = cv2.Laplacian(gray, cv2.CV_64F).var()

        # Quality check 2: brightness (mean pixel value in [40, 250])
        brightness = float(np.mean(gray))

        # Quality check 3: face detection & size
        faces = cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(64, 64))
        
        quality_ok = (len(faces) == 1 and blur_var >= 100.0 and 40.0 <= brightness <= 250.0)

        if quality_ok:
            consecutive_qual += 1
            if consecutive_qual >= min_qual_frames:
                acquired = True
            
            # Run PAD inference
            rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
            tensor = pad_crop_tensor(rgb, faces[0])
            inp = tensor[np.newaxis, ...]
            out = session.run(None, {"input": inp})[0]
            # Class 0 = live, Class 1 = spoof
            l_live = out[0, 0]
            l_spoof = out[0, 1]
            m = max(l_live, l_spoof)
            el = np.exp(l_live - m)
            es = np.exp(l_spoof - m)
            score = round((el / (el + es)) * 100)
            pad_scores.append(score)
        else:
            consecutive_qual = 0

    cap.release()

    mean_score = float(np.mean(pad_scores)) if pad_scores else 0.0
    return {
        "frames_total": total_frames,
        "acquired": acquired,
        "quality_frames_count": len(pad_scores),
        "mean_pad_score": mean_score
    }

def main():
    cascade = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")
    session = ort.InferenceSession(MODEL_PATH)

    print("=====================================================================")
    print("      VIDEO-LEVEL SESSION EVALUATION (ISO/IEC 30107-3 METRICS)")
    print("=====================================================================")

    results = {}

    for species, (vdir, label) in VIDEO_DIRS.items():
        full_dir = os.path.join(PROJECT, vdir)
        vids = sorted(glob.glob(os.path.join(full_dir, "*.*")))
        results[species] = []

        print(f"\nProcessing {species} ({len(vids)} videos)...")
        for v in vids:
            res = evaluate_video(v, cascade, session)
            res["path"] = os.path.basename(v)
            res["species"] = species
            res["label"] = label
            results[species].append(res)
            
            status = "ACQUIRED" if res["acquired"] else "UNACQUIRED"
            print(f"  {res['path'][:40]:<40} -> {status} (qual frames: {res['quality_frames_count']}, PAD: {res['mean_pad_score']:.1f})")

    # Compute ISO/IEC 30107-3 session-level metrics
    print("\n" + "=" * 65)
    print("                 SESSION-LEVEL METRIC SUMMARY")
    print("=" * 65)

    print("\n--- Attack Acquisition Rates (APAR) & Non-Response (APNRR) ---")
    print(f"{'PAI Species':<15} {'Total':<8} {'Acquired':<10} {'APAR':<10} {'APNRR':<10} {'Video APCER':<12}")
    print("-" * 65)

    for sp in ["print", "screen", "cutout"]:
        vlist = results[sp]
        n_total = len(vlist)
        n_acq = sum(1 for r in vlist if r["acquired"])
        apar = n_acq / n_total if n_total > 0 else 0.0
        # APNRR: presentations that failed to acquire or timed out
        apnrr = (n_total - n_acq) / n_total if n_total > 0 else 0.0
        # Video APCER: of acquired attacks, how many got mean score >= 70
        n_spoof_pass = sum(1 for r in vlist if r["acquired"] and r["mean_pad_score"] >= 70.0)
        apcer = n_spoof_pass / n_acq if n_acq > 0 else 0.0
        print(f"{sp:<15} {n_total:<8} {n_acq:<10} {apar:<10.4f} {apnrr:<10.4f} {apcer:<12.4f}")

    print("\n--- Bona Fide Presentation Non-Response (BPNRR) & Video BPCER ---")
    live_list = results["live_video"]
    n_live = len(live_list)
    n_live_acq = sum(1 for r in live_list if r["acquired"])
    bpnrr = (n_live - n_live_acq) / n_live if n_live > 0 else 0.0
    n_live_rejected = sum(1 for r in live_list if r["acquired"] and r["mean_pad_score"] < 70.0)
    bpcer = n_live_rejected / n_live_acq if n_live_acq > 0 else 0.0
    print(f"Bona Fide Videos: {n_live}")
    print(f"Acquired (BPAR):  {n_live_acq / n_live:.4f} ({n_live_acq}/{n_live})")
    print(f"BPNRR:            {bpnrr:.4f} (non-response rate)")
    print(f"Video BPCER:      {bpcer:.4f} ({n_live_rejected}/{n_live_acq} acquired live rejected)")

if __name__ == "__main__":
    main()
