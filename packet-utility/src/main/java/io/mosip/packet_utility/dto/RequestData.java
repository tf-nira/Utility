package io.mosip.packet_utility.dto;

import java.util.List;

import lombok.Data;

@Data
public class RequestData {
    private String registrationId;
    private String status;
    private Object identity; 
    private List<DocumentDto> documents;
}
