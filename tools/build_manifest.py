#!/usr/bin/env python3
"""
build_manifest.py - scan team recordings, extract frames, write a manifest that the
repo's ManifestLoader (liveness.evaluation) accepts.

Schema (from ManifestLoader.java / HANDOFF-2 section 15.5):
  required: path,label,species,subject_id,split
  optional: lighting,skin_tone,glasses,source,gender,age_group,capture_device,content_origin
  extra trailing columns are ignored by the loader; we add provenance columns.
  label   in {bona_fide, attack}      (bona fide species is empty)
  species in {print, screen, video, cutout, curved_print, mask_2d, mask_3d, deepfake}
  split   in {train, dev, test}
  The loader splits on bare commas: NO commas inside any value (we sanitize).

Folder convention:
  ROOT/<subject_id>/<attack|bona_fide>/<species|genuine>/<file>
File tokens (double underscore separated, key-value):
  clip01__lit-office__dev-pixel7__sub-matte__pai-iphone12__gl-no__var-a4hand__sess-1.mp4

Requires: pip install opencv-python numpy      Python 3.8+, ASCII only.
"""
import argparse
import csv
import hashlib
import sys
from collections import Counter, defaultdict
from pathlib import Path

try:
    import cv2
    import numpy as np
except ImportError:
    sys.exit("Missing deps: pip install opencv-python numpy")

IMG_EXT = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
VID_EXT = {".mp4", ".mov", ".avi", ".mkv", ".webm", ".m4v"}

SPECIES = {"print", "screen", "video", "cutout", "curved_print",
           "mask_2d", "mask_3d", "deepfake"}
LABELS = {"attack", "bona_fide"}
SPLITS = {"train", "dev", "test"}
LIGHTING = {"office", "window", "dim", "backlit", "warm"}
RULE_OF_30 = 30
FRAMES_DIRNAME = "_frames"

COLUMNS = [
    # loader columns, in the documented order
    "path", "label", "species", "subject_id", "split",
    "lighting", "skin_tone", "glasses", "source",
    "gender", "age_group", "capture_device", "content_origin",
    # extra provenance columns (ignored by the loader)
    "presentation_id", "pai_subtype", "pai_device", "variant", "session_id",
    "frame_index", "timestamp_ms", "width", "height", "sharpness", "mean_luma", "sha256",
]


def clean(v):
    """Loader splits on commas and trims; keep values comma/newline free."""
    return str(v).replace(",", ";").replace("\n", " ").replace("\r", " ").strip()


def parse_tokens(stem):
    toks = {}
    for part in stem.split("__")[1:]:
        if "-" in part:
            k, v = part.split("-", 1)
            toks[k.lower()] = v
    return toks


def mst_value(scale, mode):
    try:
        s = int(scale)
    except (TypeError, ValueError):
        return ""
    if not 1 <= s <= 10:
        return ""
    if mode == "raw":
        return str(s)
    if s <= 3:
        return "MST_1-3"
    if s <= 6:
        return "MST_4-6"
    return "MST_7-10"


