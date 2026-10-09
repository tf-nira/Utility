package io.mosip.packet_utility.dto;

import lombok.Data;

@Data
public class EnrolmentDetailsDTO {

    private String regId;
    private String applicantPlaceOfEnrolmentDistrict;
    private String applicantPlaceOfEnrolmentCounty;
    private String applicantPlaceOfEnrolmentSubCounty;
    private String applicantPlaceOfEnrolmentParish;
    private String applicantPlaceOfEnrolmentVillage;
    private String remark;
    private String failedHierarchyLevel;
}