package io.mosip.packet_utility.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class DocumentDetailDto {
    private String refNumber;
    private String format;
    private String type;
    private String value;
}