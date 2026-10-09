package io.mosip.packet_utility.dto;

import lombok.Data;

@Data
public class ResidenceDetailsDTO {

    private String regId;
    private String applicantPlaceOfResidenceDistrict;
    private String applicantPlaceOfResidenceCounty;
    private String applicantPlaceOfResidenceSubCounty;
    private String applicantPlaceOfResidenceParish;
    private String applicantPlaceOfResidenceVillage;
    private String remark;
    private String failedHierarchyLevel;
}