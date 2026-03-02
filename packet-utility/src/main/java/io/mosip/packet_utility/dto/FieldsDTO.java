package io.mosip.packet_utility.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class FieldsDTO {

    private Map<String, Object> fields = new HashMap<>();

    @JsonAnySetter
    public void addField(String key, Object value) {
        fields.put(key, value);
    }
}