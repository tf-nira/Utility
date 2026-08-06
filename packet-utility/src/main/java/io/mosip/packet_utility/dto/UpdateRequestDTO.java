package io.mosip.packet_utility.dto;

import lombok.Data;

import java.util.Map;

@Data
public class UpdateRequestDTO {
    private String id;
    private String version;
    private String requesttime;
    private Map<String, Object> request;
}