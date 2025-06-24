package io.mosip.packet_utility.dto;

import lombok.Data;

@Data
public class RequestData {
    private String registrationId;
    private Identity identity;
}
