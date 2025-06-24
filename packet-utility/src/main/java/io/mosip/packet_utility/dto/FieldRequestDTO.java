package io.mosip.packet_utility.dto;

import lombok.Data;

@Data
public class FieldRequestDTO {
    private String id;
    private String field;
    private String source;
    private String process;
    private Boolean bypassCache;
}
