package io.mosip.packet_utility.dto;

import lombok.Data;

@Data
public class UpdateRequestDTO {
    private String id;
    private RequestData request;
    private String requesttime;
    private String version;
}
