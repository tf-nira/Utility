package io.mosip.packet_utility.service.impl;

import com.opencsv.CSVReader;
import io.mosip.packet_utility.dto.HierarchyCorrectionResultDTO;
import io.mosip.packet_utility.dto.HierarchyValidationResultDTO;
import io.mosip.packet_utility.dto.LevelMismatchDTO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads the Uganda location hierarchy master data from
 * {@code location_master_data.csv} and validates that the applicant residence
 * hierarchy (district -> county -> sub-county -> parish -> village) stored for
 * an RID matches the parent-child hierarchy defined in the file.
 */
@Component
public class LocationMasterDataService {

    private static final String FILE_NAME = "location_master_data.csv";
    private static final String MATCHED = "matched";
    private static final String UNMATCHED = "unmatched";
    private static final String[] LEVEL_NAMES = {
            "District", "County", "SubCounty", "Parish", "Village"
    };

    /** Index of a location record by its unique code (e.g. "UGA", "121779"). */
    private final Map<String, LocationRecord> codeIndex = new HashMap<>();
    /**
     * Maps a normalized display name to ALL codes that share that name.
     * The same name can appear multiple times (e.g. "BUNYUMA (04)" exists as
     * a village under two different parents and "HOSPITAL" appears many times).
     */
    private final Map<String, List<String>> nameIndex = new HashMap<>();

    private volatile boolean loaded = false;
    private final Object loadLock = new Object();

    /**
     * Validates whether the given residence hierarchy values form a valid
     * parent-child chain in the location master data, from district to village.
     */
    public HierarchyValidationResultDTO validateResidenceHierarchy(String district, String county,
                                                                   String subCounty, String parish, String village) {
        HierarchyValidationResultDTO result = new HierarchyValidationResultDTO();
        String[] values = {district, county, subCounty, parish, village};

        if (isBlank(district) && isBlank(county) && isBlank(subCounty)
                && isBlank(parish) && isBlank(village)) {
            result.setStatus(UNMATCHED);
            result.setFailedHierarchyLevel("");
            return result;
        }

        ensureLoaded();

        // Resolve each level to ALL candidate records that could represent it.
        List<List<LocationRecord>> candidatesByLevel = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            String rawValue = values[i];
            if (isBlank(rawValue)) {
                result.setStatus(UNMATCHED);
                result.setFailedHierarchyLevel(LEVEL_NAMES[i]);
                return result;
            }
            LocationRecord byCode = codeIndex.get(rawValue.trim());
            if (byCode != null) {
                candidatesByLevel.add(Collections.singletonList(byCode));
            } else {
                List<String> codes = nameIndex.get(normalize(rawValue.trim()));
                if (codes == null || codes.isEmpty()) {
                    result.setStatus(UNMATCHED);
                    result.setFailedHierarchyLevel(LEVEL_NAMES[i]);
                    return result;
                }
                List<LocationRecord> records = new ArrayList<>();
                for (String code : codes) {
                    LocationRecord rec = codeIndex.get(code);
                    if (rec != null && !records.contains(rec)) {
                        records.add(rec);
                    }
                }
                candidatesByLevel.add(records);
            }
        }

        // Walk the chain level by level, keeping every parent code that still forms
        // a valid prefix, so repeated names matched by ANY valid path are accepted.
        List<String> validParents = new ArrayList<>();
        for (int i = 0; i < candidatesByLevel.size(); i++) {
            List<String> nextValidParents = new ArrayList<>();
            boolean levelMatched = false;
            for (LocationRecord candidate : candidatesByLevel.get(i)) {
                boolean isChild = (i == 0)
                        || (candidate.parentLocCode != null
                            && validParents.contains(candidate.parentLocCode));
                if (isChild) {
                    levelMatched = true;
                    if (!nextValidParents.contains(candidate.code)) {
                        nextValidParents.add(candidate.code);
                    }
                }
            }
            if (!levelMatched) {
                result.setStatus(UNMATCHED);
                result.setFailedHierarchyLevel(LEVEL_NAMES[i]);
                return result;
            }
            validParents = nextValidParents;
        }

