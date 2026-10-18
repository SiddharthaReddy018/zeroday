package liveness.evaluation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Tests for manifest loading (§17.6).
 */
public class ManifestLoaderTest {

    @Test
    public void loadWithAllColumns() {
        String csv = "path,label,species,subject_id,split,lighting,skin_tone,glasses,source,gender,age_group,capture_device,content_origin\n"
                + "img001.jpg,bona_fide,,subj_001,test,good,mst_5,no,onot,male,20-30,webcam,real\n"
                + "img002.jpg,attack,print,subj_002,test,good,mst_3,no,team,female,30-40,printer_600dpi,real\n";

        List<ManifestEntry> entries = ManifestLoader.loadFromString(csv);
        assertEquals(2, entries.size());

        ManifestEntry e1 = entries.get(0);
        assertEquals("img001.jpg", e1.path());
        assertEquals("bona_fide", e1.label());
        assertTrue(e1.isBonafide());
        assertEquals("subj_001", e1.subjectId());
        assertEquals("test", e1.split());
        assertEquals("good", e1.lighting());
        assertEquals("mst_5", e1.skinTone());
        assertEquals("male", e1.gender());
        assertEquals("20-30", e1.ageGroup());

        ManifestEntry e2 = entries.get(1);
        assertEquals("attack", e2.label());
        assertEquals("print", e2.species());
        assertTrue(e2.isAttack());
    }

    @Test
    public void loadWithRequiredColumnsOnly() {
        String csv = "path,label,species,subject_id,split\n"
                + "img001.jpg,bona_fide,,subj_001,test\n"
                + "img002.jpg,attack,screen,subj_002,dev\n";

        List<ManifestEntry> entries = ManifestLoader.loadFromString(csv);
        assertEquals(2, entries.size());

        ManifestEntry e1 = entries.get(0);
        assertNull(e1.lighting());
        assertNull(e1.skinTone());
        assertNull(e1.gender());
    }

    @Test
    public void loadSkipsCommentsAndBlankLines() {
        String csv = "# This is a comment\n"
                + "\n"
                + "path,label,species,subject_id,split\n"
                + "# Another comment\n"
                + "img001.jpg,bona_fide,,subj_001,test\n"
                + "\n"
                + "img002.jpg,attack,print,subj_002,test\n";

        List<ManifestEntry> entries = ManifestLoader.loadFromString(csv);
        assertEquals(2, entries.size());
    }

    @Test
    public void loadThrowsOnMissingRequiredColumn() {
        String csv = "path,label,species\n"
                + "img001.jpg,bona_fide,\n";

        assertThrows(IllegalArgumentException.class,
                () -> ManifestLoader.loadFromString(csv));
    }

    @Test
    public void loadThrowsOnEmptyManifest() {
        String csv = "# Just comments\n# Nothing else\n";

        assertThrows(IllegalArgumentException.class,
                () -> ManifestLoader.loadFromString(csv));
    }

    @Test
    public void loadHandlesTrailingCommas() {
        String csv = "path,label,species,subject_id,split\n"
                + "img001.jpg,bona_fide,,subj_001,test,\n";

        List<ManifestEntry> entries = ManifestLoader.loadFromString(csv);
        assertEquals(1, entries.size());
    }

    @Test
    public void loadHandlesWhitespaceInFields() {
        String csv = "path, label , species , subject_id , split\n"
                + " img001.jpg , bona_fide , , subj_001 , test \n";

        List<ManifestEntry> entries = ManifestLoader.loadFromString(csv);
        assertEquals(1, entries.size());
        assertEquals("img001.jpg", entries.get(0).path());
        assertEquals("bona_fide", entries.get(0).label());
    }

    @Test
    public void manifestValidation() {
        String csv = "path,label,species,subject_id,split\n"
                + "img001.jpg,bona_fide,,subj_001,test\n"
                + "img002.jpg,attack,print,subj_002,test\n";

        List<ManifestEntry> entries = ManifestLoader.loadFromString(csv);
        List<String> issues = EvaluationHarness.validateManifest(entries);

        // Should flag Rule of 30 (only 1 bona fide subject)
        boolean hasRuleOf30Warning = false;
        for (String issue : issues) {
            if (issue.contains("Rule of 30")) {
                hasRuleOf30Warning = true;
            }
        }
        assertTrue(hasRuleOf30Warning,
                "Should warn about Rule of 30 with only 1 bona fide subject");
    }
}
