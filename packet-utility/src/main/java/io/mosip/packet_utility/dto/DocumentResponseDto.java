package io.mosip.packet_utility.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DocumentResponseDto {
    private String category;
    private String value;
}