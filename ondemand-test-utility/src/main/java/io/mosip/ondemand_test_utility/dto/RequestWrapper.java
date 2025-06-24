package io.mosip.ondemand_test_utility.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Data
public class RequestWrapper<T> {

    public String id;
    public String version;

    @NotNull
    @Valid
    public T request;
}
