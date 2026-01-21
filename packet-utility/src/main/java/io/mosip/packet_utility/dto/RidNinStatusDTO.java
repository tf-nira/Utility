package io.mosip.packet_utility.dto;

import lombok.Data;

@Data
public class RidNinStatusDTO {
    private String rid;
    private String nin;
    private String status;
}
