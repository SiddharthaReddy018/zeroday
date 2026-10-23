package liveness.evaluation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Loads evaluation dataset manifests in CSV format (§12.1).
 * <p>
 * Required columns: path, label, species, subject_id, split.
 * Optional columns: lighting, skin_tone, glasses, source, gender, age_group, capture_device, content_origin.
 * <p>
 * Handles missing optional columns gracefully — they default to null.
 * Lines starting with '#' are treated as comments and skipped.
 */
public final class ManifestLoader {

    private static final List<String> REQUIRED_COLUMNS = Arrays.asList(
            "path", "label", "species", "subject_id", "split"
    );

    private static final List<String> OPTIONAL_COLUMNS = Arrays.asList(
            "lighting", "skin_tone", "glasses", "source",
            "gender", "age_group", "capture_device", "content_origin"
    );

    private ManifestLoader() {
        // utility class
    }

    /**
     * Loads a manifest from a file path.
     *
     * @param path path to the CSV manifest file
     * @return unmodifiable list of parsed manifest entries
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if required columns are missing
     */
    public static List<ManifestEntry> load(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return load(reader);
        }
    }

    /**
     * Loads a manifest from a string (primarily for testing).
     *
     * @param csv the CSV content
     * @return unmodifiable list of parsed manifest entries
     * @throws IllegalArgumentException if required columns are missing
     */
    public static List<ManifestEntry> loadFromString(String csv) {
        try {
            return load(new BufferedReader(new StringReader(csv)));
        } catch (IOException e) {
            // StringReader never throws IOException
            throw new RuntimeException("Unexpected IOException from StringReader", e);
        }
    }

    /**
     * Loads a manifest from a reader.
     */
    static List<ManifestEntry> load(BufferedReader reader) throws IOException {
        String headerLine = null;

        // Skip comments and blank lines to find the header
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                throw new IllegalArgumentException("Manifest is empty — no header found");
            }
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                headerLine = trimmed;
                break;
            }
        }

        // Parse header
        String[] headers = parseRow(headerLine);
        Map<String, Integer> columnIndex = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            columnIndex.put(headers[i].toLowerCase(Locale.ROOT), i);
        }

        // Validate required columns
        for (String required : REQUIRED_COLUMNS) {
            if (!columnIndex.containsKey(required)) {
                throw new IllegalArgumentException("Missing required column: " + required);
            }
        }

        // Parse data rows
        List<ManifestEntry> entries = new ArrayList<>();
        int lineNum = 1;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNum++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            String[] fields = parseRow(trimmed);
            try {
                ManifestEntry entry = buildEntry(fields, columnIndex);
                entries.add(entry);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Error parsing manifest line " + lineNum + ": " + e.getMessage(), e);
            }
        }

        return Collections.unmodifiableList(entries);
    }

    /**
     * Builds a ManifestEntry from parsed fields using the column index map.
     */
    private static ManifestEntry buildEntry(String[] fields, Map<String, Integer> columnIndex) {
        String path = getRequired(fields, columnIndex, "path");
        String label = getRequired(fields, columnIndex, "label");
        String species = getRequired(fields, columnIndex, "species");
        String subjectId = getRequired(fields, columnIndex, "subject_id");
        String split = getRequired(fields, columnIndex, "split");

        String lighting = getOptional(fields, columnIndex, "lighting");
        String skinTone = getOptional(fields, columnIndex, "skin_tone");
        String glasses = getOptional(fields, columnIndex, "glasses");
        String source = getOptional(fields, columnIndex, "source");
        String gender = getOptional(fields, columnIndex, "gender");
        String ageGroup = getOptional(fields, columnIndex, "age_group");
        String captureDevice = getOptional(fields, columnIndex, "capture_device");
        String contentOrigin = getOptional(fields, columnIndex, "content_origin");

        return new ManifestEntry(path, label, species, subjectId, split,
                lighting, skinTone, glasses, source,
                gender, ageGroup, captureDevice, contentOrigin);
    }

    private static String getRequired(String[] fields, Map<String, Integer> columnIndex, String column) {
        Integer idx = columnIndex.get(column);
        if (idx == null || idx >= fields.length) {
            throw new IllegalArgumentException("Missing required field: " + column);
        }
        return fields[idx].trim();
    }

    private static String getOptional(String[] fields, Map<String, Integer> columnIndex, String column) {
        Integer idx = columnIndex.get(column);
        if (idx == null || idx >= fields.length) {
            return null;
        }
        String value = fields[idx].trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * Splits a CSV row on commas, trimming whitespace.
     * Does not handle quoted fields with embedded commas (evaluation manifests should not need this).
     */
    private static String[] parseRow(String line) {
        String[] parts = line.split(",", -1);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }
}
