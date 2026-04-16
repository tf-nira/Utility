package io.mosip.packet_utility.dto; 

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DocumentRequestDto {
    private String id;
    private String type;
    private String value;
    private String format;
}