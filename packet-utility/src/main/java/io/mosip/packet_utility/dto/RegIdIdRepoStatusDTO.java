package io.mosip.packet_utility.dto;

import lombok.Data;

@Data
public class RegIdIdRepoStatusDTO {
    private String regId;
    private String status;
    private String nin;
    private String remark;
}