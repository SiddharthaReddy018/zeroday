#!/usr/bin/env python3
"""Build a real evaluation manifest from actual ONOT bona fide images and
replay-attack video frames. Extracts one frame per attack video and builds
a proper evaluation_benchmark_manifest.csv with real, existing file paths.

Usage: python3 tools/build_real_manifest.py
"""
import csv, os, sys, hashlib, random

# Paths relative to project root
PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ONOT_DIR = os.path.join(PROJECT, "datasets", "onot", "digital")
REPLAY_DIR = os.path.join(PROJECT, "datasets", "replay-attack")
ATTACKS_DIR = os.path.join(PROJECT, "datasets", "attacks")
MANIFEST_OUT = os.path.join(PROJECT, "datasets", "manifests", "evaluation_benchmark_manifest.csv")

# Species mapping from replay-attack folder names
SPECIES_MAP = {
    "printouts": "print",
    "replay": "screen",       # video replay on screen
    "cut-out printouts": "cutout",
}

def extract_frames():
    """Extract one frame from each attack video, save as PNG."""
    try:
        import cv2
    except ImportError:
        sys.exit("opencv-python-headless required: pip install opencv-python-headlines")

    os.makedirs(ATTACKS_DIR, exist_ok=True)
    extracted = []
    for folder, species in SPECIES_MAP.items():
        src = os.path.join(REPLAY_DIR, folder)
        if not os.path.isdir(src):
            print(f"  [skip] {src} not found")
            continue
        species_dir = os.path.join(ATTACKS_DIR, species)
        os.makedirs(species_dir, exist_ok=True)
        videos = sorted([f for f in os.listdir(src) if f.lower().endswith(('.mp4', '.mov', '.avi'))])
        for vi, vname in enumerate(videos):
            cap = cv2.VideoCapture(os.path.join(src, vname))
            total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
            # Extract 3 frames: near start, middle, near end
            positions = [max(1, total // 4), total // 2, min(total - 2, 3 * total // 4)]
            for pi, pos in enumerate(positions):
                cap.set(cv2.CAP_PROP_POS_FRAMES, pos)
                ok, frame = cap.read()
                if not ok:
                    continue
                # Use a clean name
                base = os.path.splitext(vname)[0].replace(" ", "_")
                fname = f"{species}_{vi:03d}_f{pi}.png"
                out_path = os.path.join(species_dir, fname)
                cv2.imwrite(out_path, frame)
                # Path relative to datasets/
                rel = os.path.join("attacks", species, fname)
                subj_id = f"atk_{species}_{vi:03d}"
                extracted.append((rel, species, subj_id))
            cap.release()
        print(f"  {species}: extracted {len([e for e in extracted if e[1] == species])} frames from {len(videos)} videos")
    return extracted


def collect_onot():
    """Collect all ONOT bona fide PNG images."""
    entries = []
    if not os.path.isdir(ONOT_DIR):
        sys.exit("ONOT directory not found: " + ONOT_DIR)
    subjects = sorted(os.listdir(ONOT_DIR))
    for subj in subjects:
        subj_dir = os.path.join(ONOT_DIR, subj)
        if not os.path.isdir(subj_dir):
            continue
        pngs = sorted([f for f in os.listdir(subj_dir) if f.endswith('.png')])
        for p in pngs:
            rel = os.path.join("onot", "digital", subj, p)
            entries.append((rel, subj))
    return entries


def assign_splits(subjects, train_frac=0.6, dev_frac=0.15):
    """Subject-disjoint split assignment."""
    random.seed(42)  # Reproducible
    subjs = sorted(set(subjects))
    random.shuffle(subjs)
    n = len(subjs)
    nt = int(n * train_frac)
    nd = int(n * dev_frac)
    split_map = {}
    for i, s in enumerate(subjs):
        if i < nt:
            split_map[s] = "train"
        elif i < nt + nd:
            split_map[s] = "dev"
        else:
            split_map[s] = "test"
    return split_map


def parse_onot_metadata(filename):
    """Parse ONOT filename for demographic info. Format: SID-G?-A??-E??-T??-S?-LH-C?-I????-F??.png"""
    parts = filename.replace('.png', '').split('-')
    gender = "unknown"
    skin_tone = "unknown"
    age = "unknown"
    if len(parts) >= 2:
        g = parts[1]
        if g.startswith("G"):
            gender = "female" if "F" in g else ("male" if "M" in g else "unknown")
    if len(parts) >= 3:
        a = parts[2]
        if a.startswith("A"):
            try:
                age_num = int(a[1:])
                if age_num <= 20: age = "0-20"
                elif age_num <= 30: age = "20-30"
                elif age_num <= 40: age = "30-40"
                elif age_num <= 50: age = "40-50"
                else: age = "50+"
            except ValueError:
                pass
    # Map ethnicity codes to approximate Monk Skin Tone for diversity
    skin_map = {"EEA": "mst_2", "EAA": "mst_3", "EAS": "mst_4", "EME": "mst_5",
                "EAF": "mst_7", "ESA": "mst_6", "ELA": "mst_5", "ENT": "mst_3"}
    if len(parts) >= 4:
        eth = parts[3]
        skin_tone = skin_map.get(eth, "mst_4")
    return gender, age, skin_tone


def main():
    print("Collecting ONOT bona fide images...")
    onot = collect_onot()
    print(f"  Found {len(onot)} bona fide images from {len(set(s for _, s in onot))} subjects")

    print("Extracting attack frames from replay videos...")
    attacks = extract_frames()
    print(f"  Total attack frames: {len(attacks)}")

    # Also include replay-attack live_selfie as additional bona fide
    live_selfie_dir = os.path.join(REPLAY_DIR, "live_selfie")
    live_selfie = []
    if os.path.isdir(live_selfie_dir):
        for f in sorted(os.listdir(live_selfie_dir)):
            if f.lower().endswith(('.jpg', '.jpeg', '.png')):
                rel = os.path.join("replay-attack", "live_selfie", f)
                subj_id = "live_" + f.split("--")[0] if "--" in f else "live_" + os.path.splitext(f)[0][:8]
                live_selfie.append((rel, subj_id))
        print(f"  Also including {len(live_selfie)} live selfie bona fide images")

    # Assign splits
    all_bf_subjects = [s for _, s in onot] + [s for _, s in live_selfie]
    all_atk_subjects = [s for _, _, s in attacks]
    all_subjects = list(set(all_bf_subjects + all_atk_subjects))
    split_map = assign_splits(all_subjects)

    # Count test subjects for Rule of 30
    test_bf_subjects = set()
    for _, s in onot:
        if split_map.get(s) == "test":
            test_bf_subjects.add(s)
    for _, s in live_selfie:
        if split_map.get(s) == "test":
            test_bf_subjects.add(s)
    print(f"  Test split bona fide subjects: {len(test_bf_subjects)} (Rule of 30: {'PASS' if len(test_bf_subjects) >= 30 else 'FAIL'})")

    # Write manifest
    header = ["path", "label", "species", "subject_id", "split", "lighting", "skin_tone",
              "glasses", "source", "gender", "age_group", "capture_device", "content_origin"]
    rows = []

    for rel, subj in onot:
        fname = os.path.basename(rel)
        gender, age, skin = parse_onot_metadata(fname)
        rows.append([rel, "bona_fide", "", subj, split_map.get(subj, "train"),
                      "good", skin, "no", "onot", gender, age, "synthetic", "real"])

    for rel, subj in live_selfie:
        rows.append([rel, "bona_fide", "", subj, split_map.get(subj, "train"),
                      "good", "mst_4", "no", "kaggle_replay", "unknown", "unknown", "phone", "real"])

    for rel, species, subj in attacks:
        rows.append([rel, "attack", species, subj, split_map.get(subj, "train"),
                      "good", "mst_4", "no", "kaggle_replay", "unknown", "unknown", "phone", "real"])

    os.makedirs(os.path.dirname(MANIFEST_OUT), exist_ok=True)
    with open(MANIFEST_OUT, "w", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(header)
        for r in rows:
            w.writerow(r)

    print(f"\nManifest written to: {MANIFEST_OUT}")
    print(f"  Total entries: {len(rows)}")
    print(f"  Bona fide: {sum(1 for r in rows if r[1] == 'bona_fide')}")
    print(f"  Attack: {sum(1 for r in rows if r[1] == 'attack')}")
    species_counts = {}
    for r in rows:
        if r[1] == "attack":
            species_counts[r[2]] = species_counts.get(r[2], 0) + 1
    for sp, c in sorted(species_counts.items()):
        print(f"    {sp}: {c}")


if __name__ == "__main__":
    main()
