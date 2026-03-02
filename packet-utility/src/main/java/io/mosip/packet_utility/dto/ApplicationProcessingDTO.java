package io.mosip.packet_utility.dto;
import lombok.Data;

@Data
public class ApplicationProcessingDTO {
    private String RegId;
    private String Process;
    private String DateOfBirth;
    private String PacketCreatedDate;
    private String ApplicantAge;
    private String Remark;
}
