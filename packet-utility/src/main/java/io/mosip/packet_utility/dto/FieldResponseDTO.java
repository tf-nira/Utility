package io.mosip.packet_utility.dto;

import lombok.Data;

import java.util.Map;

@Data
public class FieldResponseDTO {
    Map<String, String> fields;
}
