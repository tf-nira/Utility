package io.mosip.packet_utility.dto;

import lombok.Data;

@Data
public class CompareDataResultDto {

	private String renewalRid;
	private String idRepoRid;
	private boolean status;
}
