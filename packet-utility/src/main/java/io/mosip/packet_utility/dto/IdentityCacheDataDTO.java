package io.mosip.packet_utility.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentityCacheDataDTO {
    private String idHash;
    private String token;
    private Object identity;
    private String expiryTimestamp;   // null in sample; String or Instant
    private Integer transactionLimit; // null in sample
}
