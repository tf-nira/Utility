package io.mosip.packet_utility.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BiometricsRequestDTO {
    private String id;
    private String person;
    private List<String> modalities = new ArrayList<>();
    private String source;
    private String process;
    private Boolean bypassCache;
}
