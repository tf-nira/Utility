package io.mosip.packet_utility.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Identity {
    @NotNull
    @Valid
    @JsonProperty("IDSchemaVersion")
    private double IDSchemaVersion;

    @JsonProperty("NIN")
    private String NIN;
    private List<LocalizedValue> surname;
    private List<LocalizedValue> givenName;
    private List<LocalizedValue> otherNames;
    private List<LocalizedValue> gender;
    private String dateOfBirth;
}
