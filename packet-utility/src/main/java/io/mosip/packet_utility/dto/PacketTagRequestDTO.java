package io.mosip.packet_utility.dto;

import lombok.Data;

import java.util.Map;

@Data
public class PacketTagRequestDTO {
    private String id;
    private Map<String, String> tags;
}
