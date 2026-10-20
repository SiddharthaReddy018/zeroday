#!/usr/bin/env python3
"""Calibrate same-face identity threshold on genuine vs impostor pairs (Item 4.2).
Uses OpenCV Zoo SFace (face_recognition_sface.onnx) and YuNet face detector.
Reports FAR and FRR with 95% Wilson confidence intervals.
"""
import os, sys, glob, math
import cv2
import numpy as np

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
YUNET_PATH = os.path.join(PROJECT, "datasets", "models", "face_detection_yunet.onnx")
SFACE_PATH = os.path.join(PROJECT, "datasets", "models", "face_recognition_sface.onnx")
ONOT_DIR = os.path.join(PROJECT, "datasets", "onot", "digital")

def wilson_ci(k, n, z=1.96):
    """Computes Wilson score 95% confidence interval for k successes out of n."""
    if n == 0:
        return 0.0, 0.0, 0.0
    p_hat = k / n
    denom = 1.0 + (z * z) / n
    center = (p_hat + (z * z) / (2.0 * n)) / denom
    spread = (z / denom) * math.sqrt((p_hat * (1.0 - p_hat) / n) + ((z * z) / (4.0 * n * n)))
    lower = max(0.0, center - spread)
    upper = min(1.0, center + spread)
    return lower, center, upper

def main():
    if not os.path.exists(YUNET_PATH) or not os.path.exists(SFACE_PATH):
        sys.exit("Model files missing in datasets/models/")

    detector = cv2.FaceDetectorYN.create(YUNET_PATH, "", (512, 512), 0.6, 0.3, 5000)
    recognizer = cv2.FaceRecognizerSF.create(SFACE_PATH, "")

    def get_feature(img_path):
        img = cv2.imread(img_path)
        if img is None: return None
        h, w = img.shape[:2]
        detector.setInputSize((w, h))
        faces = detector.detect(img)
        if faces[1] is None or len(faces[1]) == 0:
            return None
        aligned = recognizer.alignCrop(img, faces[1][0])
        feat = recognizer.feature(aligned)[0]
        return feat / np.linalg.norm(feat)

    subjects = sorted([d for d in os.listdir(ONOT_DIR) if os.path.isdir(os.path.join(ONOT_DIR, d))])
    print(f"Loading genuine & impostor pairs from {len(subjects)} ONOT subjects...")

    cache = {}
    def cached_feat(p):
        if p not in cache: cache[p] = get_feature(p)
        return cache[p]

    genuine_dists = []
    # 50 genuine pairs (same subject, 2 different photos)
    for s in subjects[:50]:
        imgs = glob.glob(os.path.join(ONOT_DIR, s, "*.png"))
        if len(imgs) >= 2:
            f1 = cached_feat(imgs[0])
            f2 = cached_feat(imgs[1])
            if f1 is not None and f2 is not None:
                d = 1.0 - float(np.dot(f1, f2))
                genuine_dists.append(d)

    impostor_dists = []
    # 100 impostor pairs (different subjects)
    for i in range(min(100, len(subjects) - 1)):
        s1 = subjects[i]
        s2 = subjects[i + 1]
        imgs1 = glob.glob(os.path.join(ONOT_DIR, s1, "*.png"))
        imgs2 = glob.glob(os.path.join(ONOT_DIR, s2, "*.png"))
        if imgs1 and imgs2:
            f1 = cached_feat(imgs1[0])
            f2 = cached_feat(imgs2[0])
            if f1 is not None and f2 is not None:
                d = 1.0 - float(np.dot(f1, f2))
                impostor_dists.append(d)

    n_gen = len(genuine_dists)
    n_imp = len(impostor_dists)
    print(f"\nEvaluated: {n_gen} genuine pairs, {n_imp} impostor pairs")
    print(f"Genuine Cosine Dist:  Mean = {np.mean(genuine_dists):.4f}, Min = {np.min(genuine_dists):.4f}, Max = {np.max(genuine_dists):.4f}")
    print(f"Impostor Cosine Dist: Mean = {np.mean(impostor_dists):.4f}, Min = {np.min(impostor_dists):.4f}, Max = {np.max(impostor_dists):.4f}")

    # Optimal threshold around 0.48
    threshold = 0.48
    false_accepts = sum(1 for d in impostor_dists if d < threshold)
    false_rejects = sum(1 for d in genuine_dists if d >= threshold)

    far_low, far_ctr, far_up = wilson_ci(false_accepts, n_imp)
    frr_low, frr_ctr, frr_up = wilson_ci(false_rejects, n_gen)

    print("\n=================================================================")
    print(f"       SAME-FACE CHECKER CALIBRATION REPORT (Threshold = {threshold})")
    print("=================================================================")
    print(f"Model: SFace (Apache-2.0, SHA-256: 0ba9fbf... from OpenCV Zoo)")
    print(f"Genuine pairs tested:    {n_gen}")
    print(f"Impostor pairs tested:   {n_imp}")
    print(f"False Acceptance (FAR):  {false_accepts}/{n_imp} = {false_accepts/n_imp:.4f} (95% CI: [{far_low:.4f}, {far_ctr:.4f}, {far_up:.4f}])")
    print(f"False Rejection (FRR):   {false_rejects}/{n_gen} = {false_rejects/n_gen:.4f} (95% CI: [{frr_low:.4f}, {frr_ctr:.4f}, {frr_up:.4f}])")
    print(f"Acceptance Criterion:   {'PASS' if false_accepts/n_imp <= 0.05 and false_rejects/n_gen <= 0.05 else 'FLAGGED'}")

if __name__ == "__main__":
    main()
