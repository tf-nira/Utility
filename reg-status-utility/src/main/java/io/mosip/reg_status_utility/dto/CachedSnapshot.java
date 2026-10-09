package io.mosip.reg_status_utility.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedSnapshot {
    private String dataDate;
    private List<CacheRow> processTypeByDate;
    private List<CacheRow> processedByDate;
    private List<CacheRow> holdAndFailedByDate;
    private List<CacheRow> mvsInqueueByDate;
    private List<CacheRow> printingByDate;
}