        result.setStatus(MATCHED);
        result.setFailedHierarchyLevel("");
        return result;
    }

    /**
     * Reconstructs the correct residence hierarchy bottom-up by anchoring on the
     * village and walking up its {@code parent_loc_code} chain in the location
     * master data. Each candidate chain (a village name can resolve to several
     * records, e.g. "BUNYUMA (04)") is scored against the stored higher-level
     * values to disambiguate, and the best chain is used as the ground truth.
     */
    public HierarchyCorrectionResultDTO correctResidenceHierarchy(String district, String county,
                                                                  String subCounty, String parish, String village) {
        HierarchyCorrectionResultDTO result = new HierarchyCorrectionResultDTO();
        result.setFailedHierarchyLevel("");
        result.setMismatches(new ArrayList<>());

        if (isBlank(village)) {
            result.setStatus(UNMATCHED);
            addMismatch(result, "Village", village, "");
            return result;
        }

        ensureLoaded();

        // 1) Resolve the village to one or more candidate records (code, else name).
        List<LocationRecord> villageCandidates = resolveCandidates(village);
        if (villageCandidates.isEmpty()) {
            result.setStatus(UNMATCHED);
            result.setFailedHierarchyLevel("Village");
            addMismatch(result, "Village", village, "");
            return result;
        }

        // 2) Build the corrected chain for each candidate and score it against the
        //    stored higher-level values to pick the most plausible one.
        String[] stored = {district, county, subCounty, parish, village};
        String[] bestCorrect = null;
        String bestVillageCode = "";
        int bestScore = -1;
        int bestCompleteness = -1;

        for (LocationRecord candidate : villageCandidates) {
            String[] correct = buildUpwardChain(candidate);
            int score = scoreChain(stored, correct);
            int completeness = countFilled(correct);
            if (score > bestScore
                    || (score == bestScore && completeness > bestCompleteness)) {
                bestScore = score;
                bestVillageCode = candidate.code;
                bestCorrect = correct;
                bestCompleteness = completeness;
            }
        }

        // 3) Fill the correct values on the result.
        result.setVillageCode(bestVillageCode);
        result.setCorrectDistrict(bestCorrect[0]);
        result.setCorrectCounty(bestCorrect[1]);
        result.setCorrectSubCounty(bestCorrect[2]);
        result.setCorrectParish(bestCorrect[3]);
        result.setCorrectVillage(bestCorrect[4]);

        // 4) Compare stored vs correct level by level and record mismatches.
        boolean allMatched = true;
        for (int i = 0; i < LEVEL_NAMES.length; i++) {
            String storedValue = stored[i];
            String correctValue = bestCorrect[i];
            boolean matches = !isBlank(storedValue) && normalizeEquals(storedValue, correctValue);
            if (!matches) {
                allMatched = false;
                addMismatch(result, LEVEL_NAMES[i], storedValue, correctValue);
                if (isBlank(result.getFailedHierarchyLevel())) {
                    result.setFailedHierarchyLevel(LEVEL_NAMES[i]);
                }
            }
        }

        result.setStatus(allMatched ? MATCHED : UNMATCHED);
        return result;
    }

    private void addMismatch(HierarchyCorrectionResultDTO result, String levelName,
                             String storedValue, String correctValue) {
        LevelMismatchDTO mismatch = new LevelMismatchDTO();
        mismatch.setLevelName(levelName);
        mismatch.setStoredValue(storedValue);
        mismatch.setCorrectValue(correctValue);
        result.getMismatches().add(mismatch);
    }

    /**
     * Resolves a residence value into candidate records. A value may be a code
     * (exact match) or a display name (which can match multiple records).
     */
    private List<LocationRecord> resolveCandidates(String value) {
        String trimmed = value.trim();
        LocationRecord byCode = codeIndex.get(trimmed);
        if (byCode != null) {
            return Collections.singletonList(byCode);
        }
        List<String> codes = nameIndex.get(normalize(trimmed));
        List<LocationRecord> records = new ArrayList<>();
        if (codes != null) {
            for (String code : codes) {
                LocationRecord rec = codeIndex.get(code);
                if (rec != null && !records.contains(rec)) {
                    records.add(rec);
                }
            }
        }
        return records;
    }

    /**
     * Walks up from the given record through {@code parent_loc_code} and returns
     * the correct names as {district, county, subCounty, parish, village}. Levels
     * not reachable in the chain (or non-residential levels such as polling
     * stations) remain empty.
     */
    private String[] buildUpwardChain(LocationRecord start) {
        String[] correct = new String[LEVEL_NAMES.length];
        Set<String> visited = new HashSet<>();
        LocationRecord current = start;
        int depth = 0;
        while (current != null && !visited.contains(current.code) && depth < 10) {
            visited.add(current.code);
            int slot = levelSlot(current.hierarchyLevelName);
            if (slot >= 0 && isBlank(correct[slot])) {
                correct[slot] = current.name;
            }
            current = current.parentLocCode == null || current.parentLocCode.isEmpty()
                    ? null
                    : codeIndex.get(current.parentLocCode);
            depth++;
        }
        // If the anchor resolved to a non-residential record (e.g. a polling
        // station), make the village slot reflect the actual ancestor village.
        if (isBlank(correct[LEVEL_NAMES.length - 1]) && start != null) {
            correct[LEVEL_NAMES.length - 1] = start.name;
        }
        return correct;
    }

    /** Maps a hierarchy level name to the residential level index, or -1 if ignored. */
    private int levelSlot(String hierarchyLevelName) {
        if (hierarchyLevelName == null) {
            return -1;
        }
        switch (hierarchyLevelName.trim()) {
            case "District":
                return 0;
            case "County":
                return 1;
            case "SubCounty":
                return 2;
            case "Parish":
                return 3;
            case "Village":
                return 4;
            default:
                return -1;
        }
    }

    private int scoreChain(String[] stored, String[] correct) {
        int score = 0;
        for (int i = 0; i < stored.length; i++) {
            if (!isBlank(stored[i]) && normalizeEquals(stored[i], correct[i])) {
                score++;
            }
        }
        return score;
    }

    private int countFilled(String[] correct) {
        int count = 0;
        for (String value : correct) {
            if (!isBlank(value)) {
                count++;
            }
        }
        return count;
    }

    private boolean normalizeEquals(String a, String b) {
        return normalize(a).equals(normalize(b));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", "")
                .replace("(", "")
                .replace(")", "")
                .replace("-", "")
                .toLowerCase();
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (loadLock) {
            if (loaded) {
                return;
            }
            load();
            loaded = true;
        }
    }

    private void load() {
        try {
            ClassPathResource resource = new ClassPathResource(FILE_NAME);
            try (Reader fileReader = new InputStreamReader(resource.getInputStream());
                 CSVReader reader = new CSVReader(fileReader)) {

                String[] line;
                boolean header = true;
                while ((line = reader.readNext()) != null) {
                    if (header) {
                        header = false;
                        continue;
                    }
                    if (line.length < 5) {
                        continue;
                    }
                    LocationRecord rec = new LocationRecord();
                    rec.code = clean(line[0]);
                    rec.name = clean(line[1]);
                    rec.hierarchyLevel = clean(line[2]);
                    rec.hierarchyLevelName = clean(line[3]);
                    rec.parentLocCode = clean(line[4]);
                    if (rec.code.isEmpty()) {
                        continue;
                    }
                    codeIndex.put(rec.code, rec);
                    if (!rec.name.isEmpty()) {
                        String key = normalize(rec.name);
                        nameIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(rec.code);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Exception while loading location master data: " + e.getMessage());
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static class LocationRecord {
        String code;
        String name;
        String hierarchyLevel;
        String hierarchyLevelName;
        String parentLocCode;
    }
}