package io.mosip.packet_utility.dto;

import lombok.Data;

/**
 * Describes a mismatch at a single hierarchy level: the value stored for the
 * RID vs the correct value derived from the location master data.
 */
@Data
public class LevelMismatchDTO {

    private String levelName;
    private String storedValue;
    private String correctValue;
}