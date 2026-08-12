package io.mosip.packet_utility.dto;

import lombok.Data;

@Data
public class DocumentResponseDTO {
    private String document;
    private String value;
    private String type;
    private String format;
    private String refNumber;
}