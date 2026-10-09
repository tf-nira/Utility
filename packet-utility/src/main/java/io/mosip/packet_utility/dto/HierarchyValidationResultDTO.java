package io.mosip.packet_utility.dto;

import lombok.Data;

@Data
public class HierarchyValidationResultDTO {

    private String status;
    private String failedHierarchyLevel;
}
