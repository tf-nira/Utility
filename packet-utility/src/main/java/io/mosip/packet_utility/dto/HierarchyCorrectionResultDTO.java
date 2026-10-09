package io.mosip.packet_utility.dto;

import lombok.Data;

import java.util.List;

@Data
public class HierarchyCorrectionResultDTO {

    private String status;
    private String failedHierarchyLevel;

    /** The village code that the chain was anchored on (empty when unresolved). */
    private String villageCode;

    /** Correct values derived bottom-up from the village (empty when not derivable). */
    private String correctDistrict;
    private String correctCounty;
    private String correctSubCounty;
    private String correctParish;
    private String correctVillage;

    /** Per-level mismatches between the stored and the correct value. */
    private List<LevelMismatchDTO> mismatches;
}