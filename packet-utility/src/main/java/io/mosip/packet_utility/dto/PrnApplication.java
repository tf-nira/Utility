package io.mosip.packet_utility.dto;
import lombok.Data;

@Data
public class PrnApplication {
    private String regId;
    private String process;
    private String prn;
    private String operator;
}
