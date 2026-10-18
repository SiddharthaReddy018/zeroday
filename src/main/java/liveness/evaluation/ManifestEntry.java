package liveness.evaluation;

/**
 * Immutable data holder for one row of the evaluation dataset manifest (§12.1).
 * <p>
 * Required columns: path, label, species, subjectId, split.
 * Optional columns: lighting, skinTone, glasses, source, gender, ageGroup, captureDevice, contentOrigin.
 */
public final class ManifestEntry {

    private final String path;
    private final String label;
    private final String species;
    private final String subjectId;
    private final String split;

    // Optional demographic / provenance columns (nullable)
    private final String lighting;
    private final String skinTone;
    private final String glasses;
    private final String source;
    private final String gender;
    private final String ageGroup;
    private final String captureDevice;
    private final String contentOrigin;

    /**
     * @param path           file path to the sample image/video
     * @param label          "bona_fide" or "attack"
     * @param species        PAI species (e.g. "print", "screen", "mask_3d") or empty for bona fide
     * @param subjectId      unique subject identifier
     * @param split          "train", "dev", or "test"
     * @param lighting       lighting condition (nullable)
     * @param skinTone       Monk Skin Tone scale value (nullable)
     * @param glasses        glasses condition (nullable)
     * @param source         data source identifier (nullable)
     * @param gender         gender demographic (nullable)
     * @param ageGroup       age group bin (nullable)
     * @param captureDevice  capture device identifier (nullable)
     * @param contentOrigin  content origin identifier (nullable)
     */
    public ManifestEntry(String path, String label, String species, String subjectId, String split,
                         String lighting, String skinTone, String glasses, String source,
                         String gender, String ageGroup, String captureDevice, String contentOrigin) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("path must not be null or empty");
        }
        if (label == null || (!label.equals("bona_fide") && !label.equals("attack"))) {
            throw new IllegalArgumentException("label must be 'bona_fide' or 'attack', got: " + label);
        }
        if (subjectId == null || subjectId.isEmpty()) {
            throw new IllegalArgumentException("subjectId must not be null or empty");
        }
        if (split == null || (!split.equals("train") && !split.equals("dev") && !split.equals("test"))) {
            throw new IllegalArgumentException("split must be 'train', 'dev', or 'test', got: " + split);
        }
        this.path = path;
        this.label = label;
        this.species = species;
        this.subjectId = subjectId;
        this.split = split;
        this.lighting = lighting;
        this.skinTone = skinTone;
        this.glasses = glasses;
        this.source = source;
        this.gender = gender;
        this.ageGroup = ageGroup;
        this.captureDevice = captureDevice;
        this.contentOrigin = contentOrigin;
    }

    /**
     * Convenience constructor for required-only fields.
     */
    public ManifestEntry(String path, String label, String species, String subjectId, String split) {
        this(path, label, species, subjectId, split, null, null, null, null, null, null, null, null);
    }

    public String path()          { return path; }
    public String label()         { return label; }
    public String species()       { return species; }
    public String subjectId()     { return subjectId; }
    public String split()         { return split; }
    public String lighting()      { return lighting; }
    public String skinTone()      { return skinTone; }
    public String glasses()       { return glasses; }
    public String source()        { return source; }
    public String gender()        { return gender; }
    public String ageGroup()      { return ageGroup; }
    public String captureDevice() { return captureDevice; }
    public String contentOrigin() { return contentOrigin; }

    /** True if this entry represents a bona fide presentation. */
    public boolean isBonafide() {
        return "bona_fide".equals(label);
    }

    /** True if this entry represents an attack presentation. */
    public boolean isAttack() {
        return "attack".equals(label);
    }

    @Override
    public String toString() {
        return "ManifestEntry{path='" + path + "', label='" + label
                + "', species='" + species + "', subject='" + subjectId
                + "', split='" + split + "'}";
    }
}
