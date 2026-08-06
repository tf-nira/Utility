package io.mosip.packet_utility.dto;

import lombok.Data;

@Data
public class NinStatusDTO {
    private String nin;
    private String status;
    private String idSchemaVersion;
    private String uin;
    private String cardNumber;
    private String dateOfIssuance;
    private String dateOfExpiry;
}
