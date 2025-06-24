package io.mosip.packet_utility.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Data
public class ResponseWrapper<T> {
    private String id;
    private String version;
    private String responsetime;
    private String metadata;

    @NotNull
    @Valid
    private T response;
    private List<ErrorDTO> errors = new ArrayList<>();
}

