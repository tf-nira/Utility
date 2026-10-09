package io.mosip.reg_status_utility.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.reg_status_utility.dto.CachedSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * Persists each day's "yesterday" report results to a JSON file so that on the
 * next run they can be reused as the "day before yesterday" data without
 * re-querying the database. Stored on disk (not memory) so it survives restarts.
 */
@Service
@Slf4j
public class ReportCacheService {

    @Value("${report.cache.file.path:report-cache.json}")
    private String cacheFilePath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Loads the cached snapshot whose dataDate matches the expected date.
     *
     * @param expectedDate the date the stored data should represent (yyyy-MM-dd)
     * @return the snapshot if present and date matches, otherwise null
     */
    public CachedSnapshot load(String expectedDate) {
        File file = new File(cacheFilePath);
        if (!file.exists()) {
            log.info("Report cache file not found at {} - will query day-before-yesterday", file.getAbsolutePath());
            return null;
        }
        try {
            CachedSnapshot snapshot = objectMapper.readValue(file, CachedSnapshot.class);
            if (snapshot == null || !expectedDate.equals(snapshot.getDataDate())) {
                log.info("Report cache date {} does not match expected {} -> will query day-before-yesterday",
                        snapshot == null ? null : snapshot.getDataDate(), expectedDate);
                return null;
            }
            log.info("Loaded daily report cache for date {}", expectedDate);
            return snapshot;
        } catch (Exception e) {
            log.warn("Failed to read report cache file {} - will query day-before-yesterday", cacheFilePath, e);
            return null;
        }
    }

    /**
     * Persists the snapshot to the cache file.
     */
    public void save(CachedSnapshot snapshot) {
        try {
            File file = new File(cacheFilePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, snapshot);
            log.info("Saved daily report cache for date {} to {}", snapshot.getDataDate(), file.getAbsolutePath());
        } catch (Exception e) {
            log.warn("Failed to write report cache file {}", cacheFilePath, e);
        }
    }
}
