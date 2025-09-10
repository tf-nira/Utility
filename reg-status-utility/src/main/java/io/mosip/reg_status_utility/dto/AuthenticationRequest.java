package io.mosip.reg_status_utility.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class AuthenticationRequest {
    private String appId;

    private String clientId;

    private String secretKey;
}
