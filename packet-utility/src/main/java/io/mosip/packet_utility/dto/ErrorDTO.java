package io.mosip.packet_utility.dto;

import lombok.Data;

@Data
public class ErrorDTO {
    private String errorCode;
    private String message;
}