def metrics(bgr):
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    h, w = gray.shape
    sharp = float(cv2.Laplacian(gray, cv2.CV_64F).var())
    luma = float(gray[h // 4:3 * h // 4, w // 4:3 * w // 4].mean())
    return sharp, luma


def sha256_bytes(b):
    return hashlib.sha256(b).hexdigest()


def load_subjects(path):
    subs = {}
    with open(path, newline="", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            sid = (r.get("subject_id") or "").strip()
            if not sid:
                continue
            subs[sid] = {k: (v or "").strip() for k, v in r.items()}
    return subs


def iter_video(path, fps_target, max_frames):
    cap = cv2.VideoCapture(str(path))
    if not cap.isOpened():
        return
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    step = max(1, int(round(fps / fps_target)))
    idx, kept = 0, 0
    while kept < max_frames:
        ok, frame = cap.read()
        if not ok:
            break
        if idx % step == 0:
            yield idx, int(round(idx / fps * 1000)), frame
            kept += 1
        idx += 1
    cap.release()


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--root", required=True, help="recordings root")
    ap.add_argument("--subjects", required=True,
                    help="subjects.csv: subject_id,mst_scale,consent_signed[,split,gender,age_group]")
    ap.add_argument("--out", default="manifest_out", help="directory for the manifest CSV")
    ap.add_argument("--manifest-name", default="evaluation_benchmark_manifest.csv")
    ap.add_argument("--default-split", default="test", choices=sorted(SPLITS),
                    help="used when subjects.csv has no split (default: test)")
    ap.add_argument("--source", default="team_recorded")
    ap.add_argument("--mst-mode", default="band", choices=["band", "raw"],
                    help="band = MST_1-3 / MST_4-6 / MST_7-10; raw = 1..10")
    ap.add_argument("--fps", type=float, default=2.0)
    ap.add_argument("--max-frames", type=int, default=20)
    ap.add_argument("--min-sharpness", type=float, default=0.0)
    args = ap.parse_args()

    root = Path(args.root).resolve()
    out = Path(args.out).resolve()
    out.mkdir(parents=True, exist_ok=True)
    frames_dir = root / FRAMES_DIRNAME
    frames_dir.mkdir(exist_ok=True)
    subjects = load_subjects(args.subjects)

    warnings, rows = [], []
    pres = defaultdict(set)            # group -> presentation ids
    frames = Counter()
    subj_by_group = defaultdict(set)   # group -> subject ids
    bona_subj_by_mst = defaultdict(set)
    skipped_subjects = set()

    files = [p for p in sorted(root.rglob("*"))
             if p.is_file() and p.suffix.lower() in (IMG_EXT | VID_EXT)
             and FRAMES_DIRNAME not in p.relative_to(root).parts]
    if not files:
        sys.exit("No media found under %s" % root)

    for p in files:
        rel = p.relative_to(root).parts
        if len(rel) < 4:
            warnings.append("SKIP (need SUBJECT/LABEL/GROUP/file): " + "/".join(rel))
            continue
        subject, label, group = rel[0], rel[1].lower(), rel[2]
        if label not in LABELS:
            warnings.append("SKIP (label folder must be attack or bona_fide): " + "/".join(rel))
            continue
        sub = subjects.get(subject)
        if sub is None:
            skipped_subjects.add(subject)
            continue
        if sub.get("consent_signed", "").lower() != "yes":
            warnings.append("SKIP (no consent): " + subject)
            continue
        if label == "attack":
            if group not in SPECIES:
                warnings.append("SKIP (species '%s' not in %s): %s" % (group, sorted(SPECIES), "/".join(rel)))
                continue
            species = group
        else:
            species = ""
        split = sub.get("split") or args.default_split
        if split not in SPLITS:
            warnings.append("SKIP (bad split '%s' for %s)" % (split, subject))
            continue

        toks = parse_tokens(p.stem)
        lighting = toks.get("lit", "")
        if lighting and lighting not in LIGHTING:
            warnings.append("lighting '%s' not in %s: %s" % (lighting, sorted(LIGHTING), "/".join(rel)))
        if not lighting:
            warnings.append("NO lit- token (lighting left blank): " + "/".join(rel))
        glasses = toks.get("gl", "")
        skin = mst_value(sub.get("mst_scale"), args.mst_mode)
        if not skin:
            warnings.append("NO valid mst_scale for subject " + subject)

        pid = hashlib.sha1("/".join(rel).encode()).hexdigest()[:10]
        gkey = "%s:%s" % (label, species or "genuine")
        pres[gkey].add(pid)
        subj_by_group[gkey].add(subject)
        if label == "bona_fide":
            bona_subj_by_mst[skin or "UNLABELED"].add(subject)

        items = []
        if p.suffix.lower() in IMG_EXT:
            bgr = cv2.imread(str(p))
            if bgr is None:
                warnings.append("UNREADABLE image (convert HEIC to JPG): " + "/".join(rel))
                continue
            items.append((0, 0, bgr, p, None))
        else:
            for idx, ts, bgr in iter_video(p, args.fps, args.max_frames):
                items.append((idx, ts, bgr, None, idx))
            if not items:
                warnings.append("UNREADABLE video: " + "/".join(rel))
                continue

        for idx, ts, bgr, img_path, vid_idx in items:
            sharp, luma = metrics(bgr)
            if args.min_sharpness and sharp < args.min_sharpness:
                continue
            if img_path is not None:
                path_out = "/".join(rel)
                digest = hashlib.sha256(img_path.read_bytes()).hexdigest()
            else:
                fname = "%s_%s_%s_f%04d.jpg" % (subject, "A" if label == "attack" else "B", pid, idx)
                ok, buf = cv2.imencode(".jpg", bgr, [cv2.IMWRITE_JPEG_QUALITY, 95])
                if not ok:
                    continue
                (frames_dir / fname).write_bytes(buf.tobytes())
                path_out = FRAMES_DIRNAME + "/" + fname
                digest = sha256_bytes(buf.tobytes())
            h, w = bgr.shape[:2]
            rec = {
                "path": path_out, "label": label, "species": species,
                "subject_id": subject, "split": split,
                "lighting": lighting, "skin_tone": skin, "glasses": glasses,
                "source": args.source,
                "gender": sub.get("gender", ""), "age_group": sub.get("age_group", ""),
                "capture_device": toks.get("dev", ""),
                "content_origin": "real",
                "presentation_id": pid, "pai_subtype": toks.get("sub", ""),
                "pai_device": toks.get("pai", ""), "variant": toks.get("var", ""),
                "session_id": toks.get("sess", ""),
                "frame_index": idx, "timestamp_ms": ts, "width": w, "height": h,
                "sharpness": "%.1f" % sharp, "mean_luma": "%.1f" % luma, "sha256": digest,
            }
            rows.append(rec)
            frames[gkey] += 1

    # ---- write + loader self-check ----
    out_csv = out / args.manifest_name
    with open(out_csv, "w", newline="", encoding="utf-8") as f:
        f.write(",".join(COLUMNS) + "\n")
        for r in rows:
            f.write(",".join(clean(r[c]) for c in COLUMNS) + "\n")

    problems = 0
    with open(out_csv, encoding="utf-8") as f:
        header = f.readline().strip().split(",")
        for n, line in enumerate(f, start=2):
            parts = [x.strip() for x in line.rstrip("\n").split(",", -1)]
            d = dict(zip(header, parts))
            if len(parts) != len(header) or not d["path"] or not d["subject_id"] \
                    or d["label"] not in LABELS or d["split"] not in SPLITS:
                problems += 1
                print("LOADER-CHECK line %d invalid" % n)
    print("\n=== %s: %d rows, loader self-check %s ===" %
          (out_csv, len(rows), "OK" if problems == 0 else "%d PROBLEMS" % problems))

    print("\n%-26s %13s %9s %9s  %s" % ("group", "presentations", "subjects", "frames", "status"))
    for key in sorted(pres):
        n = len(pres[key])
        st = "OK" if n >= RULE_OF_30 else "need %d more presentations" % (RULE_OF_30 - n)
        print("%-26s %13d %9d %9d  %s" % (key, n, len(subj_by_group[key]), frames[key], st))
    missing = sorted(s for s in ("print", "curved_print", "screen", "video", "cutout")
                     if "attack:" + s not in pres)
    if missing:
        print("\nNO DATA for species: " + ", ".join(missing))
    print("\nBona fide subjects per skin_tone cell (fairness cells need >= 30 SUBJECTS, else exploratory):")
    for k in sorted(bona_subj_by_mst):
        n = len(bona_subj_by_mst[k])
        print("  %-10s %3d subjects  %s" % (k, n, "OK" if n >= RULE_OF_30 else "EXPLORATORY ONLY"))
    if skipped_subjects:
        print("\nSubjects not in subjects.csv (skipped): " + ", ".join(sorted(skipped_subjects)))
    if warnings:
        print("\nWARNINGS (%d):" % len(warnings))
        for w in warnings[:200]:
            print("  - " + w)


if __name__ == "__main__":
    main()
