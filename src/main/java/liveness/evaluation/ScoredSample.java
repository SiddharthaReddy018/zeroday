package liveness.evaluation;

/**
 * Immutable holder for a single scored evaluation sample (§12.1).
 * <p>
 * The score is 0–100 where higher = more live (bona fide).
 * A threshold is applied externally to decide accept/reject.
 */
public final class ScoredSample {

    private final int score;
    private final String label;
    private final String species;
    private final String subjectId;

    // Optional demographic fields for fairness analysis
    private final String skinTone;
    private final String ageGroup;
    private final String gender;
    private final String lighting;
    private final String glasses;

    /**
     * Full constructor with demographics.
     *
     * @param score     PAD score, 0–100 (higher = more live)
     * @param label     "bona_fide" or "attack"
     * @param species   PAI species for attacks, may be empty/null for bona fide
     * @param subjectId unique subject identifier
     * @param skinTone  Monk Skin Tone (nullable)
     * @param ageGroup  age group bin (nullable)
     * @param gender    gender (nullable)
     * @param lighting  lighting condition (nullable)
     * @param glasses   glasses condition (nullable)
     */
    public ScoredSample(int score, String label, String species, String subjectId,
                        String skinTone, String ageGroup, String gender,
                        String lighting, String glasses) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be 0-100, got: " + score);
        }
        if (label == null || (!label.equals("bona_fide") && !label.equals("attack"))) {
            throw new IllegalArgumentException("label must be 'bona_fide' or 'attack', got: " + label);
        }
        this.score = score;
        this.label = label;
        this.species = species;
        this.subjectId = subjectId;
        this.skinTone = skinTone;
        this.ageGroup = ageGroup;
        this.gender = gender;
        this.lighting = lighting;
        this.glasses = glasses;
    }

    /**
     * Convenience constructor without demographics.
     */
    public ScoredSample(int score, String label, String species, String subjectId) {
        this(score, label, species, subjectId, null, null, null, null, null);
    }

    public int score()        { return score; }
    public String label()     { return label; }
    public String species()   { return species; }
    public String subjectId() { return subjectId; }
    public String skinTone()  { return skinTone; }
    public String ageGroup()  { return ageGroup; }
    public String gender()    { return gender; }
    public String lighting()  { return lighting; }
    public String glasses()   { return glasses; }

    /** True if this sample is bona fide. */
    public boolean isBonafide() {
        return "bona_fide".equals(label);
    }

    /** True if this sample is an attack. */
    public boolean isAttack() {
        return "attack".equals(label);
    }

    @Override
    public String toString() {
        return "ScoredSample{score=" + score + ", label='" + label
                + "', species='" + species + "', subject='" + subjectId + "'}";
    }
}
