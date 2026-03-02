package io.mosip.packet_utility.dto;

import lombok.Data;

@Data
public class MultiFieldRequestDTO {
    private String id;
    private String[] fields;
    private String source;
    private String process;
    private Boolean bypassCache;
}
