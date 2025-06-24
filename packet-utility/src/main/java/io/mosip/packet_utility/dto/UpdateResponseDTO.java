package io.mosip.packet_utility.dto;

import lombok.Data;

@Data
public class UpdateResponseDTO {
    private String status;
    private String identity;
    private String documents;
    private String verifiedAttributes;
    private String cardDetails;
}
