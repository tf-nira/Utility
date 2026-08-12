package io.mosip.packet_utility.dto;

import lombok.Data;

@Data
public class DocumentRequestDTO {
    private String id;
    private String documentName;
    private String source;
    private String process;
    private boolean bypassCache;
}