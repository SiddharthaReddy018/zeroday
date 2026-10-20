#!/usr/bin/env python3
"""Writes <manifest>.bbox.csv (path,x,y,w,h) for evaluation (ADR-010 / R9).

Usage: python3 tools/detect_face_boxes.py datasets/manifests/evaluation_benchmark_manifest.csv [--root DIR]

Uses OpenCV's bundled Haar frontal-face cascade so there is no model download. It is a stand-in for the
production face detector: if your pipeline uses another detector (ML Kit, BlazeFace, YuNet), swap detect() so the
evaluation crop matches production. Images with no detection are left out of the sidecar; the evaluation report
then counts them as whole-image fallbacks, so the gap is visible instead of silent. Largest face wins.
Requires: pip install opencv-python-headless
"""
import csv, os, sys
import cv2

def detect(gray, cascade):
    faces = cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(40, 40))
    if len(faces) == 0:
        return None
    return max(faces, key=lambda r: r[2] * r[3])

def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    manifest = sys.argv[1]
    root = os.path.dirname(os.path.abspath(manifest))
    if "--root" in sys.argv:
        root = sys.argv[sys.argv.index("--root") + 1]
    cascade = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")
    out, found, missing = manifest + ".bbox.csv", 0, 0
    with open(manifest, newline="") as f, open(out, "w", newline="") as o:
        w = csv.writer(o, lineterminator="\n")
        w.writerow(["path", "x", "y", "w", "h"])
        for row in csv.DictReader(f):
            p = row["path"]
            full = p if os.path.exists(p) else os.path.join(root, p)
            img = cv2.imread(full)
            box = None if img is None else detect(cv2.cvtColor(img, cv2.COLOR_BGR2GRAY), cascade)
            if box is None:
                missing += 1
                continue
            w.writerow([p] + [int(v) for v in box])
            found += 1
    print("faces found: %d, no detection: %d -> %s" % (found, missing, out))

if __name__ == "__main__":
    main()
