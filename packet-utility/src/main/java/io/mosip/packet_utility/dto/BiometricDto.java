package io.mosip.packet_utility.dto;


import lombok.Data;

@Data
public class BiometricDto {

	private String format;
    private int version;
    private String value;
}