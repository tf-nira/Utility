package io.mosip.packet_utility.dto;

import lombok.Data;

@Data
public class FaceBiometricResultDTO {
    private String regId;
    private String process;
    private String status;
    private String rawFile;
    private String imageFile;
    private String remark;
}
